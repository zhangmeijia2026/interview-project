package com.group5.interview.common;

import java.util.List;

/** 通用分页视图（题库浏览 / 错题本 / 后台列表等）。 */
public record PageResult<T>(List<T> items, long total) {
}
