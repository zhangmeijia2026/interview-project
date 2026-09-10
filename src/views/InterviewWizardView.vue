<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import * as api from '../api';
import { fmtTime, statusText, statusTagType } from '../utils';
import MatchSummary from '../components/MatchSummary.vue';

const props = defineProps<{ id?: string | number }>();
const router = useRouter();
const route = useRoute();

// 新建 / 续办共用：id 存在 = 从历史继续完善（/interviews/:id/setup）
const editId = computed<number | null>(() => {
  const raw = props.id;
  if (raw === undefined || raw === null || raw === '') return null;
  const n = Number(raw);
  return Number.isFinite(n) ? n : null;
});

const interview = ref<api.Interview | null>(null);
const resume = ref<api.ResumeRecord | null>(null);
const jd = ref<api.JdRecord | null>(null);
const match = ref<api.MatchResult | null>(null);
const busy = ref(false);
const loading = ref(false);

const createForm = reactive({
  mode: 'formal' as 'formal' | 'practice',
  title: '',
  targetPosition: '',
  questionCount: 5,
  weakBoost: false,
  practiceCategory: '',
});

const bankCats = ref<api.BankCategoryCount[]>([]);
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

const isPractice = computed(() => interview.value?.mode === 'practice');

/** 正式模式：后端 interview.status 驱动的向导进度（0 创建 / 1 简历 / 2 JD / 3 匹配 / 4 开始答题） */
const activeStep = computed<number>(() => {
  if (!interview.value || isPractice.value) return 4;
  const s = interview.value.status;
  if (s === 'draft') return 1;
  if (s === 'resume_uploaded') return 2;
  if (s === 'jd_uploaded') return 3;
  return 4; // matched / ready / in_progress / completed / abandoned
});

const stepTitles = ['基本信息', '上传简历', '岗位 JD', '匹配分析', '开始答题'];

async function loadBankCats() {
  try {
    bankCats.value = await api.listBankCategories();
  } catch { /* 题库加载失败不阻塞创建 */ }
}

// ---------- 第一步：创建会话 ----------
async function onCreate() {
  if (createForm.mode === 'formal') {
    if (!createForm.title.trim() || !createForm.targetPosition.trim()) {
      ElMessage.warning('请填写面试标题与目标岗位');
      return;
    }
  } else {
    // 练习模式自动生成标题/目标（后端二者均必填）
    if (!createForm.practiceCategory) createForm.practiceCategory = '';
  }
  busy.value = true;
  const practice = createForm.mode === 'practice';
  const category = createForm.practiceCategory || '';
  try {
    const it = await api.createInterview({
      title: practice
        ? (createForm.title.trim() || (category ? `专项练习 · ${category}` : '综合练习'))
        : createForm.title.trim(),
      targetPosition: practice
        ? (createForm.targetPosition.trim() || (category ? `${category} 技能练习` : '综合技能练习'))
        : createForm.targetPosition.trim(),
      questionCount: createForm.questionCount,
      mode: createForm.mode,
      weakBoost: createForm.weakBoost,
      practiceCategory: practice && category ? category : undefined,
    });
    interview.value = it;
    if (practice) {
      ElMessage.success(`已创建练习会话 #${it.id}，点击下方按钮开始练习`);
    } else {
      ElMessage.success(`已创建会话 #${it.id}，请上传简历`);
    }
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}

// ---------- 第二步：简历（上传新简历 / 从简历库复用，可查看详情与原文） ----------
const resumeMode = ref<'upload' | 'library'>('upload');
const resumeLib = ref<api.ResumeRecord[]>([]);
const selectedResumeId = ref<number | null>(null);
const resumeInput = ref<HTMLInputElement | null>(null);
const libraryLoading = ref(false);
const resumeDetailVisible = ref(false);
const resumeDetail = ref<api.ResumeRecord | null>(null);

function pickResumeFile() { resumeInput.value?.click(); }

async function onResumeFileChosen(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || !interview.value) return;
  busy.value = true;
  try {
    await api.uploadResume(interview.value.id, file);
    ElMessage.info('简历已上传，解析中…');
    const ready = await waitResumeReady();
    await api.confirmResume(interview.value.id);
    resume.value = ready;
    await refreshInterview();
    ElMessage.success('简历解析完成并已定稿');
  } catch (err) {
    ElMessage.error((err as Error).message);
  } finally {
    busy.value = false;
  }
}

async function waitResumeReady(): Promise<api.ResumeRecord> {
  if (!interview.value) throw new Error('会话不存在');
  for (let i = 0; i < 30; i++) {
    const r = await api.getResume(interview.value.id);
    if (r.status === 'ready') return r;
    if (r.status === 'failed') throw new Error('简历解析失败，请换一份 PDF/DOCX 重试');
    await sleep(1000);
  }
  throw new Error('简历解析超时，请稍后在「历史记录」中继续');
}

async function switchResumeMode(mode: 'upload' | 'library') {
  resumeMode.value = mode;
  if (mode === 'library' && !resumeLib.value.length) await loadResumeLib();
}

function onResumeModeChange(mode: string | number | boolean) {
  switchResumeMode(mode as 'upload' | 'library');
}

async function loadResumeLib() {
  libraryLoading.value = true;
  try {
    resumeLib.value = await api.listResumes();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    libraryLoading.value = false;
  }
}

function resumeTitle(r: api.ResumeRecord): string {
  const name = r.parsedData?.name;
  return (name && name.trim()) || r.fileName || `简历 #${r.id}`;
}

async function onReuseResume() {
  if (!interview.value || selectedResumeId.value == null) return;
  busy.value = true;
  try {
    resume.value = await api.reuseResume(interview.value.id, selectedResumeId.value);
    selectedResumeId.value = null;
    await refreshInterview();
    ElMessage.success('已复用历史简历');
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}

async function openResumeDetail(r: api.ResumeRecord) {
  resumeDetailVisible.value = true;
  resumeDetail.value = r;
}

async function openResumeFile(r: api.ResumeRecord) {
  try {
    const blob = await api.getResumeFileBlob(r.id);
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

// ---------- 第三步：JD（粘贴文字 / 上传文件 / 历史 JD 三 Tab） ----------
const jdMode = ref<'text' | 'upload' | 'history'>('text');
const jdText = ref('');
const jdInput = ref<HTMLInputElement | null>(null);
const pickedJdName = ref('');
const jdLib = ref<api.JdLibraryItem[]>([]);
const jdLibLoading = ref(false);
const selectedJdId = ref<number | null>(null);
const jdDetailVisible = ref(false);
const jdDetailView = ref<api.JdDetailView | null>(null);
const jdImageUrl = ref('');
const jdImageName = ref('');

function pickJdFile() { jdInput.value?.click(); }

async function onJdFileChosen(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || !interview.value) return;
  busy.value = true;
  pickedJdName.value = file.name;
  try {
    jd.value = await api.uploadJdFile(interview.value.id, file);
    ElMessage.info('JD 上传成功，识别中…');
    await pollJdReady();
  } catch (err) {
    ElMessage.error((err as Error).message);
  } finally {
    busy.value = false;
  }
}

async function onJdTextSubmit() {
  if (!jdText.value.trim() || !interview.value) return;
  busy.value = true;
  try {
    jd.value = await api.submitJdText(interview.value.id, jdText.value);
    ElMessage.info('JD 解析中…');
    await pollJdReady();
  } catch (err) {
    ElMessage.error((err as Error).message);
  } finally {
    busy.value = false;
  }
}

async function pollJdReady() {
  if (!interview.value) return;
  for (let i = 0; i < 30; i++) {
    const j = await api.getJd(interview.value.id);
    if (j.status === 'ready') {
      jd.value = j;
      await refreshInterview();
      ElMessage.success('JD 解析完成');
      return;
    }
    if (j.status === 'failed') {
      // 识别失败 → 引导改用粘贴文字（后端 interview.status 仍停在 resume_uploaded）
      jd.value = j;
      jdMode.value = 'text';
      ElMessage.error('JD 识别失败，请改用粘贴文字');
      return;
    }
    await sleep(1000);
  }
  throw new Error('JD 解析超时');
}

async function switchJdMode(mode: string | number | boolean) {
  const m = mode as 'text' | 'upload' | 'history';
  jdMode.value = m;
  if (m === 'history' && !jdLib.value.length) await loadJdLib();
}

function onJdModeChange(mode: string | number | boolean) {
  switchJdMode(mode);
}

async function loadJdLib() {
  jdLibLoading.value = true;
  try {
    jdLib.value = await api.listJdLibrary();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    jdLibLoading.value = false;
  }
}

async function onReuseJd() {
  if (!interview.value || selectedJdId.value == null) return;
  busy.value = true;
  try {
    jd.value = await api.reuseJd(interview.value.id, selectedJdId.value);
    selectedJdId.value = null;
    await refreshInterview();
    ElMessage.success('已复用历史 JD');
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}

function isImageFile(name?: string | null): boolean {
  if (!name) return false;
  return /\.(jpg|jpeg|png|bmp|webp)$/i.test(name);
}

async function showJdDetail(item: api.JdLibraryItem) {
  jdDetailVisible.value = true;
  jdDetailView.value = null;
  jdImageUrl.value = '';
  jdImageName.value = '';
  try {
    const detail = await api.getJdDetail(item.id);
    jdDetailView.value = detail;
    if (item.type === 'file' && isImageFile(item.fileName)) {
      const blob = await api.getJdFileBlob(item.id);
      jdImageUrl.value = URL.createObjectURL(blob);
      jdImageName.value = item.fileName || '';
    }
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

async function openJdRawFile(item: api.JdLibraryItem) {
  if (item.type === 'text') { showJdDetail(item); return; }
  try {
    const blob = await api.getJdFileBlob(item.id);
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

function jdStatusOk(item: api.JdLibraryItem): boolean {
  return item.status === 'ready';
}

// ---------- 第四步：匹配 ----------
async function onMatch() {
  if (!interview.value) return;
  busy.value = true;
  try {
    match.value = await api.runMatch(interview.value.id);
    await refreshInterview();
    ElMessage.success(`匹配完成：综合 ${match.value.overall} 分`);
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}

async function refreshInterview() {
  if (!interview.value) return;
  interview.value = await api.getInterview(interview.value.id);
}

// ---------- 通用 ----------
function goBack() {
  router.push(editId.value ? '/history' : '/home');
}

function goEngine() {
  if (!interview.value) return;
  router.push(`/interviews/${interview.value.id}/engine`);
}

function goReport() {
  if (!interview.value) return;
  router.push(`/interviews/${interview.value.id}/report`);
}

onMounted(async () => {
  // 外部入口（如薄弱技能「去加强」）可通过 query 预选模式 / 针对薄弱点 / 分类
  const q = route.query;
  if (!editId.value) {
    if (q.mode === 'practice' || q.mode === 'formal') createForm.mode = q.mode;
    if (q.weakBoost === '1' || q.weakBoost === 'true') createForm.weakBoost = true;
    if (q.category) createForm.practiceCategory = String(q.category);
  }
  await loadBankCats();
  if (!editId.value) return;
  loading.value = true;
  try {
    interview.value = await api.getInterview(editId.value);
    if (interview.value.mode !== 'practice') {
      if (interview.value.resumeId) {
        try { resume.value = await api.getResume(editId.value); } catch { /* 素材缺失可忽略 */ }
      }
      if (interview.value.jdId) {
        try { jd.value = await api.getJd(editId.value); } catch { /* 素材缺失可忽略 */ }
      }
      if (['matched', 'ready', 'in_progress'].includes(interview.value.status)) {
        try { match.value = await api.getMatch(editId.value); } catch { /* 未匹配可忽略 */ }
      }
    }
  } catch (e) {
    ElMessage.error((e as Error).message);
    router.replace('/home');
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div v-loading="loading" class="page">
    <!-- 创建会话（第 0 步：模式 + 基本信息） -->
    <el-card v-if="!interview" shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>开始一场新的模拟面试</span>
          <el-button text type="primary" @click="goBack">← 返回工作台</el-button>
        </div>
      </template>

      <el-form label-width="110px" style="max-width: 620px" @submit.prevent="onCreate">
        <el-form-item label="面试模式">
          <el-radio-group v-model="createForm.mode">
            <el-radio-button value="formal">正式面试</el-radio-button>
            <el-radio-button value="practice">练习模式</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-alert
          v-if="createForm.mode === 'formal'"
          type="info" :closable="false" class="mode-tip"
          title="正式：简历→JD→匹配后开面，动态出题 + 每题计时，完成后出评级报告。"
        />
        <el-alert
          v-else
          type="warning" :closable="false" class="mode-tip"
          title="练习：从题库直接抽题，无需简历/JD；可随时查看提示与参考答案，完成出复盘（无评级）。"
        />

        <el-form-item v-if="createForm.mode === 'practice'" label="练习分类">
          <el-select
            v-model="createForm.practiceCategory"
            placeholder="全部（跨分类随机抽题）"
            clearable style="width: 100%"
          >
            <el-option v-for="c in bankCats" :key="c.category" :label="`${c.category}（${c.count} 题）`" :value="c.category" />
          </el-select>
        </el-form-item>

        <el-form-item label="面试标题">
          <el-input
            v-model="createForm.title"
            :placeholder="createForm.mode === 'practice' ? '可留空自动命名' : '例如：Java 后端模拟面试'"
          />
        </el-form-item>
        <el-form-item v-if="createForm.mode === 'formal'" label="目标岗位">
          <el-input v-model="createForm.targetPosition" placeholder="例如：Java后端开发工程师" />
        </el-form-item>

        <el-form-item label="题目数量">
          <el-input-number v-model="createForm.questionCount" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="针对薄弱点">
          <el-switch v-model="createForm.weakBoost" />
          <span class="muted" style="margin-left: 8px">优先针对你的薄弱技能出题（需要已有薄弱技能记录）</span>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" style="background: #1a237e" :loading="busy" @click="onCreate">
            创建{{ createForm.mode === 'practice' ? '练习' : '会话' }}
          </el-button>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" title="历史记录中的未完成面试可在「历史记录 → 继续完善」中接着做。" />
    </el-card>

    <!-- 向导主体（已有会话） -->
    <template v-else>
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px">
        <el-text type="info" size="small">
          会话 #{{ interview.id }} · {{ interview.title }}
          <el-tag :type="statusTagType(interview.status)" size="small" style="margin-left: 6px">{{ statusText(interview.status) }}</el-tag>
        </el-text>
        <el-button text type="primary" @click="goBack">← 返回</el-button>
      </div>

      <!-- 练习模式：直接开始（无需简历/JD/匹配） -->
      <el-card v-if="isPractice" shadow="never">
        <template #header>
          <span>练习会话 #{{ interview.id }}</span>
          <el-tag type="warning" size="small" style="margin-left: 8px">练习</el-tag>
        </template>
        <el-descriptions :column="3" border size="small" style="margin-bottom: 14px">
          <el-descriptions-item label="标题">{{ interview.title }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ interview.practiceCategory || '全部' }}</el-descriptions-item>
          <el-descriptions-item label="题数">{{ interview.questionCount }}</el-descriptions-item>
          <el-descriptions-item label="针对薄弱点">{{ interview.weakBoost ? '开' : '关' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert v-if="interview.status === 'abandoned'" type="warning" :closable="false" title="该场练习已被放弃，无法继续。" style="margin-bottom: 12px" />
        <template v-else>
          <el-button
            v-if="interview.status === 'completed'"
            type="primary" size="large" style="background: #1a237e" @click="goReport"
          >
            查看复盘报告
          </el-button>
          <el-button
            v-else
            type="primary" size="large" style="background: #1a237e"
            :disabled="interview.status === 'abandoned'"
            @click="goEngine"
          >
            {{ interview.status === 'in_progress' ? '继续练习' : '开始练习' }}（{{ interview.questionCount }} 题）
          </el-button>
          <p class="muted" style="margin-top: 8px">练习模式全程可见「提示 / 参考答案」，完成全部题目后生成复盘（无评级）。</p>
        </template>
      </el-card>

      <!-- 正式模式向导步骤 -->
      <template v-else>
        <el-steps :active="activeStep" align-center finish-status="success" style="margin-bottom: 18px">
          <el-step v-for="(t, i) in stepTitles" :key="i" :title="t" />
        </el-steps>

        <el-descriptions :column="4" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="标题">{{ interview.title }}</el-descriptions-item>
          <el-descriptions-item label="岗位">{{ interview.targetPosition }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(interview.status)" size="small">{{ statusText(interview.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="进度">
            {{ interview.completedQuestionCount }} / {{ interview.questionCount }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 简历：draft -->
        <el-card v-if="activeStep === 1" shadow="never">
          <template #header>① 上传简历</template>

          <div v-if="resume && resume.status === 'ready'" class="muted" style="margin-bottom: 12px">
            当前已绑定：{{ resumeTitle(resume) }}（{{ resume.fileName }}）
          </div>

          <el-radio-group v-model="resumeMode" style="margin-bottom: 14px" @change="onResumeModeChange">
            <el-radio-button value="upload">上传新简历</el-radio-button>
            <el-radio-button value="library">从我的历史简历中选择</el-radio-button>
          </el-radio-group>

          <!-- 上传模式 -->
          <div v-if="resumeMode === 'upload'">
            <input ref="resumeInput" type="file" accept=".pdf,.docx" hidden @change="onResumeFileChosen" />
            <el-button type="primary" style="background: #1a237e" :loading="busy" @click="pickResumeFile">
              选择文件上传
            </el-button>
            <p class="muted" style="margin-top: 8px">支持 PDF / DOCX，≤10MB；上传后自动解析并定稿。</p>
          </div>

          <!-- 简历库复用 -->
          <div v-else>
            <div v-loading="libraryLoading" class="resume-lib">
              <template v-if="resumeLib.length">
                <div
                  v-for="r in resumeLib"
                  :key="r.id"
                  class="lib-item"
                  :class="{ active: selectedResumeId === r.id }"
                  @click="selectedResumeId = r.id"
                >
                  <div style="display: flex; align-items: center; justify-content: space-between; gap: 8px">
                    <span style="font-weight: 600">{{ resumeTitle(r) }}</span>
                    <span>
                      <el-tag v-if="selectedResumeId === r.id" size="small" type="success">已选</el-tag>
                      <el-button size="small" text type="primary" @click.stop="openResumeDetail(r)">详情</el-button>
                      <el-button size="small" text type="primary" @click.stop="openResumeFile(r)">原文件</el-button>
                    </span>
                  </div>
                  <div class="muted">{{ r.fileName }} · {{ fmtTime(r.createdAt) }}</div>
                </div>
              </template>
              <el-empty v-else description="暂无已解析的历史简历，可先「上传新简历」。" :image-size="70" />
            </div>
            <div style="display: flex; gap: 10px; margin-top: 12px">
              <el-button type="primary" style="background: #1a237e" :disabled="selectedResumeId == null" :loading="busy" @click="onReuseResume">
                复用这份简历
              </el-button>
              <el-button @click="resumeMode = 'upload'">去上传新简历</el-button>
            </div>
          </div>
        </el-card>

        <!-- JD：resume_uploaded（三 Tab） -->
        <el-card v-if="activeStep === 2" shadow="never">
          <template #header>② 岗位 JD</template>

          <el-tabs v-model="jdMode" @tab-change="onJdModeChange">
            <!-- 粘贴文字 -->
            <el-tab-pane label="粘贴文字" name="text">
              <el-input v-model="jdText" type="textarea" :rows="6" placeholder="粘贴目标岗位 JD 原文…" />
              <el-button class="mt" type="primary" style="background: #1a237e" :loading="busy" @click="onJdTextSubmit">
                解析 JD
              </el-button>
            </el-tab-pane>

            <!-- 上传文件（文档/图片） -->
            <el-tab-pane label="上传文件" name="upload">
              <input ref="jdInput" type="file" accept=".pdf,.docx,.jpg,.jpeg,.png,.bmp,.webp" hidden @change="onJdFileChosen" />
              <el-button type="primary" style="background: #1a237e" :loading="busy" @click="pickJdFile">
                选择文件上传
              </el-button>
              <span v-if="pickedJdName" class="muted" style="margin-left: 10px">{{ pickedJdName }}</span>
              <p class="muted" style="margin-top: 8px">支持 PDF / DOCX / 图片（jpg、jpeg、png、bmp、webp），≤10MB；图片自动 OCR 识别。</p>
              <p v-if="jd && jd.status === 'failed'" class="muted" style="color: #f56c6c">
                上次文件识别失败，可切换「粘贴文字」重试。
              </p>
            </el-tab-pane>

            <!-- 历史 JD -->
            <el-tab-pane label="历史 JD" name="history">
              <div v-loading="jdLibLoading" class="jd-lib">
                <template v-if="jdLib.length">
                  <div
                    v-for="item in jdLib"
                    :key="item.id"
                    class="lib-item"
                    :class="{ active: selectedJdId === item.id }"
                    @click="selectedJdId = item.id"
                  >
                    <div style="display: flex; align-items: center; justify-content: space-between; gap: 8px">
                      <span style="font-weight: 600; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
                        {{ item.type === 'file' ? item.fileName || `JD 文件 #${item.id}` : (item.textPreview || `JD 文本 #${item.id}`) }}
                      </span>
                      <span>
                        <el-tag v-if="selectedJdId === item.id" size="small" type="success">已选</el-tag>
                        <el-tag v-if="!jdStatusOk(item)" size="small" type="danger">未就绪</el-tag>
                        <el-button size="small" text type="primary" @click.stop="showJdDetail(item)">详情</el-button>
                        <el-button size="small" text type="primary" @click.stop="openJdRawFile(item)">
                          {{ item.type === 'file' ? '原文件/图片' : '查看文本' }}
                        </el-button>
                      </span>
                    </div>
                    <div class="muted">{{ fmtTime(item.createdAt) }}<template v-if="item.type === 'file'"> · {{ item.type }}</template></div>
                  </div>
                </template>
                <el-empty v-else description="暂无历史 JD，可先「粘贴文字」或「上传文件」。" :image-size="70" />
              </div>
              <div style="display: flex; gap: 10px; margin-top: 12px">
                <el-button type="primary" style="background: #1a237e" :disabled="selectedJdId == null" :loading="busy" @click="onReuseJd">
                  复用这份 JD
                </el-button>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>

        <!-- 匹配：jd_uploaded -->
        <el-card v-if="activeStep === 3" shadow="never">
          <template #header>③ 匹配与面试重点</template>
          <template v-if="!match">
            <p class="muted" style="margin: 0 0 10px">
              简历与 JD 均已就绪。点击下方按钮，由 AI 生成简历-JD 匹配度与逐题出题重点。
            </p>
            <el-button type="primary" style="background: #1a237e" :loading="busy" @click="onMatch">
              开始匹配
            </el-button>
          </template>
          <template v-else>
            <div class="muted" style="margin-bottom: 6px">匹配已完成，可点「重新匹配」换一组出题重点。</div>
            <MatchSummary :match="match" />
            <el-button class="mt" type="primary" style="background: #1a237e" :loading="busy" @click="onMatch">
              重新匹配
            </el-button>
          </template>
        </el-card>

        <!-- 开始答题：matched / ready / in_progress / completed -->
        <el-card v-if="activeStep === 4" shadow="never">
          <template #header>
            <span>④ 准备就绪</span>
            <el-tag :type="statusTagType(interview.status)" style="margin-left: 8px">{{ statusText(interview.status) }}</el-tag>
          </template>

          <el-alert v-if="interview.status === 'abandoned'" type="warning" :closable="false" title="该场面试已被放弃，无法继续答题。可在历史记录中删除。" style="margin-bottom: 12px" />

          <template v-else>
            <div v-if="match" style="margin-bottom: 8px">
              <MatchSummary :match="match" />
            </div>
            <div v-else class="muted" style="margin: 0 0 10px">缺少匹配结果，请先完成第 ③ 步匹配。</div>

            <div style="display: flex; gap: 12px; margin-top: 14px">
              <el-button
                v-if="interview.status === 'in_progress'"
                type="primary" size="large" style="background: #1a237e" @click="goEngine"
              >
                继续答题（断点续面）
              </el-button>
              <el-button
                v-else-if="interview.status === 'completed'"
                type="primary" size="large" style="background: #1a237e" @click="goReport"
              >
                查看综合报告
              </el-button>
              <el-button
                v-else-if="interview.status === 'matched' || interview.status === 'ready'"
                type="primary" size="large" style="background: #1a237e" @click="goEngine"
              >
                开始答题（共 {{ interview.questionCount }} 题）
              </el-button>
            </div>
            <p class="muted" style="margin-top: 8px">答题过程中每题的 AI 点评会即时反馈，全部完成后自动生成综合报告。</p>
          </template>
        </el-card>
      </template>
    </template>

    <!-- 简历详情弹窗 -->
    <el-dialog v-model="resumeDetailVisible" title="简历解析详情" width="680px">
      <div v-if="resumeDetail" class="resume-detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="姓名">{{ resumeDetail.parsedData?.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ resumeDetail.parsedData?.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="电话">{{ resumeDetail.parsedData?.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文件名">{{ resumeDetail.fileName }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="resumeDetail.parsedData">
          <div v-for="(edu, i) in resumeDetail.parsedData.education || []" :key="'edu' + i" class="sub-sec">
            <b>教育背景 {{ i + 1 }}</b>：{{ edu.school }} · {{ edu.degree }} · {{ edu.major }} · {{ edu.period }}
          </div>
          <div v-for="(ex, i) in resumeDetail.parsedData.experience || []" :key="'ex' + i" class="sub-sec">
            <b>工作经历 {{ i + 1 }}</b>：{{ ex.company }} · {{ ex.title }} · {{ ex.period }}
            <div class="muted">{{ ex.summary }}</div>
            <div v-if="ex.tech && ex.tech.length" class="muted">技术：{{ ex.tech.join('、') }}</div>
          </div>
          <div v-for="(p, i) in resumeDetail.parsedData.projects || []" :key="'p' + i" class="sub-sec">
            <b>项目 {{ i + 1 }}</b>：{{ p.name }} · {{ p.period }} · {{ p.role }}
            <div class="muted">{{ p.description }}</div>
          </div>
          <div v-if="resumeDetail.parsedData.skills && resumeDetail.parsedData.skills.length" class="sub-sec">
            <b>技能</b>：<el-tag v-for="(s, i) in resumeDetail.parsedData.skills" :key="i" size="small" style="margin-right: 6px">{{ s.name }}（{{ s.level }}）</el-tag>
          </div>
        </template>
      </div>
    </el-dialog>

    <!-- JD 详情弹窗 -->
    <el-dialog v-model="jdDetailVisible" title="历史 JD 详情" width="680px">
      <div v-if="jdDetailView">
        <el-alert type="info" :closable="false" class="mt" :title="`来源类型：${jdDetailView.type === 'file' ? '文件（' + (jdDetailView.fileName || '') + '）' : '直接粘贴文本'}`" />
        <img
          v-if="jdImageUrl"
          :src="jdImageUrl"
          :alt="jdImageName"
          style="max-width: 100%; margin-top: 12px; border: 1px solid #ebeef5; border-radius: 6px"
        />
        <p v-if="jdDetailView.rawText" style="white-space: pre-wrap; margin: 12px 0 0; line-height: 1.7">{{ jdDetailView.rawText }}</p>
        <el-empty v-else-if="!jdImageUrl" description="暂无解析文本（文本型 JD 已删除或文件型解析失败）" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.mode-tip { margin-bottom: 16px; max-width: 620px; }
.resume-lib, .jd-lib { max-height: 320px; overflow: auto; }
.lib-item {
  padding: 10px 12px; border: 1px solid #dcdfe6; border-radius: 6px; margin-bottom: 8px; cursor: pointer;
}
.lib-item.active { border-color: #1a237e; background: #eef0fb; }
.resume-detail .sub-sec { margin-top: 10px; font-size: 13px; }
</style>
