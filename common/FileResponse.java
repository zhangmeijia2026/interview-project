package com.group5.interview.common;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 历史 JD/简历"查看原文"文件流工具：校验存在后按扩展名返回 inline 资源，
 * 便于浏览器直接预览图片/PDF；DOCX 等不可内联类型降级为下载。统一走真实文件，
 * 不经过 ApiResponse JSON 包装（docs/06 文件流端点）。
 */
public final class FileResponse {

    private FileResponse() {
    }

    /** 以 inline 方式流式返回上传目录内的原文件（fileName 用于浏览器文件名/类型推断）。 */
    public static ResponseEntity<Resource> inline(Path path, String fileName) {
        try {
            FileSystemResource resource = new FileSystemResource(path);
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "原文件不存在或已被移动");
            }
            String displayName = fileName == null || fileName.isBlank() ? path.getFileName().toString() : fileName;
            String disposition = ContentDisposition.inline()
                    .filename(displayName, StandardCharsets.UTF_8)
                    .build().toString();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .contentType(mediaType(displayName))
                    .body(resource);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL, "读取原文件失败");
        }
    }

    private static MediaType mediaType(String fileName) {
        String ext = fileName == null ? "" : fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "jpg", "jpeg" -> MediaType.valueOf("image/jpeg");
            case "png" -> MediaType.valueOf("image/png");
            case "bmp" -> MediaType.valueOf("image/bmp");
            case "webp" -> MediaType.valueOf("image/webp");
            case "docx" -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "doc" -> MediaType.valueOf("application/msword");
            case "txt" -> MediaType.TEXT_PLAIN;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
