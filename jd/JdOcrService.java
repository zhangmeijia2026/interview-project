package com.group5.interview.module.jd;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JD 图片文字识别（Tesseract 离线 OCR，chi_sim+eng，docs/03 安装说明）。
 *
 * <p>引擎或 tessdata 未就绪 / 识别失败时抛出带中文提示的异常，
 * 由 JdAsyncProcessor 记入失败状态并引导用户改为「粘贴文字」兜底，不阻塞其余功能。</p>
 */
@Slf4j
@Component
public class JdOcrService {

    private final String datapath;
    private final String language;

    public JdOcrService(@Value("${app.ocr.datapath}") String datapath,
                        @Value("${app.ocr.language:chi_sim+eng}") String language) {
        this.datapath = datapath;
        this.language = language;
    }

    /**
     * 解析 tessdata 目录（启动目录无关），并交给 tess4j 前尽量转成<b>相对 cwd 的路径</b>：
     * <ol>
     *   <li>配置值（绝对 OCR_DATAPATH 或相对）本身是存在的目录 → 采用（绝对路径原样尊重用户设置）；</li>
     *   <li>否则按 cwd → cwd/backend → 逐级父目录(+backend) 找名为 datapath 的目录，
     *       兼容从仓库根 / 上级目录 / IDE 启动后端的情况；</li>
     *   <li>找到的候选若在 cwd 之下，返回<b>相对 cwd 路径</b>——本机仓库父路径含中文
     *       （D:\生产实习Claude\…），tess4j 原生库解析<b>含中文的绝对路径</b>会失败
     *       （"couldn't load any languages"，实测 2026-09-05），相对路径不含中文前缀、由原生层按 cwd 解析则正常；
     *       见 docs/03 §2「Windows 中文路径注意事项」；</li>
     *   <li>找不到抛中文异常，引导用户准备 tessdata 或改用粘贴文字。</li>
     * </ol>
     */
    private Path resolveDatapath() {
        String cwd = System.getProperty("user.dir", ".");
        Path cwdAbs = Path.of(cwd).toAbsolutePath().normalize();
        Path configured = Path.of(datapath);

        // ① 配置值即存在的目录
        Path configuredAbs = configured.isAbsolute()
                ? configured.normalize()
                : cwdAbs.resolve(configured).normalize();
        if (Files.isDirectory(configuredAbs)) {
            return configured.isAbsolute() ? configuredAbs : configured;
        }

        // ② 自动定位：cwd → cwd/backend → 逐级父目录(+backend)
        Path probe = cwdAbs;
        List<Path> candidates = new ArrayList<>();
        candidates.add(cwdAbs.resolve(datapath));              // cwd/ocr-tessdata
        candidates.add(cwdAbs.resolve("backend").resolve(datapath)); // cwd/backend/ocr-tessdata
        for (int i = 0; i < 4 && probe.getParent() != null; i++) {
            probe = probe.getParent();
            candidates.add(probe.resolve("backend").resolve(datapath));
            candidates.add(probe.resolve(datapath));
        }
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate.normalize())) {
                Path found = candidate.normalize();
                log.info("OCR tessdata 目录自动定位为 {}（配置 {}，cwd={}）", found, datapath, cwd);
                // ③ 位于 cwd 之下 → 返回相对路径，避开中文绝对路径给原生库的坑
                try {
                    Path rel = cwdAbs.relativize(found);
                    if (!rel.toString().startsWith("..")) {
                        return rel;
                    }
                } catch (IllegalArgumentException ignored) {
                    // 不同盘符无法 relativize，退回绝对路径
                }
                return found;
            }
        }
        throw new IllegalStateException("OCR 语言数据目录不存在（已尝试「" + datapath
                + "」及 cwd/backend、上级目录等候选），请按 docs/03 §OCR 准备 tessdata，或改用粘贴文字上传 JD");
    }

    /** 识别图片中的文字并返回（已 trim）。失败抛异常，由调用方决定降级策略。 */
    public String ocrText(Path imagePath) {
        Path dir = resolveDatapath();
        for (String lang : language.split("\\+")) {
            if (!Files.exists(dir.resolve(lang + ".traineddata"))) {
                throw new IllegalStateException("OCR 缺少语言包 " + lang + ".traineddata（"
                        + dir + "），请按 docs/03 §OCR 准备，或改用粘贴文字上传 JD");
            }
        }
        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(dir.toString());
            tesseract.setLanguage(language);
            String text = tesseract.doOCR(imagePath.toFile());
            return text == null ? "" : text.trim();
        } catch (Exception e) {
            log.warn("JD 图片 OCR 失败 {}: {}", imagePath, e.getMessage());
            throw new IllegalStateException("图片文字识别失败，请确认图片清晰且包含中文/英文，或改用粘贴文字上传 JD");
        }
    }
}
