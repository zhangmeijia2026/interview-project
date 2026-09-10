<script setup lang="ts">
import type { ReportQuestion } from '../api';

defineProps<{ q: ReportQuestion }>();

const ORDER_CN = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十'];
function orderCn(n: number): string {
  return n <= 10 ? ORDER_CN[n - 1] : String(n);
}
</script>

<template>
  <el-timeline-item
    :timestamp="'第 ' + q.orderIndex + ' 题 · ' + (q.score ?? '-') + ' 分'"
    placement="top"
  >
    <p style="margin: 0 0 4px"><b>{{ orderCn(q.orderIndex) }}、{{ q.question }}</b></p>
    <p class="muted">你的回答：{{ q.answer || '（跳过 / 未作答）' }}</p>

    <!-- 参考回答 / 提示对照 -->
    <template v-if="q.referenceAnswer || q.hint">
      <el-alert v-if="q.referenceAnswer" type="success" :closable="false" style="margin-top: 6px">
        <template #title><b>参考答案：</b>{{ q.referenceAnswer }}</template>
      </el-alert>
      <p v-if="q.hint" class="muted" style="margin: 4px 0 0">提示：{{ q.hint }}</p>
    </template>

    <!-- 点评：优点 / 待改进 / 建议（旧报告可能缺省） -->
    <div v-if="q.strong && q.strong.length" style="margin-top: 4px">
      <el-tag v-for="(s, i) in q.strong" :key="'s' + i" size="small" type="success" effect="plain" style="margin: 0 6px 4px 0">
        ✓ {{ s }}
      </el-tag>
    </div>
    <div v-if="q.weak && q.weak.length" style="margin-top: 2px">
      <span v-for="(w, i) in q.weak" :key="'w' + i">
        <el-tag size="small" type="warning" effect="plain">待改进</el-tag>
        <span class="muted" style="margin-right: 8px">{{ w }}</span>
      </span>
    </div>
    <p v-if="q.suggestion" class="mt" style="margin-bottom: 2px">
      <b>改进建议：</b>{{ q.suggestion }}
    </p>
    <p v-if="q.resumeAdvice" class="mt">
      <el-tag size="small" type="info" effect="plain">简历建议</el-tag>
      <span style="margin-left: 4px">{{ q.resumeAdvice }}</span>
    </p>

    <!-- 追问 -->
    <el-alert
      v-if="q.followUp"
      type="warning"
      :closable="false"
      class="mt"
      style="margin-top: 6px"
    >
      <template #title>面试官追问：{{ q.followUp.question }}</template>
      <div v-if="q.followUp.answer" class="muted">
        你的追问回答：{{ q.followUp.answer }}
        <span v-if="q.followUp.score != null"> · {{ q.followUp.score }} 分</span>
      </div>
      <div v-else class="muted">未回答追问</div>
    </el-alert>
  </el-timeline-item>
</template>
