package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopServiceImpl shopServiceImpl;

    @Resource
    private RedisIdWorker redisIdWorker;

    private final ExecutorService es =Executors.newFixedThreadPool(500);

    @Test
    void testRedis(){
        stringRedisTemplate.opsForValue().set("test", "test");
        String s = stringRedisTemplate.opsForValue().get("test");
        System.out.println(s);
    }

    @Test
    void testSaveShop(){
      shopServiceImpl.saveShopRedis(1L, 10L);
    }



    @Test
    void testIdWorker() throws InterruptedException {
       CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id:" + id);
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println(end - begin);
    }

    @Test
    void testTime(){
        long time = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        System.out.println(time);
    }

    @Test
    void testRedisId(){
        long[] ids = new long[100];
        for (int i = 0; i < ids.length; i++) {
            long test = redisIdWorker.nextId("test");
            System.out.println(ids[i] = test);
        }
        for (int i = 0; i < ids.length - 1; i++) {
            int j = i + 1;
            if (ids[i] > ids[j]){
                System.out.println("false");
                return;
            }
        }

    }

}
