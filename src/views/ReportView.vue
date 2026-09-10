<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import * as api from '../api';
import { labelOf, ratingText, ratingTagType } from '../utils';
import DimensionRadar from '../components/DimensionRadar.vue';
import QuestionReplayCard from '../components/QuestionReplayCard.vue';

const props = defineProps<{ id?: string | number }>();
const router = useRouter();

const interview = ref<api.Interview | null>(null);
const report = ref<api.ReportResponse | null>(null);
const loading = ref(true);
const failed = ref(false);
const failMsg = ref('');

const isPracticeReport = computed(() => report.value?.mode === 'practice');
const hasAnalysis = computed(() => {
  const a = report.value?.analysis;
  return !!(a && (a.overallAnalysis || (a.dimensionAnalyses && a.dimensionAnalyses.length) || a.conclusion));
});
const hasResumeAdvice = computed(() => {
  const r = report.value?.resumeAdvice;
  return !!(r && (r.intro || (r.groups && r.groups.length)));
});

/** 无深度分析时的兜底文案：由确定性子段拼接。 */
const fallbackAnalysisLines = computed<string[]>(() => {
  const r = report.value;
  if (!r) return [];
  const lines: string[] = [];
  if (r.strengths && r.strengths.length) lines.push('你的相对强项：' + r.strengths.join('；'));
  if (r.improvements && r.improvements.length) lines.push('建议重点加强：' + r.improvements.join('；'));
  if (r.recommendation) lines.push('总体建议：' + r.recommendation);
  return lines;
});

/** 无详细简历建议时的兜底文案。 */
const fallbackResumeAdviceText = computed(() => {
  const r = report.value;
  if (!r) return '';
  const parts = ['本场面试未生成分优先级的详细简历修改建议，可参考以下现有信息完善简历：'];
  if (r.improvements && r.improvements.length) parts.push('· ' + r.improvements.join('；'));
  const hasQuestionAdvice = (r.questions || []).some((q) => !!q.resumeAdvice);
  if (hasQuestionAdvice) parts.push('· 逐题回放中的「简历建议」条目可直接采纳到对应项目描述。');
  return parts.join('\n');
});

const sortedGroups = computed(() => {
  const g = report.value?.resumeAdvice?.groups || [];
  const order: Record<string, number> = { HIGH: 0, MED: 1, LOW: 2 };
  return [...g].sort((a, b) => (order[a.priority] ?? 3) - (order[b.priority] ?? 3));
});

function priorityMeta(p?: string): { text: string; type: string } {
  if (p === 'HIGH') return { text: '高优先', type: 'danger' };
  if (p === 'MED') return { text: '中优先', type: 'warning' };
  return { text: '低优先', type: 'info' };
}

async function load() {
  const id = Number(props.id);
  if (!Number.isFinite(id)) { router.replace('/home'); return; }
  loading.value = true;
  failed.value = false;
  try {
    interview.value = await api.getInterview(id).catch(() => null);
    report.value = await api.getReport(id);
  } catch (e) {
    failed.value = true;
    failMsg.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div v-loading="loading" class="page">
    <!-- 顶部工具条 -->
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px">
      <div style="display: flex; align-items: center; gap: 10px">
        <el-button text type="primary" @click="router.push('/home')">← 返回工作台</el-button>
        <el-tag v-if="report && !isPracticeReport" :type="ratingTagType(report.rating)" effect="plain">综合报告</el-tag>
        <el-tag v-else-if="report && isPracticeReport" type="warning" effect="plain">练习复盘 · 无评级</el-tag>
        <span v-if="interview" class="muted">{{ interview.title }}</span>
      </div>
    </div>

    <!-- 报告主体 -->
    <template v-if="report">
      <!-- 概览/评级卡 -->
      <el-card shadow="never" class="rating-card">
        <template v-if="isPracticeReport">
          <el-alert type="warning" :closable="false" title="本场为练习模式，无正式评级。以下为练习复盘与作答反馈，可对照参考答案复习。" style="margin-bottom: 12px" />
        </template>
        <div class="rating-row">
          <div>
            <template v-if="!isPracticeReport">
              <el-tag :type="ratingTagType(report.rating)" size="large" effect="dark" class="rating-tag">
                {{ report.rating }} · {{ ratingText(report.rating) }}
              </el-tag>
            </template>
            <template v-else>
              <el-tag type="warning" size="large" effect="dark" class="rating-tag">练习复盘</el-tag>
            </template>
            <div class="muted" style="margin-top: 6px">
              <template v-if="!isPracticeReport">
                综合匹配分 {{ report.matchScore ?? '-' }}
                <template v-if="interview"> · {{ interview.targetPosition }}</template>
              </template>
              <template v-else-if="interview">目标：{{ interview.targetPosition }}</template>
              <template v-if="interview">（{{ interview.completedQuestionCount }}/{{ interview.questionCount }} 题）</template>
            </div>
          </div>
          <el-alert type="info" :closable="false" :title="report.recommendation || '保持当前节奏持续练习。'" class="rec" />
        </div>

        <el-row :gutter="24" style="margin-top: 8px">
          <el-col :xs="24" :md="12">
            <h4 style="margin-bottom: 6px">六维能力雷达</h4>
            <DimensionRadar :dimensions="report.dimensions" />
          </el-col>
          <el-col :xs="24" :md="12">
            <h4>强项</h4>
            <ul>
              <li v-for="(s, i) in report.strengths" :key="i">{{ s }}</li>
            </ul>
            <h4>待加强</h4>
            <ul>
              <li v-for="(s, i) in report.improvements" :key="i">{{ s }}</li>
            </ul>
          </el-col>
        </el-row>
      </el-card>

      <!-- A：面试结果详细分析（练习模式为复盘要点） -->
      <el-card shadow="never" class="mt">
        <template #header><b>{{ isPracticeReport ? '复盘要点' : '面试结果详细分析' }}</b></template>
        <template v-if="hasAnalysis && report.analysis">
          <p style="white-space: pre-wrap; margin: 0 0 12px">{{ report.analysis.overallAnalysis }}</p>

          <div v-if="report.analysis.dimensionAnalyses && report.analysis.dimensionAnalyses.length" class="dim-cards">
            <el-card v-for="d in report.analysis.dimensionAnalyses" :key="d.dimension" shadow="never" class="dim-card">
              <div class="dim-head">
                <b>{{ labelOf(d.dimension) }}</b>
                <el-tag size="small" :type="d.score >= 75 ? 'success' : d.score >= 55 ? 'warning' : 'danger'">
                  {{ d.score }} 分
                </el-tag>
              </div>
              <p class="dim-text">{{ d.analysis }}</p>
              <p class="dim-advice">改进建议：{{ d.advice }}</p>
            </el-card>
          </div>

          <el-alert v-if="report.analysis.conclusion" type="success" :closable="false" class="mt">
            <template #title>总结：{{ report.analysis.conclusion }}</template>
          </el-alert>
        </template>
        <template v-else>
          <el-alert type="info" :closable="false" title="本场未生成逐维度深度点评（历史数据或生成失败），以下为确定性字段兜底：" style="margin-bottom: 10px" />
          <p v-for="(line, i) in fallbackAnalysisLines" :key="i" style="margin: 6px 0">{{ line }}</p>
        </template>
      </el-card>

      <!-- B：详细简历修改建议（仅正式面试展示） -->
      <el-card v-if="!isPracticeReport" shadow="never" class="mt">
        <template #header><b>详细简历修改建议</b></template>
        <template v-if="hasResumeAdvice && report.resumeAdvice">
          <p v-if="report.resumeAdvice.intro" style="white-space: pre-wrap; margin: 0 0 12px">{{ report.resumeAdvice.intro }}</p>
          <div v-for="g in sortedGroups" :key="g.title" class="advice-group">
            <div class="advice-group-head">
              <b>{{ g.title }}</b>
              <el-tag :type="priorityMeta(g.priority).type" size="small" effect="dark">
                {{ priorityMeta(g.priority).text }}
              </el-tag>
            </div>
            <ul class="advice-items">
              <li v-for="(it, i) in g.items" :key="i">{{ it }}</li>
            </ul>
          </div>
        </template>
        <template v-else>
          <p style="white-space: pre-wrap; margin: 0">{{ fallbackResumeAdviceText }}</p>
        </template>
      </el-card>

      <!-- 逐题回放 -->
      <el-card shadow="never" class="mt">
        <template #header><b>逐题回放（含每题点评与追问）</b></template>
        <el-timeline v-if="report.questions && report.questions.length">
          <QuestionReplayCard v-for="q in report.questions" :key="q.orderIndex" :q="q" />
        </el-timeline>
        <el-empty v-else description="暂无题目回放数据" :image-size="72" />
      </el-card>
    </template>

    <!-- 失败 / 异常空态 -->
    <el-result v-else-if="!loading && failed" icon="error" title="报告加载失败" :sub-title="failMsg">
      <template #extra>
        <el-button type="primary" style="background: #1a237e" @click="router.push('/home')">返回工作台</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.rating-card .rating-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.rating-tag { font-size: 18px; padding: 6px 14px; }
.rec { flex: 1; min-width: 260px; }
.dim-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }
.dim-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.dim-text { color: #303133; font-size: 13px; line-height: 1.7; margin: 6px 0; }
.dim-advice { color: #5c6bc0; font-size: 13px; margin: 0; }
.advice-group { border: 1px solid #ebeef5; border-radius: 8px; padding: 12px 14px; margin-bottom: 10px; }
.advice-group-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.advice-items { margin: 4px 0 0; padding-left: 18px; line-height: 1.9; }
</style>
