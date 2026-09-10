package com.group5.interview.ai;

/** 所有大模型调用的唯一入口，业务模块不得直接依赖具体模型客户端。 */
public interface AIService {
    <T> T structured(String promptKey, Object context, Class<T> clazz);
}
