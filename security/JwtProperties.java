package com.group5.interview.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JWT 相关配置：app.jwt.*
 * secret 至少 32 字节（HS256 要求），生产环境通过环境变量注入。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** 签名密钥（>=32 字节）。开发默认值仅在 application-local.yml。 */
    private String secret;

    /** access token 有效期（分钟），默认 120 */
    private long accessTtlMinutes = 120;

    /** refresh token 有效期（天），默认 7 */
    private long refreshTtlDays = 7;

    /** 额外放行路径（精确匹配） */
    private List<String> publicPaths = new ArrayList<>();
}
