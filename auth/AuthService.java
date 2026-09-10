package com.group5.interview.module.auth;

import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.entity.User;
import com.group5.interview.entity.UserSettings;
import com.group5.interview.module.auth.dto.LoginRequest;
import com.group5.interview.module.auth.dto.RegisterRequest;
import com.group5.interview.module.auth.dto.TokenResponse;
import com.group5.interview.module.auth.dto.UserSummary;
import com.group5.interview.repository.UserRepository;
import com.group5.interview.repository.UserSettingsRepository;
import com.group5.interview.security.JwtProperties;
import com.group5.interview.security.JwtUtil;
import com.group5.interview.security.LoginLockService;
import com.group5.interview.security.TokenStore;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final LoginLockService loginLockService;
    private final TokenStore tokenStore;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    @Value("${app.verify-code}")
    private String verifyCode;

    @Transactional
    public UserSummary register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (!verifyCode.equals(request.verifyCode())) {
            throw new BusinessException(ErrorCode.VERIFY_CODE_INVALID);
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该邮箱已注册，请直接登录");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(PASSWORD_ENCODER.encode(request.password()));
        user.setNickname(defaultNickname(email));
        user.setCreatedAt(LocalDateTime.now());
        user.setActive(true);
        User saved = userRepository.save(user);

        UserSettings settings = new UserSettings();
        settings.setUserId(saved.getId());
        settings.setUpdatedAt(LocalDateTime.now());
        userSettingsRepository.save(settings);
        return summary(saved);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        if (loginLockService.isLocked(email)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.isActive() || !PASSWORD_ENCODER.matches(request.password(), user.getPasswordHash())) {
            boolean nowLocked = loginLockService.recordFailure(email);
            if (nowLocked) {
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        loginLockService.clearFailures(email);
        user.setLastLoginAt(LocalDateTime.now());
        return issueTokens(user);
    }

    public TokenResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtUtil.parse(refreshToken);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!jwtUtil.isRefreshToken(claims)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Long userId = JwtUtil.userIdOf(claims);
        if (!tokenStore.matchesRefreshToken(userId, refreshToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return issueTokens(user);
    }

    public void logout(Long userId, String accessToken) {
        tokenStore.deleteRefreshToken(userId);
        try {
            Claims claims = jwtUtil.parse(accessToken);
            if (jwtUtil.isAccessToken(claims) && userId.equals(JwtUtil.userIdOf(claims))) {
                tokenStore.blacklistAccessToken(claims);
            }
        } catch (Exception ignored) {
            // 过滤器已校验过 access；此处防御性兜底，不泄露令牌解析信息。
        }
    }

    private TokenResponse issueTokens(User user) {
        String access = jwtUtil.createAccessToken(user.getId(), user.getEmail());
        String refresh = jwtUtil.createRefreshToken(user.getId());
        tokenStore.saveRefreshToken(user.getId(), refresh, Duration.ofDays(jwtProperties.getRefreshTtlDays()));
        return new TokenResponse(access, refresh, summary(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultNickname(String email) {
        String localPart = email.substring(0, email.indexOf('@'));
        return localPart.length() > 50 ? localPart.substring(0, 50) : localPart;
    }

    private UserSummary summary(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getNickname(), user.getRole());
    }
}
