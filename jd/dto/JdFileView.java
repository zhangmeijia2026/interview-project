package com.group5.interview.module.jd.dto;

import java.nio.file.Path;

/** JD 原文件视图：路径 + 原始文件名（文件流端点用）。 */
public record JdFileView(Path path, String fileName) {
}
