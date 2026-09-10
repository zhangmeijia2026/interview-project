package com.group5.interview.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 开启 @Scheduled（SSE 心跳等）。 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
