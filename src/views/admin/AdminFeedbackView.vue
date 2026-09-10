<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as api from '../../api';
import { feedbackCategoryText, feedbackStatusText, fmtTime } from '../../utils';

const rows = ref<api.FeedbackView[]>([]);
const total = ref(0);
const loading = ref(false);
const query = reactive({ status: '', page: 0 });
const size = 10;

const STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '待处理', value: 'new' },
  { label: '处理中', value: 'processing' },
  { label: '已处理', value: 'done' },
];

const reply = reactive({ visible: false, submitting: false, id: 0, status: 'done', text: '' });

function statusTag(s?: string): string {
  if (s === 'done') return 'success';
  if (s === 'processing') return 'warning';
  return 'info';
}

async function load() {
  loading.value = true;
  try {
    const res = await api.adminFeedbackPage({
      status: query.status || undefined,
      page: query.page,
      size,
    });
    rows.value = res.items;
    total.value = res.total;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  query.page = 0;
  load();
}

function onPageChange(p: number) {
  query.page = p - 1;
  load();
}

function openReply(row: api.FeedbackView) {
  reply.id = row.id;
  reply.status = row.status === 'new' ? 'processing' : row.status;
  reply.text = row.reply || '';
  reply.visible = true;
}

function quickDone(row: api.FeedbackView) {
  reply.id = row.id;
  reply.status = 'done';
  reply.text = row.reply || '';
  reply.visible = true;
}

async function saveReply() {
  if (!reply.text.trim()) {
    ElMessage.warning('请填写回复内容（或仅切换状态）');
    return;
  }
  reply.submitting = true;
  try {
    await api.adminFeedbackReply(reply.id, { status: reply.status, reply: reply.text.trim() });
    ElMessage.success('已保存处理结果');
    reply.visible = false;
    load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    reply.submitting = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <span>用户反馈处理</span>
      </template>

      <div style="display: flex; gap: 10px; margin-bottom: 14px; align-items: center">
        <el-select v-model="query.status" style="width: 160px" @change="onSearch">
          <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <span class="muted">共 {{ total }} 条</span>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe empty-text="暂无反馈" size="default">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="用户" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.userEmail || `#${row.userId}` }}</template>
        </el-table-column>
        <el-table-column label="分类" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ feedbackCategoryText(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status) as any">{{ feedbackStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openReply(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: flex-end; margin-top: 14px">
        <el-pagination
          v-if="total > size"
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="query.page + 1"
          background
          @current-change="onPageChange"
        />
      </div>
    </el-card>

    <!-- 处理对话框 -->
    <el-dialog v-model="reply.visible" :title="`处理反馈 #${reply.id}`" width="560px">
      <p style="margin: 0 0 8px" class="muted">处理中请标注「处理中」；完成后置为「已处理」并填写回复。</p>
      <el-form label-width="80px">
        <el-form-item label="状态">
          <el-select v-model="reply.status" style="width: 180px">
            <el-option label="处理中" value="processing" />
            <el-option label="已处理" value="done" />
          </el-select>
        </el-form-item>
        <el-form-item label="回复">
          <el-input v-model="reply.text" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="回复内容（用户会在「我的反馈」中看到）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reply.visible = false">取消</el-button>
        <el-button type="primary" style="background: #1a237e" :loading="reply.submitting" @click="saveReply">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
