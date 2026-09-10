package com.group5.interview.module.engine;

/** 面试级 SSE 事件（docs/06 §3）：event=type，data=JSON 文本。 */
public record SseEvent(String type, String data) {
    public static SseEvent of(String type, String data) {
        return new SseEvent(type, data);
    }
}
