package com.group5.interview.module.interview;

import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;

/** 面试主状态机；解析中的状态留在 resumes.status，不污染会话状态。 */
public enum InterviewStatus {
    DRAFT("draft"), RESUME_UPLOADED("resume_uploaded"), JD_UPLOADED("jd_uploaded"),
    MATCHED("matched"), READY("ready"), IN_PROGRESS("in_progress"),
    COMPLETED("completed"), ABANDONED("abandoned");
    private final String value;
    InterviewStatus(String value) { this.value = value; }
    public String value() { return value; }
    public static InterviewStatus from(String value) {
        for (InterviewStatus status : values()) if (status.value.equals(value)) return status;
        throw new BusinessException(ErrorCode.CONFLICT, "未知面试状态");
    }
    public boolean canTransitionTo(InterviewStatus target) {
        return target == ABANDONED && this != COMPLETED && this != ABANDONED;
    }
}
