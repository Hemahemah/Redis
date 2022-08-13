package com.example.redisdemo1;

import com.example.redisdemo1.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
class RedisDemo1ApplicationTests {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void testString(){
        redisTemplate.opsForValue().set("name","test");
         String name = (String) redisTemplate.opsForValue().get("name");
        System.out.println(name);
    }

    @Test
    void testSaveUser(){
        redisTemplate.opsForValue().set("user", new User("user1", 12));
        User user = (User) redisTemplate.opsForValue().get("user");
        System.out.println(user);
    }

}
