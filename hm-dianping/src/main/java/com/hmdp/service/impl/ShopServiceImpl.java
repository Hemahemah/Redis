package com.hmdp.service.impl;
import java.time.LocalDateTime;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import javax.annotation.Resource;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        Shop shop = cacheClient.queryWithThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class,
                this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result update(Shop shop) {
        if (shop.getId() == null){
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    private boolean tryLock(String key){
        Boolean ifAbsent = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(ifAbsent);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    public Shop queryWithMutex(Long id){
        // 1.从redis查询商铺
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        // 2.判断是否存在
        if (StringUtils.hasText(shopJson)){
            // 3.存在,直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null){
            //返回错误信息
            return null;
        }
        // 4.实现缓存重建
        // 4.1 获取互斥锁
        Shop shop = null;
        try {
            boolean tryLock = tryLock(RedisConstants.LOCK_SHOP_KEY + id);
            // 4.2 判断是否获取成功
            if (!tryLock){
                // 4.3 失败则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // 4.4 成功,根据id查询数据库
            shop = getById(id);
            // 5.不存在，返回错误
            if (shop == null){
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 6.存在，写入redis并返回
            // 7.释放互斥锁
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException();
        }finally {
            unLock(RedisConstants.LOCK_SHOP_KEY + id);
        }
        return shop;
    }

    public Shop queryWithThrough(Long id){
        // 1.从redis查询商铺
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        // 2.判断是否存在
        if (StringUtils.hasText(shopJson)){
            // 3.存在,直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null){
            //返回错误信息
            return null;
        }
        // 4.不存在,查询数据库
        Shop shop = getById(id);
        // 5.不存在，返回错误
        if (shop == null){
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6.存在，写入redis并返回
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id){
        // 1.从redis查询商铺
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        // 2.判断是否存在
        if (!StringUtils.hasText(shopJson)){
            // 3.不存在,直接返回
            return null;
        }
        // 4.命中,先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            // 5.1 未过期,直接返回店铺信息
            return shop;
        }
        // 5.2 已过期,需缓存重建
        // 6. 缓存重建
        // 6.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断是否获取锁成功
        if (isLock){
            // todo 6.3 成功,开启独立线程实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                //重建缓存
                saveShopRedis(id, 30L);
                //释放锁
                unLock(lockKey);
            });

        }
        // 6.4 返回过期商铺信息
        return shop;
    }

    public void saveShopRedis(Long id, Long expireSeconds){
        // 1.查询店铺数据
        Shop shop = getById(id);
        // 2.封装逻辑过期
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisData.setData(shop);
        // 3.写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
}
