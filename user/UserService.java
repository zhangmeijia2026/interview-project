package com.group5.interview.module.user;

import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.entity.User;
import com.group5.interview.entity.UserSettings;
import com.group5.interview.module.user.dto.*;
import com.group5.interview.repository.UserRepository;
import com.group5.interview.repository.UserSettingsRepository;
import com.group5.interview.security.TokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);
    private static final Set<String> LANGUAGES = Set.of("zh", "en");
    private static final Set<String> SUPPORTED_MODELS = Set.of("deepseek-chat", "deepseek-reasoner");
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final TokenStore tokenStore;

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        User user = requiredUser(userId);
        return toMe(user, requiredSettings(userId));
    }

    @Transactional
    public MeResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = requiredUser(userId);
        user.setNickname(request.nickname().trim());
        user.setAvatarUrl(blankToNull(request.avatarUrl()));
        return toMe(user, requiredSettings(userId));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = requiredUser(userId);
        if (!PASSWORD_ENCODER.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "原密码错误");
        }
        if (request.oldPassword().equals(request.newPassword())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "新密码不能与原密码相同");
        }
        user.setPasswordHash(PASSWORD_ENCODER.encode(request.newPassword()));
        tokenStore.deleteRefreshToken(userId);
    }

    @Transactional
    public SettingsResponse updateSettings(Long userId, UpdateSettingsRequest request) {
        if (!"deepseek".equals(request.modelProvider()) || !SUPPORTED_MODELS.contains(request.modelName())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "当前仅支持 DeepSeek 模型");
        }
        if (!LANGUAGES.contains(request.language())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "language 仅支持 zh 或 en");
        }
        UserSettings settings = requiredSettings(userId);
        settings.setModelProvider(request.modelProvider());
        settings.setModelName(request.modelName());
        settings.setLanguage(request.language());
        settings.setNotifyEnabled(request.notifyEnabled());
        settings.setUpdatedAt(LocalDateTime.now());
        return toSettings(settings);
    }

    private User requiredUser(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private UserSettings requiredSettings(Long userId) {
        return userSettingsRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户设置不存在"));
    }

    private MeResponse toMe(User user, UserSettings settings) {
        return new MeResponse(user.getId(), user.getEmail(), user.getNickname(), user.getAvatarUrl(),
                user.getRole(), toSettings(settings));
    }

    private SettingsResponse toSettings(UserSettings settings) {
        return new SettingsResponse(settings.getModelProvider(), settings.getModelName(), settings.getLanguage(),
                settings.isNotifyEnabled(), settings.getTheme());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
