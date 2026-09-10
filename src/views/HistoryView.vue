<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import * as api from '../api';
import { fmtTime, modeText, modeTagType, ratingTagType, ratingText, statusText, statusTagType } from '../utils';

const router = useRouter();

const rows = ref<api.HistoryItem[]>([]);
const total = ref(0);
const loading = ref(false);
const page = reactive({ current: 1, size: 10 });

/** 根据状态决定操作目标；null = 不可操作。 */
function targetOf(row: api.HistoryItem): string | null {
  if (row.hasReport || row.status === 'completed') return `/interviews/${row.id}/report`;
  if (row.status === 'in_progress' || row.status === 'matched' || row.status === 'ready') {
    return `/interviews/${row.id}/engine`;
  }
  if (row.status === 'abandoned') return null;
  return `/interviews/${row.id}/setup`;
}

function actionText(row: api.HistoryItem): string {
  const t = targetOf(row);
  if (!t) return '已放弃';
  if (t.includes('/report')) return '查看报告';
  if (t.includes('/engine')) return row.status === 'in_progress' ? '继续答题' : '开始答题';
  return '继续完善';
}

async function load() {
  loading.value = true;
  try {
    const res = await api.listHistory(page.current, page.size);
    rows.value = res.list;
    total.value = res.total;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

function onPageChange(p: number) {
  page.current = p;
  load();
}

function openRow(row: api.HistoryItem) {
  const t = targetOf(row);
  if (!t) {
    ElMessage.info('该场面试已放弃，暂无内容可查看');
    return;
  }
  router.push(t);
}

async function delRow(row: api.HistoryItem) {
  try {
    await ElMessageBox.confirm('删除后将无法恢复，确定删除该场面试记录？', '提示', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
    });
  } catch { return; }
  try {
    await api.deleteHistory(row.id);
    ElMessage.success('已删除');
    if (rows.value.length === 1 && page.current > 1) page.current -= 1;
    load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

async function clearAll() {
  try {
    await ElMessageBox.confirm('将删除全部历史记录，且不可恢复。确定清空？', '提示', {
      type: 'warning', confirmButtonText: '全部删除', cancelButtonText: '取消',
    });
  } catch { return; }
  try {
    await api.clearHistory();
    ElMessage.success('已清空');
    page.current = 1;
    load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>面试历史记录</span>
          <div>
            <el-button text type="primary" @click="router.push('/interviews/new')">＋ 新建面试</el-button>
            <el-button v-if="total > 0" text type="danger" @click="clearAll">清空全部</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border stripe empty-text="暂无历史记录">
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="targetPosition" label="目标岗位" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.targetPosition || '-' }}</template>
        </el-table-column>
        <el-table-column label="模式" width="76" align="center">
          <template #default="{ row }">
            <el-tag :type="modeTagType(row.mode)" size="small">{{ modeText(row.mode) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="92">
          <template #default="{ row }">{{ row.completedQuestionCount }}/{{ row.questionCount }}</template>
        </el-table-column>
        <el-table-column label="匹配分" width="88">
          <template #default="{ row }">{{ row.matchScore != null ? Math.round(row.matchScore) : '-' }}</template>
        </el-table-column>
        <el-table-column label="评级" width="104">
          <template #default="{ row }">
            <el-tag v-if="row.overallRating" :type="ratingTagType(row.overallRating)" size="small">
              {{ row.overallRating }} {{ ratingText(row.overallRating) }}
            </el-tag>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210">
          <template #default="{ row }">
            <el-button
              v-if="targetOf(row)"
              size="small"
              :type="row.status === 'in_progress' ? 'success' : row.status === 'completed' ? 'primary' : 'primary'"
              text
              @click="openRow(row)"
            >
              {{ actionText(row) }}
            </el-button>
            <span v-else class="muted" style="font-size: 12px">不可继续</span>
            <el-button size="small" type="danger" text @click="delRow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 14px">
        <span class="muted">共 {{ total }} 条</span>
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="page.size"
          :current-page="page.current"
          background
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>
