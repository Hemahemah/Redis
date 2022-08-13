package com.example.redisdemo1;

import com.example.redisdemo1.pojo.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

@SpringBootTest
class StringRedisTemplateTests {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void contextLoads() {
    }

    @Test
    void testString(){
        redisTemplate.opsForValue().set("name","test");
         String name = redisTemplate.opsForValue().get("name");
        System.out.println(name);
    }

    @Test
    void testSaveUser() throws JsonProcessingException {
        User user = new User("jack", 21);
        String string = mapper.writeValueAsString(user);
        redisTemplate.opsForValue().set("user", string);
        String jsonUser = redisTemplate.opsForValue().get("user");
        User value = mapper.readValue(jsonUser, User.class);
        System.out.println(value);
    }

    @Test
    void testHash(){
        redisTemplate.opsForHash().put("user", "name", "user");
        redisTemplate.opsForHash().put("user", "age", "21");
        Map<Object, Object> user = redisTemplate.opsForHash().entries("user");
        System.out.println(user);
    }

}
