package com.group5.interview.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** 登录连续失败五次后锁定十五分钟。 */
@Service
@RequiredArgsConstructor
public class LoginLockService {

    private static final String PREFIX = "login:fail:";
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private final StringRedisTemplate redisTemplate;

    public boolean isLocked(String email) {
        String value = redisTemplate.opsForValue().get(key(email));
        return value != null && Integer.parseInt(value) >= MAX_FAILURES;
    }

    public boolean recordFailure(String email) {
        Long count = redisTemplate.opsForValue().increment(key(email));
        if (count != null && count == 1) {
            redisTemplate.expire(key(email), LOCK_DURATION);
        }
        return count != null && count >= MAX_FAILURES;
    }

    public void clearFailures(String email) {
        redisTemplate.delete(key(email));
    }

    private String key(String email) {
        return PREFIX + email;
    }
}
