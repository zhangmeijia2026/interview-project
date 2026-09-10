<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import * as api from '../api';
import { fmtTime } from '../utils';

const resumes = ref<api.ResumeRecord[]>([]);
const resLoading = ref(false);
const submitting = ref(false);

const resumeAId = ref<number | null>(null);
const resumeBId = ref<number | null>(null);
const targetPosition = ref('');

const result = ref<api.CompareView | null>(null);
const history = ref<api.CompareListItem[]>([]);

function resumeTitle(r: api.ResumeRecord): string {
  return r.parsedData?.name || r.fileName || `简历 #${r.id}`;
}

async function loadResumes() {
  resLoading.value = true;
  try {
    resumes.value = await api.listResumes();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    resLoading.value = false;
  }
}

async function loadHistory() {
  try {
    history.value = await api.listCompareHistory();
  } catch { /* 历史加载失败不阻塞 */ }
}

async function onCompare() {
  if (resumeAId.value == null || resumeBId.value == null) {
    ElMessage.warning('请选择两份要对比的简历');
    return;
  }
  if (resumeAId.value === resumeBId.value) {
    ElMessage.warning('请选择两份不同的简历');
    return;
  }
  submitting.value = true;
  try {
    const view = await api.runCompare({
      resumeAId: resumeAId.value,
      resumeBId: resumeBId.value,
      targetPosition: targetPosition.value.trim() || undefined,
    });
    result.value = view;
    ElMessage.success('AI 对比已完成');
    loadHistory();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    submitting.value = false;
  }
}

async function viewHistory(item: api.CompareListItem) {
  try {
    const detail = await api.getCompareDetail(item.id);
    result.value = detail;
    // 若该条使用的简历仍可获取，则把下拉对应
    resumeAId.value = detail.resumeAId;
    resumeBId.value = detail.resumeBId;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

async function deleteHistory(item: api.CompareListItem) {
  try {
    await ElMessageBox.confirm('删除该条对比记录？', '提示', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
    });
  } catch { return; }
  try {
    await api.deleteCompare(item.id);
    ElMessage.success('已删除');
    loadHistory();
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

onMounted(() => {
  loadResumes();
  loadHistory();
});
</script>

<template>
  <div class="page">
    <!-- 选择与提交 -->
    <el-card shadow="never">
      <template #header><b>简历对比</b></template>
      <div v-loading="resLoading" class="pick-row">
        <div>
          <span class="muted">简历 A：</span>
          <el-select v-model="resumeAId" placeholder="选择简历 A" style="width: 240px">
            <el-option v-for="r in resumes" :key="r.id" :label="resumeTitle(r)" :value="r.id" />
          </el-select>
        </div>
        <div>
          <span class="muted">简历 B：</span>
          <el-select v-model="resumeBId" placeholder="选择简历 B" style="width: 240px">
            <el-option v-for="r in resumes" :key="r.id" :label="resumeTitle(r)" :value="r.id" />
          </el-select>
        </div>
        <el-input v-model="targetPosition" placeholder="目标岗位（可选，用于结论参考）" style="width: 260px" clearable />
        <el-button type="primary" style="background: #1a237e" :loading="submitting" @click="onCompare">
          开始对比
        </el-button>
      </div>
      <p class="muted" style="margin: 8px 0 0">选择两份已解析完成的简历，AI 会输出字段对照、技能交集/差异与综合建议。</p>
    </el-card>

    <!-- 对比结果 -->
    <el-card v-if="result && result.result" shadow="never" class="mt">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <b>对比结果 · {{ result.title || `#${result.id}` }}</b>
          <span class="muted">{{ fmtTime(result.createdAt) }}</span>
        </div>
      </template>

      <div v-if="result.result.resumeASummary || result.result.resumeBSummary" style="display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 12px">
        <el-alert type="success" :closable="false" class="resume-summary">
          <template #title><b>简历 A 摘要</b></template>
          {{ result.result.resumeASummary || '（无）' }}
        </el-alert>
        <el-alert type="primary" :closable="false" class="resume-summary">
          <template #title><b>简历 B 摘要</b></template>
          {{ result.result.resumeBSummary || '（无）' }}
        </el-alert>
      </div>

      <!-- 字段对照表 -->
      <el-table v-if="result.result.fields.length" :data="result.result.fields" border size="small" style="margin-bottom: 14px">
        <el-table-column prop="field" label="字段" width="130" />
        <el-table-column prop="aValue" label="简历 A" min-width="160" show-overflow-tooltip />
        <el-table-column prop="bValue" label="简历 B" min-width="160" show-overflow-tooltip />
        <el-table-column prop="note" label="对比说明" min-width="180" show-overflow-tooltip />
      </el-table>

      <el-row :gutter="12">
        <el-col :xs="24" :md="8">
          <div class="grp">
            <b>技能交集</b>
            <div><el-tag v-for="(s, i) in result.result.skillOverlap" :key="i" size="small" type="success" style="margin: 3px 6px 3px 0">{{ s }}</el-tag></div>
            <p v-if="!result.result.skillOverlap.length" class="muted">无</p>
          </div>
        </el-col>
        <el-col :xs="24" :md="8">
          <div class="grp">
            <b>仅 A 拥有</b>
            <div><el-tag v-for="(s, i) in result.result.onlyA" :key="i" size="small" type="primary" style="margin: 3px 6px 3px 0">{{ s }}</el-tag></div>
            <p v-if="!result.result.onlyA.length" class="muted">无</p>
          </div>
        </el-col>
        <el-col :xs="24" :md="8">
          <div class="grp">
            <b>仅 B 拥有</b>
            <div><el-tag v-for="(s, i) in result.result.onlyB" :key="i" size="small" type="warning" style="margin: 3px 6px 3px 0">{{ s }}</el-tag></div>
            <p v-if="!result.result.onlyB.length" class="muted">无</p>
          </div>
        </el-col>
      </el-row>

      <el-divider content-position="left">双方优势</el-divider>
      <div class="advice-cols">
        <div class="grp">
          <b>简历 A 优势</b>
          <ul><li v-for="(s, i) in result.result.strengthsA" :key="i">{{ s }}</li></ul>
          <p v-if="!result.result.strengthsA.length" class="muted">无</p>
        </div>
        <div class="grp">
          <b>简历 B 优势</b>
          <ul><li v-for="(s, i) in result.result.strengthsB" :key="i">{{ s }}</li></ul>
          <p v-if="!result.result.strengthsB.length" class="muted">无</p>
        </div>
      </div>

      <el-divider content-position="left">综合结论</el-divider>
      <el-alert v-if="result.result.differenceSummary" type="info" :closable="false" :title="result.result.differenceSummary" style="margin-bottom: 10px" />
      <ul>
        <li v-for="(a, i) in result.result.advice" :key="i" style="margin: 4px 0">{{ a }}</li>
      </ul>
    </el-card>

    <!-- 对比历史 -->
    <el-card shadow="never" class="mt">
      <template #header><b>对比历史</b></template>
      <div v-if="history.length">
        <div v-for="item in history" :key="item.id" class="hist-row">
          <div style="flex: 1; overflow: hidden">
            <b>{{ item.title || `对比 #${item.id}` }}</b>
            <div class="muted" style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
              {{ item.differenceSummary || '（无摘要）' }} · {{ fmtTime(item.createdAt) }}
            </div>
          </div>
          <el-button size="small" text type="primary" @click="viewHistory(item)">查看</el-button>
          <el-button size="small" text type="danger" @click="deleteHistory(item)">删除</el-button>
        </div>
      </div>
      <el-empty v-else description="暂无对比记录" />
    </el-card>
  </div>
</template>

<style scoped>
.pick-row { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
.resume-summary { flex: 1; min-width: 260px; white-space: pre-wrap; }
.grp { border: 1px solid #ebeef5; border-radius: 8px; padding: 10px 12px; margin-bottom: 10px; height: 100%; }
.advice-cols { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 12px; }
.hist-row {
  display: flex; align-items: center; gap: 8px; border: 1px solid #ebeef5;
  border-radius: 8px; padding: 8px 12px; margin-bottom: 8px;
}
</style>
