package com.group5.interview.ai.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 确定性本地向量化：把文本切成特征 token（CJK 走相邻二字，拉丁/数字走词形），
 * 用双哈希(特征哈希 trick)散列进 dimension 维，加权后再做 L2 归一化。
 *
 * <p>在同一文本上结果稳定可复现，余弦相似度能反映词面/短语层面的重合度——
 * 用于把简历/JD/题库知识点这类同源语料在向量空间里做 top-k 召回。无外部模型依赖，
 * 离线 mock/CI 均可跑；如需真实语义向量，替换实现或升档到 onnx-bge（docs/07 §10）。</p>
 */
@Service
@RequiredArgsConstructor
public class HashEmbeddingService implements EmbeddingService {

    private final RagProperties properties;

    @Override
    public int dimension() {
        return Math.max(8, properties.getDimension());
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) return new float[dimension()];
        int dim = dimension();
        float[] vector = new float[dim];
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) return new float[dim];
        // 频率 → 亚线性权重：1 + ln(count)，缓解高频词主导
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (String token : tokens) counts.merge(token, 1, Integer::sum);
        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
            String token = entry.getKey();
            double weight = 1.0 + Math.log(entry.getValue());
            int h1 = hash(token, 1);
            int idx1 = Math.floorMod(h1, dim);
            int h2 = hash(token, 2);
            int idx2 = Math.floorMod(h2, dim);
            float sign = ((h1 >>> 30) & 1) == 0 ? 1f : -1f;
            vector[idx1] += (float) (sign * weight);
            vector[idx2] += (float) (sign * weight);
        }
        return normalize(vector);
    }

    /** 归一化到单位向量（欧氏范数=1），供点积=余弦。 */
    static float[] normalize(float[] vector) {
        double norm = 0;
        for (float value : vector) norm += (double) value * value;
        norm = Math.sqrt(norm);
        if (norm == 0) return vector;
        for (int i = 0; i < vector.length; i++) vector[i] /= (float) norm;
        return vector;
    }

    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** 轻量分词：连续汉字→相邻二字；拉丁/数字→按空白/标点切出词形小写。 */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        int length = text.length();
        int i = 0;
        while (i < length) {
            char ch = text.charAt(i);
            if (isHan(ch)) {
                // 汉字连续串：加入单字与相邻二字
                int start = i;
                while (i < length && isHan(text.charAt(i))) i++;
                String run = text.substring(start, i);
                for (int k = 0; k < run.length(); k++) {
                    tokens.add(String.valueOf(run.charAt(k)));
                    if (k + 1 < run.length()) tokens.add(run.substring(k, k + 2));
                }
            } else if (Character.isLetterOrDigit(ch) || ch == '_') {
                int start = i;
                while (i < length && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) i++;
                tokens.add(text.substring(start, i).toLowerCase(java.util.Locale.ROOT));
            } else {
                i++;
            }
        }
        return tokens;
    }

    private static boolean isHan(char ch) {
        return Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN;
    }

    private static int hash(String token, int seed) {
        int h = seed;
        for (int j = 0; j < token.length(); j++) {
            h = 31 * h + token.charAt(j);
        }
        return h;
    }
}
