package com.group5.interview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 智能面试问答系统 —— 后端入口。
 * 依赖数据库 interview_system 与 Redis 6379（详见 application-local.yml）。
 */
@EnableAsync
@SpringBootApplication
public class InterviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewApplication.class, args);
    }
}
