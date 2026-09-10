package com.group5.interview.module.admin;

import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.entity.User;
import com.group5.interview.entity.UserSettings;
import com.group5.interview.module.admin.dto.AdminCreateAdminRequest;
import com.group5.interview.module.admin.dto.AdminUserView;
import com.group5.interview.repository.UserRepository;
import com.group5.interview.repository.UserSettingsRepository;
import com.group5.interview.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 管理端账号操作（2026-09-07）：新增管理员 + 账号停用/启用（停用即黑名单，复用 users.is_active）。
 * 停用即时生效由 {@link com.group5.interview.security.JwtAuthFilter} 逐请求校验 active 保证；
 * 停用前做守卫：不能停用自己、不能停用最后一位启用中的管理员。
 */
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);
    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    /** 停用(false)/启用(true) 账号。 */
    @Transactional
    public AdminUserView setActive(Long id, boolean active) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账号不存在"));
        if (!active) {
            if (target.isAdmin()) {
                Long operatorId = UserContext.currentUserIdOrNull();
                if (operatorId != null && operatorId.equals(target.getId())) {
                    throw new BusinessException(ErrorCode.CONFLICT, "不能停用当前登录的管理员账号");
                }
                if (userRepository.countByRoleAndActiveTrue("admin") <= 1) {
                    throw new BusinessException(ErrorCode.CONFLICT, "系统至少需要保留一位启用中的管理员");
                }
            }
        }
        target.setActive(active);
        userRepository.save(target);
        return toView(target);
    }

    /** 新增管理员（邮箱唯一、密码≥8 位、bcrypt cost 12，同时建 UserSettings，参照注册/种子）。 */
    @Transactional
    public AdminUserView createAdmin(AdminCreateAdminRequest request) {
        if (request.email() == null || !EMAIL.matcher(request.email().trim()).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "邮箱格式不正确");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该邮箱已注册，请直接登录");
        }
        if (request.password() == null || request.password().length() < 8) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "密码长度至少 8 位");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(ENCODER.encode(request.password()));
        String nickname = request.nickname() == null || request.nickname().isBlank()
                ? email.substring(0, email.indexOf('@')) : request.nickname().trim();
        user.setNickname(nickname.length() > 50 ? nickname.substring(0, 50) : nickname);
        user.setRole("admin");
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);

        UserSettings settings = new UserSettings();
        settings.setUserId(user.getId());
        settings.setUpdatedAt(LocalDateTime.now());
        userSettingsRepository.save(settings);
        return toView(user);
    }

    private AdminUserView toView(User u) {
        return new AdminUserView(u.getId(), u.getEmail(), u.getNickname(), u.getRole(),
                u.isActive(), u.getCreatedAt(), u.getLastLoginAt());
    }
}
