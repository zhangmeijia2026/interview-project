package com.group5.interview.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析（HS256）。依赖 jjwt 0.13。
 * 文档：docs/05 §4。
 */
@Component
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public JwtUtil(JwtProperties props) {
        String secret = props.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret 未配置或长度不足 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMs = props.getAccessTtlMinutes() * 60_000L;
        this.refreshTtlMs = props.getRefreshTtlDays() * 24 * 3600_000L;
    }

    /** 签发 access token，claim 含 userId/email/type/jti。 */
    public String createAccessToken(Long userId, String email) {
        return create(userId, email, TYPE_ACCESS, accessTtlMs);
    }

    /** 签发 refresh token。 */
    public String createRefreshToken(Long userId) {
        return create(userId, null, TYPE_REFRESH, refreshTtlMs);
    }

    private String create(Long userId, String email, String type, long ttlMs) {
        Date now = new Date();
        var b = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs));
        if (email != null) {
            b.claim("email", email);
        }
        return b.signWith(key).compact();
    }

    /**
     * 解析并校验 token。签名/过期错误抛出 JwtException。
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 校验 token 类型，返回是否 access token。 */
    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get("type", String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get("type", String.class));
    }

    public static boolean isExpired(Claims claims) {
        return claims.getExpiration() == null || claims.getExpiration().before(new Date());
    }

    public static Long userIdOf(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public static String jtiOf(Claims claims) {
        return claims.getId();
    }

    /** 返回令牌距过期的剩余毫秒，已过期时返回 0。 */
    public static long remainingTtlMs(Claims claims) {
        return Math.max(0, claims.getExpiration().getTime() - System.currentTimeMillis());
    }

    public static boolean isJwtProblem(Throwable t) {
        return t instanceof JwtException || t instanceof IllegalArgumentException;
    }
}
