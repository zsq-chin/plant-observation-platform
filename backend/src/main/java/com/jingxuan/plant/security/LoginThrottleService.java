package com.jingxuan.plant.security;

import com.jingxuan.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** 登录防爆破（V4 §53）：账号级失败计数，超阈值临时锁定。 */
@Service
@RequiredArgsConstructor
public class LoginThrottleService {

    private static final int MAX_FAILURES = 5;
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public void assertAllowed(String username) {
        String key = key(username);
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            try {
                if (Integer.parseInt(value) >= MAX_FAILURES) {
                    throw new BusinessException("登录失败次数过多，请10分钟后再试");
                }
            } catch (NumberFormatException ignored) {
                // 忽略脏数据
            }
        }
    }

    public void recordFailure(String username) {
        String key = key(username);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, TTL);
        }
    }

    public void reset(String username) {
        redisTemplate.delete(key(username));
    }

    private String key(String username) {
        return "jingxuan:login:fail:" + (username == null ? "" : username.toLowerCase());
    }
}
