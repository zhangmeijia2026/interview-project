package com.group5.interview.module.interview;

import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.entity.Interview;
import com.group5.interview.module.interview.dto.CreateInterviewRequest;
import com.group5.interview.module.interview.dto.InterviewResponse;
import com.group5.interview.repository.InterviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewService {
    private final InterviewRepository interviewRepository;

    @Transactional
    public InterviewResponse create(Long userId, CreateInterviewRequest request) {
        String mode = normalizeMode(request.mode());
        Interview interview = new Interview();
        interview.setUserId(userId);
        interview.setTitle(request.title().trim());
        interview.setTargetPosition(request.targetPosition().trim());
        interview.setStatus(InterviewStatus.DRAFT.value());
        interview.setMode(mode);
        interview.setWeakBoost(Boolean.TRUE.equals(request.weakBoost()));
        if ("practice".equals(mode)) {
            String category = request.practiceCategory() == null ? null : request.practiceCategory().trim();
            interview.setPracticeCategory(category == null || category.isBlank() ? null : category);
        }
        interview.setQuestionCount(request.questionCount() == null ? 10 : request.questionCount());
        interview.setCreatedAt(LocalDateTime.now());
        interview.setUpdatedAt(LocalDateTime.now());
        return toResponse(interviewRepository.save(interview));
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) return "formal";
        String m = mode.trim().toLowerCase();
        if (!"formal".equals(m) && !"practice".equals(m)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "mode 仅支持 formal 或 practice");
        }
        return m;
    }

    @Transactional(readOnly = true)
    public List<InterviewResponse> list(Long userId, String status) {
        return interviewRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(item -> matchesStatus(item, status)).map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InterviewResponse detail(Long id, Long userId) {
        return toResponse(required(id, userId));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        interviewRepository.delete(required(id, userId));
    }

    @Transactional
    public void bindResume(Long id, Long userId, Long resumeId) {
        Interview interview = required(id, userId);
        InterviewStatus current = InterviewStatus.from(interview.getStatus());
        if (current != InterviewStatus.DRAFT && current != InterviewStatus.RESUME_UPLOADED) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前面试状态不能上传简历");
        }
        interview.setResumeId(resumeId);
        interview.setUpdatedAt(LocalDateTime.now());
    }

    @Transactional
    public void confirmResume(Long id, Long userId) {
        Interview interview = required(id, userId);
        if (InterviewStatus.from(interview.getStatus()) != InterviewStatus.DRAFT || interview.getResumeId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "简历尚未处于可确认状态");
        }
        interview.setStatus(InterviewStatus.RESUME_UPLOADED.value());
        interview.setUpdatedAt(LocalDateTime.now());
    }

    /** 绑定 JD（可替换）：仅允许在简历已确认、尚未开始面试前的状态。 */
    @Transactional
    public void bindJd(Long id, Long userId, Long jdId) {
        Interview interview = required(id, userId);
        InterviewStatus current = InterviewStatus.from(interview.getStatus());
        if (current != InterviewStatus.DRAFT && current != InterviewStatus.RESUME_UPLOADED
                && current != InterviewStatus.JD_UPLOADED) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前面试状态不能上传 JD");
        }
        interview.setJdId(jdId);
        interview.setUpdatedAt(LocalDateTime.now());
    }

    /** JD 异步解析完成（ready）后推进状态：resume_uploaded -> jd_uploaded。 */
    @Transactional
    public void onJdReady(Long id, Long userId) {
        Interview interview = required(id, userId);
        if (InterviewStatus.from(interview.getStatus()) == InterviewStatus.RESUME_UPLOADED) {
            interview.setStatus(InterviewStatus.JD_UPLOADED.value());
            interview.setUpdatedAt(LocalDateTime.now());
        }
    }

    /** 匹配成功后推进状态到 matched 并回写匹配分。 */
    @Transactional
    public void markMatched(Long id, Long userId, java.math.BigDecimal overall) {
        Interview interview = required(id, userId);
        InterviewStatus current = InterviewStatus.from(interview.getStatus());
        if (current != InterviewStatus.RESUME_UPLOADED && current != InterviewStatus.JD_UPLOADED
                && current != InterviewStatus.MATCHED) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前面试状态不能执行匹配");
        }
        interview.setStatus(InterviewStatus.MATCHED.value());
        interview.setMatchScore(overall);
        interview.setUpdatedAt(LocalDateTime.now());
    }

    public Interview required(Long id, Long userId) {
        return interviewRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "面试会话不存在"));
    }

    private boolean matchesStatus(Interview item, String status) {
        if (status == null || status.isBlank() || "all".equals(status)) return true;
        if ("completed".equals(status)) return InterviewStatus.COMPLETED.value().equals(item.getStatus());
        if ("ongoing".equals(status)) return !InterviewStatus.COMPLETED.value().equals(item.getStatus())
                && !InterviewStatus.ABANDONED.value().equals(item.getStatus());
        throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "status 仅支持 all、ongoing、completed");
    }

    private InterviewResponse toResponse(Interview item) {
        return new InterviewResponse(item.getId(), item.getTitle(), item.getTargetPosition(), item.getStatus(),
                item.getResumeId(), item.getJdId(), item.getQuestionCount(), item.getCompletedQuestionCount(),
                item.getCreatedAt(), item.getMode(), item.isWeakBoost(), item.getPracticeCategory());
    }
}
