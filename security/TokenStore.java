package com.group5.interview.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/** Redis 中保存刷新令牌摘要与 access 黑名单，避免保存明文 refresh token。 */
@Component
@RequiredArgsConstructor
public class TokenStore {

    private static final String REFRESH_PREFIX = "refresh:";
    private static final String BLACKLIST_PREFIX = "black:";
    private final StringRedisTemplate redisTemplate;

    public void saveRefreshToken(Long userId, String token, Duration ttl) {
        redisTemplate.opsForValue().set(REFRESH_PREFIX + userId, sha256(token), ttl);
    }

    public boolean matchesRefreshToken(Long userId, String token) {
        String stored = redisTemplate.opsForValue().get(REFRESH_PREFIX + userId);
        return stored != null && MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8), sha256(token).getBytes(StandardCharsets.UTF_8));
    }

    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(REFRESH_PREFIX + userId);
    }

    public void blacklistAccessToken(Claims claims) {
        long ttl = JwtUtil.remainingTtlMs(claims);
        String jti = JwtUtil.jtiOf(claims);
        if (ttl > 0 && jti != null) {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", Duration.ofMillis(ttl));
        }
    }

    public boolean isAccessBlacklisted(Claims claims) {
        String jti = JwtUtil.jtiOf(claims);
        return jti != null && Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JRE 不支持 SHA-256", e);
        }
    }
}
