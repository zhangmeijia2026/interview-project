<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import * as api from '../api';
import { modeTagType, modeText, statusText, statusTagType } from '../utils';

const props = defineProps<{ id?: string | number }>();
const router = useRouter();

// 正式模式单题限时（秒）。后端无每题时长字段，前端统一 3 分钟。
const FORMAL_LIMIT_SECONDS = 180;

const interview = ref<api.Interview | null>(null);
const session = ref<api.SessionResponse | null>(null);
const currentQ = ref<api.QuestionSummary | null>(null);
const progress = reactive({ done: 0, total: 0 });
const answerText = ref('');
const ans = ref<api.AnswerResponse | null>(null);
const followUpText = ref('');
const fuMode = ref(false);
const fuNote = ref<string | null>(null);
const feedbackView = ref(false);
const busy = ref(false);
const initLoading = ref(true);

// 参考回答 / 提示（练习模式可在作答前查看；正式模式作答后由 AnswerResponse 带出）
const curRef = ref<api.ReferenceView | null>(null);
const refLoading = ref(false);

// 正式模式倒计时
const remain = ref(FORMAL_LIMIT_SECONDS);
let timerId: number | null = null;

const interviewId = computed<number>(() => Number(props.id));
const isPractice = computed(() => interview.value?.mode === 'practice');
const isFormalTiming = computed(() => !isPractice.value && !!currentQ.value && !feedbackView.value && !ans.value);

function formatRemain(sec: number): string {
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function stopTimer() {
  if (timerId !== null) {
    window.clearInterval(timerId);
    timerId = null;
  }
}

function startTimer() {
  stopTimer();
  remain.value = FORMAL_LIMIT_SECONDS;
  if (isPractice.value) return;
  timerId = window.setInterval(() => {
    remain.value -= 1;
    if (remain.value <= 0) {
      stopTimer();
      onAutoTimeout();
    }
  }, 1000);
}

/** 作答期间经过的秒数（提交给后端 answeredSeconds） */
function elapsedSeconds(): number {
  return Math.min(FORMAL_LIMIT_SECONDS, FORMAL_LIMIT_SECONDS - remain.value);
}

async function loadReference() {
  if (!currentQ.value || !interview.value) return;
  refLoading.value = true;
  try {
    curRef.value = await api.getReference(interview.value.id, currentQ.value.orderIndex);
  } catch {
    curRef.value = null;
  } finally {
    refLoading.value = false;
  }
}

/** 展示新一题时统一复位 */
function onQuestionShown(q: api.QuestionSummary | null) {
  currentQ.value = q;
  ans.value = null;
  answerText.value = '';
  followUpText.value = '';
  fuMode.value = false;
  feedbackView.value = false;
  fuNote.value = null;
  curRef.value = null;
  if (!q) return;
  if (isPractice.value) {
    loadReference();
  } else {
    startTimer();
  }
}

async function init() {
  const id = interviewId.value;
  if (!Number.isFinite(id)) {
    router.replace('/home');
    return;
  }
  initLoading.value = true;
  try {
    const it = await api.getInterview(id);
    interview.value = it;
    if (it.status === 'completed') {
      ElMessage.info('该场面试已完成');
      router.replace(`/interviews/${id}/report`);
      return;
    }
    if (it.status === 'abandoned') {
      ElMessage.warning('该场面试已放弃，不能继续答题');
      router.replace('/home');
      return;
    }
    const practice = it.mode === 'practice';
    const readyStatuses = practice ? ['draft', 'in_progress'] : ['matched', 'ready', 'in_progress'];
    if (!readyStatuses.includes(it.status)) {
      ElMessage.info(practice ? '请先进入练习页开始练习' : '面试资料尚未准备完成，请先完善简历 / JD / 匹配');
      router.replace(`/interviews/${id}/setup`);
      return;
    }
    const s = it.status === 'in_progress'
      ? await api.getSession(id)
      : await api.startSession(id);
    session.value = s;
    progress.total = s.questionCount;
    progress.done = s.completedCount;
    interview.value = await api.getInterview(id); // 拉最新状态（in_progress）
    onQuestionShown(s.current || null);
  } catch (e) {
    ElMessage.error((e as Error).message);
    router.replace('/home');
  } finally {
    initLoading.value = false;
  }
}

onMounted(init);
onBeforeUnmount(stopTimer);

async function onAnswer(auto = false) {
  if (!currentQ.value || !interview.value) return;
  const text = answerText.value.trim();
  if (!text) {
    if (!auto) {
      ElMessage.warning('请先输入你的回答');
      return;
    }
    // 超时自动提交且无内容：用占位文本推进流程（后端作答非空校验）
    answerText.value = '（本题作答超时，未填写具体内容）';
  }
  if (answerText.value.length > 8000) answerText.value = answerText.value.slice(0, 8000);
  busy.value = true;
  const elapsed = isPractice.value ? undefined : elapsedSeconds();
  stopTimer();
  try {
    ans.value = await api.submitAnswer(interview.value.id, currentQ.value.orderIndex, answerText.value, elapsed);
    progress.done = Math.max(progress.done, ans.value.orderIndex);
    answerText.value = '';
    if (ans.value.suggestFollowup && ans.value.followUp) {
      fuMode.value = true;
      feedbackView.value = false;
      return;
    }
    fuMode.value = false;
    feedbackView.value = true;
  } catch (e) {
    ElMessage.error((e as Error).message);
    // 提交失败：恢复计时（若仍在作答阶段）
    if (!ans.value && !fuMode.value && !feedbackView.value) startTimer();
  } finally {
    busy.value = false;
  }
}

async function onAutoTimeout() {
  if (busy.value || fuMode.value || feedbackView.value || !currentQ.value) return;
  ElMessage.warning('作答时间到，已自动提交当前内容');
  await onAnswer(true);
}

async function onFollowUpSubmit() {
  if (!currentQ.value || !interview.value || !ans.value?.followUp) return;
  if (!followUpText.value.trim()) {
    ElMessage.warning('请先输入追问回答');
    return;
  }
  busy.value = true;
  try {
    const res = await api.submitFollowUp(interview.value.id, currentQ.value.orderIndex, followUpText.value);
    fuMode.value = false;
    followUpText.value = '';
    fuNote.value = (res.score != null ? `追问得分 ${res.score} 分；` : '') + (res.suggestion || '');
    feedbackView.value = true;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}

async function onSkipFollowUp() {
  if (!currentQ.value || !interview.value) return;
  busy.value = true;
  try {
    await api.skipFollowUp(interview.value.id, currentQ.value.orderIndex);
    fuMode.value = false;
    followUpText.value = '';
    fuNote.value = '（已跳过追问）';
    feedbackView.value = true;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}

/** 反馈页 → 下一题 / 查看综合报告（跳独立报告页） */
function nextFromFeedback() {
  const a = ans.value;
  if (!a || !interview.value) return;
  fuNote.value = null;
  if (a.finished) {
    router.push(`/interviews/${interview.value.id}/report`);
    return;
  }
  onQuestionShown(a.nextQuestion || null);
}

async function onExit() {
  const mid = !!currentQ.value || !!feedbackView.value || (interview.value?.completedQuestionCount || 0) > 0;
  if (mid) {
    try {
      await ElMessageBox.confirm(
        '退出后本次答题进度已保存在后端，可在「历史记录 → 继续答题」断点续面。确定退出？',
        '提示', { type: 'warning', confirmButtonText: '退出', cancelButtonText: '留下继续' },
      );
    } catch { return; }
  }
  stopTimer();
  router.push('/home');
}

const percent = computed(() => {
  if (!progress.total) return 0;
  return Math.round((progress.done / progress.total) * 100);
});
</script>

<template>
  <div v-loading="initLoading" class="page">
    <!-- 顶部：会话信息 + 进度 -->
    <el-card v-if="interview" shadow="never">
      <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px">
        <div>
          <el-tag :type="modeTagType(interview.mode)" size="small">{{ modeText(interview.mode) }}</el-tag>
          <el-tag :type="statusTagType(interview.status)" size="small" style="margin-left: 4px">{{ statusText(interview.status) }}</el-tag>
          <span style="font-weight: 600; margin-left: 8px">{{ interview.title }}</span>
          <span class="muted" style="margin-left: 8px">目标岗位：{{ interview.targetPosition }}</span>
        </div>
        <div style="display: flex; align-items: center; gap: 10px">
          <el-tag v-if="isFormalTiming" :type="remain <= 30 ? 'danger' : 'primary'" effect="dark" class="timer-tag">
            ⏱ {{ formatRemain(remain) }}
          </el-tag>
          <el-button text type="danger" @click="onExit">退出面试</el-button>
        </div>
      </div>
      <div style="margin-top: 12px">
        <el-progress :percentage="percent" :stroke-width="10" :format="() => `已完成 ${progress.done} / ${progress.total} 题`" />
      </div>
    </el-card>

    <!-- 题目与作答 -->
    <el-card v-if="currentQ && !feedbackView" shadow="never" class="mt">
      <template #header>
        <b>第 {{ currentQ.orderIndex }} 题 / 共 {{ progress.total }} 题</b>
        <span v-if="interview?.mode === 'practice'" class="muted" style="margin-left: 8px">练习模式 · 随时可看提示与参考答案</span>
      </template>

      <el-alert type="info" :closable="false" class="mt">
        <template #title>{{ currentQ.content }}</template>
      </el-alert>

      <!-- 练习模式：作答前提示 / 参考答案 -->
      <el-collapse v-if="isPractice && !fuMode" class="mt">
        <el-collapse-item title="查看提示">
          <p v-if="curRef?.hint" class="ref-text">{{ curRef.hint }}</p>
          <p v-else class="muted">暂无提示</p>
        </el-collapse-item>
        <el-collapse-item title="查看参考答案">
          <p v-if="curRef?.referenceAnswer" class="ref-text">{{ curRef.referenceAnswer }}</p>
          <p v-else class="muted">暂无参考答案</p>
        </el-collapse-item>
      </el-collapse>

      <!-- 正式模式：提示作答时长 -->
      <el-alert v-if="isFormalTiming" type="warning" :closable="false" class="mt" :title="`请在 ${FORMAL_LIMIT_SECONDS} 秒内完成作答，超时将自动提交。`" />

      <!-- 作答输入 -->
      <div v-if="!fuMode" class="mt">
        <el-input
          v-model="answerText"
          type="textarea"
          :rows="6"
          maxlength="8000"
          show-word-limit
          placeholder="在此输入你的回答…"
        />
        <el-button class="mt" type="primary" style="background: #1a237e" :loading="busy" @click="onAnswer(false)">
          提交作答
        </el-button>
      </div>

      <!-- 追问输入 -->
      <div v-else class="mt">
        <el-alert type="warning" :closable="false" title="面试官追问">
          {{ ans?.followUp?.content }}
        </el-alert>
        <el-input v-model="followUpText" type="textarea" :rows="4" class="mt" placeholder="回答追问…" />
        <div style="display: flex; gap: 10px; margin-top: 12px">
          <el-button type="primary" style="background: #1a237e" :loading="busy" @click="onFollowUpSubmit">
            提交追问回答
          </el-button>
          <el-button :loading="busy" @click="onSkipFollowUp">跳过追问</el-button>
        </div>
      </div>
    </el-card>

    <!-- 本题 AI 反馈 -->
    <el-card v-if="feedbackView && ans" shadow="never" class="mt">
      <template #header>
        <b>AI 反馈 · 第 {{ ans.orderIndex }} 题 · {{ ans.score }} 分</b>
      </template>

      <el-divider content-position="left">对回答的分析</el-divider>
      <el-space wrap>
        <el-tag v-for="(s, i) in ans.strong" :key="'s' + i" type="success">✓ {{ s }}</el-tag>
      </el-space>
      <div v-if="ans.weak.length">
        <div v-for="(w, i) in ans.weak" :key="'w' + i" class="muted" style="margin-top: 6px">
          <el-tag size="small" type="warning" effect="plain">待改进</el-tag> {{ w }}
        </div>
      </div>
      <p class="mt"><b>改进建议：</b>{{ ans.suggestion }}</p>

      <!-- 参考答案对照 -->
      <template v-if="ans.referenceAnswer || ans.hint">
        <el-divider content-position="left">参考答案对照</el-divider>
        <el-alert v-if="ans.referenceAnswer" type="success" :closable="false">
          <template #title><b>参考答案：</b>{{ ans.referenceAnswer }}</template>
        </el-alert>
        <el-alert v-if="ans.hint" type="info" :closable="false" class="mt">
          <template #title><b>提示：</b>{{ ans.hint }}</template>
        </el-alert>
      </template>

      <el-divider content-position="left">对简历的修改建议</el-divider>
      <el-alert v-if="ans.resumeAdvice" type="info" :closable="false" :title="ans.resumeAdvice" />
      <el-alert v-else type="info" :closable="false" title="本轮没有针对简历的特别修改建议。" />

      <p v-if="fuNote" class="muted" style="margin-top: 6px">追问情况：{{ fuNote }}</p>

      <el-button class="mt" type="primary" style="background: #1a237e" @click="nextFromFeedback">
        {{ ans.finished ? (interview?.mode === 'practice' ? '查看复盘报告 →' : '查看综合报告 →') : '下一题 →' }}
      </el-button>
    </el-card>

    <!-- 空态（异常保护，防白屏） -->
    <el-empty v-if="!initLoading && interview && !currentQ && !feedbackView" description="暂无题目，请刷新或返回工作台">
      <el-button type="primary" style="background: #1a237e" @click="router.push('/home')">返回工作台</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.timer-tag { font-size: 14px; letter-spacing: 1px; }
.ref-text { margin: 4px 0; line-height: 1.7; white-space: pre-wrap; }
</style>
