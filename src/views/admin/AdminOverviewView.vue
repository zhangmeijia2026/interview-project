<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import * as api from '../../api';

const router = useRouter();
const loading = ref(false);
const stats = ref<api.AdminOverviewStats | null>(null);

// 概览卡片：8 项汇总（getAdminOverview 与 docs/06 对齐）
const cards = [
  { key: 'userCount', label: '注册用户', color: '', path: '/admin/users' },
  { key: 'adminCount', label: '启用管理员', color: '', path: '' },
  { key: 'disabledUserCount', label: '停用账号（黑名单）', color: '#f56c6c', path: '/admin/blacklist' },
  { key: 'interviewCount', label: '面试总数', color: '', path: '' },
  { key: 'completedInterviewCount', label: '已完成面试', color: '#00c853', path: '' },
  { key: 'inProgressInterviewCount', label: '进行中面试', color: '#ff9100', path: '' },
  { key: 'pendingFeedbackCount', label: '待处理反馈', color: '', path: '/admin/feedback' },
  { key: 'questionCount', label: '启用题目', color: '', path: '/admin/bank' },
];

const quickLinks = [
  { title: '用户管理', desc: '查看用户与面试、新增管理员', path: '/admin/users' },
  { title: '黑名单管理', desc: '停用账号列表，可一键启用', path: '/admin/blacklist' },
  { title: '题库管理', desc: '浏览 / 增删改题目，AI 一键扩充题库', path: '/admin/bank' },
  { title: '反馈处理', desc: '查看用户反馈并回复处理', path: '/admin/feedback' },
  { title: '调用统计', desc: 'DeepSeek 调用量 / 费用 / 回退率图表与明细', path: '/admin/llm' },
];

function num(key: string): number {
  const s = stats.value;
  return s ? Number((s as unknown as Record<string, number>)[key] ?? 0) : 0;
}

// 卡片点击：带目标的跳转对应管理页；无目标仅作展示
function goCard(key: string) {
  const card = cards.find((c) => c.key === key);
  if (card?.path) router.push(card.path);
}

async function load() {
  loading.value = true;
  try {
    stats.value = await api.getAdminOverview();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  

    <el-card shadow="never" class="mt">
      <template #header><b>快捷入口</b></template>
      <div class="quick-grid">
        <div v-for="q in quickLinks" :key="q.path" class="quick-card" @click="router.push(q.path)">
          <div class="quick-title">{{ q.title }}</div>
          <div class="muted">{{ q.desc }}</div>
        </div>
      </div>
    </el-card>
  
</template>

<style scoped>
.stat-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 12px; }
.stat-card { border: 1px solid #ebeef5; border-radius: 8px; padding: 16px; text-align: center; cursor: pointer; }
.stat-card:hover { border-color: #1a237e; background: #f5f6ff; }
.stat-num { font-size: 26px; font-weight: 700; color: #1a237e; }
.quick-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; }
.quick-card { border: 1px solid #ebeef5; border-radius: 10px; padding: 18px; cursor: pointer; }
.quick-card:hover { border-color: #1a237e; background: #f5f6ff; }
.quick-title { font-weight: 600; margin: 0 0 6px; color: #1a237e; }
</style>
