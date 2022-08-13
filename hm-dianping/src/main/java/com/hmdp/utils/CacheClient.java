package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author lh
 */
@Component
@Slf4j
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogical(String key, Object value, Long time, TimeUnit unit){
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 1.从redis查询商铺
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StringUtils.hasText(json)){
            // 3.存在,直接返回
            return JSONUtil.toBean(json, type);
        }
        // 判断是否为空值
        if (json != null){
            //返回错误信息
            return null;
        }
        // 4.不存在,查询数据库
        R r = dbFallBack.apply(id);
        // 5.不存在，返回错误
        if (r == null){
            stringRedisTemplate.opsForValue().set(key, "", time, unit);
            return null;
        }
        // 6.存在，写入redis并返回
        set(key, r, time, unit);
        return r;
    }

    private boolean tryLock(String key){
        Boolean ifAbsent = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(ifAbsent);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 1.从redis查询商铺
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (!StringUtils.hasText(json)){
            // 3.不存在,直接返回
            return null;
        }
        // 4.命中,先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            // 5.1 未过期,直接返回店铺信息
            return r;
        }
        // 5.2 已过期,需缓存重建
        // 6. 缓存重建
        // 6.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断是否获取锁成功
        if (isLock){
            CACHE_REBUILD_EXECUTOR.submit(()->{
                //重建缓存
                //查数据库
                R r1 = dbFallback.apply(id);
                //写入redis
                this.setWithLogical(key, r1, time, unit);
                //释放锁
                unLock(lockKey);
            });

        }
        // 6.4 返回过期商铺信息
        return r;
    }
}
