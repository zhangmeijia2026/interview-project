package com.group5.interview.module.resume;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** Apache Tika 统一抽取 PDF/DOCX 的纯文本。 */
@Component
public class TikaTextExtractor {
    public String extract(Path path) throws Exception {
        return new Tika().parseToString(path).trim();
    }
}
