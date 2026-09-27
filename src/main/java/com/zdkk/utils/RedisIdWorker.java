package com.zdkk.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1704067200; // 起始时间戳 2024-01-01
    private static final int EXPIRE_SEC = 10;    // 秒 key 10 秒后过期，够用

    private static final long COUNT_BITS = 20; // 序列号位数

    private final StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<List> script;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.script = new DefaultRedisScript<List>();
        this.script.setLocation(new ClassPathResource("lua/gen_id.lua"));
        this.script.setResultType(List.class);
    }

    public long nextId(String prefixKey) {
        List result = stringRedisTemplate.execute(
                script,
                Collections.singletonList("icr:" + prefixKey),
                String.valueOf(BEGIN_TIMESTAMP),
                String.valueOf(EXPIRE_SEC)
        );
        long timestamp = ((Number) result.get(0)).longValue();
        long count = ((Number) result.get(1)).longValue();
        return timestamp << COUNT_BITS | count;
    }
}
