<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../store';
import * as api from '../api';
import { fmtTime, modeText, modeTagType, ratingText, ratingTagType, statusText, statusTagType } from '../utils';

const auth = useAuthStore();
const router = useRouter();

const recent = ref<api.HistoryItem[]>([]);
const categories = ref<api.BankCategoryCount[]>([]);
const hotQuestions = ref<api.BankQuestionView[]>([]);
const loading = ref(false);
const bankLoading = ref(false);

async function loadRecent() {
  loading.value = true;
  try {
    const res = await api.listHistory(1, 6);
    recent.value = res.list;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

async function loadBankSummary() {
  bankLoading.value = true;
  try {
    const [cats, hot] = await Promise.all([
      api.listBankCategories(),
      api.listBankQuestions({ page: 0, size: 5 }),
    ]);
    categories.value = cats;
    hotQuestions.value = hot.items;
  } catch {
    // 题库概要加载失败不阻塞首页主体
  } finally {
    bankLoading.value = false;
  }
}

/** 根据状态决定卡片点击去向；返回 null 表示不可进入。 */
function targetOf(row: api.HistoryItem): string | null {
  if (row.status === 'completed' || row.hasReport) return `/interviews/${row.id}/report`;
  if (row.status === 'in_progress' || row.status === 'matched' || row.status === 'ready') {
    return `/interviews/${row.id}/engine`;
  }
  if (row.status === 'abandoned') return null;
  return `/interviews/${row.id}/setup`;
}

function actionText(row: api.HistoryItem): string {
  if (targetOf(row) === null) return '已放弃';
  const t = targetOf(row) as string;
  if (t.includes('/report')) return '查看报告';
  if (t.includes('/engine')) return row.status === 'in_progress' ? '继续面试' : '开始面试';
  return '继续完善';
}

function openRow(row: api.HistoryItem) {
  const t = targetOf(row);
  if (!t) {
    ElMessage.info('该场面试已放弃，暂无报告可查看');
    return;
  }
  router.push(t);
}

/** 首页功能入口卡 */
const entries = [
  { key: 'weak', title: '薄弱技能库', desc: '识别短板，定向补强', icon: '⚡', path: '/weak?tab=skills' },
  { key: 'wrong', title: '错题本', desc: '回顾低分作答与参考答案', icon: '📖', path: '/weak?tab=wrong' },
  { key: 'compare', title: '简历对比', desc: '两份简历 AI 横向对比', icon: '⇄', path: '/compare' },
  { key: 'feedback', title: '用户反馈', desc: '告诉我们你的建议与问题', icon: '✉', path: '/feedback' },
  { key: 'center', title: '个人中心', desc: '资料、设置与学习统计', icon: '👤', path: '/center' },
];

function goCategory(cat: string) {
  router.push({ path: '/bank', query: { category: cat } });
}

onMounted(() => {
  loadRecent();
  loadBankSummary();
});
</script>

<template>
  <div class="page">
    <!-- 欢迎 + 新建入口 -->
    <el-card shadow="never" class="welcome-card">
      <div class="welcome">
        <div>
          <h2 style="margin: 0 0 6px">你好，{{ auth.user?.nickname || auth.user?.email }}</h2>
          <p class="muted" style="margin: 0">
            上传简历与岗位 JD，AI 生成岗位匹配报告与模拟面试；也可进入题库直接开始针对性练习。
          </p>
        </div>
        <div style="display: flex; gap: 10px">
          <el-button size="large" @click="router.push('/bank')">去题库练习</el-button>
          <el-button type="primary" size="large" class="cta-btn" @click="router.push('/interviews/new')">
            开始新建面试
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 功能入口 -->
    <el-row :gutter="12" class="mt">
      <el-col v-for="e in entries" :key="e.key" :xs="12" :sm="8" :md="8" :lg="6">
        <el-card shadow="hover" class="entry-card" @click="router.push(e.path)">
          <div class="entry-icon">{{ e.icon }}</div>
          <div class="entry-title">{{ e.title }}</div>
          <div class="muted">{{ e.desc }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 题库区 -->
    <el-card shadow="never" class="mt">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>题库</span>
          <el-link type="primary" :underline="false" @click="router.push('/bank')">进入题库 →</el-link>
        </div>
      </template>
      <div v-loading="bankLoading" style="min-height: 60px">
        <div v-if="categories.length" class="cat-row">
          <div
            v-for="c in categories"
            :key="c.category"
            class="cat-card"
            @click="goCategory(c.category)"
          >
            <div class="cat-name">{{ c.category }}</div>
            <div class="muted">{{ c.count }} 题 · 去练习</div>
          </div>
        </div>
        <el-empty v-else description="题库暂无数据，可稍后刷新或联系管理员扩充" :image-size="60" />
      </div>
    </el-card>

    <!-- 最近面试 -->
    <el-card shadow="never" class="mt">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>最近面试</span>
          <el-link type="primary" :underline="false" @click="router.push('/history')">
            查看全部历史 →
          </el-link>
        </div>
      </template>

      <div v-loading="loading" class="recent-list">
        <template v-if="recent.length">
          <el-card
            v-for="row in recent"
            :key="row.id"
            shadow="hover"
            class="recent-card"
            :class="{ clickable: !!targetOf(row) }"
            @click="openRow(row)"
          >
            <div style="display: flex; justify-content: space-between; align-items: flex-start">
              <el-tag :type="modeTagType(row.mode)" size="small">{{ modeText(row.mode) }}</el-tag>
              <span>
                <el-tag v-if="row.overallRating" :type="ratingTagType(row.overallRating)" size="small">
                  {{ row.overallRating }} {{ ratingText(row.overallRating) }}
                </el-tag>
                <el-tag v-else type="info" size="small">未评级</el-tag>
                <el-tag :type="statusTagType(row.status)" size="small" effect="plain" style="margin-left: 4px">
                  {{ statusText(row.status) }}
                </el-tag>
              </span>
            </div>
            <h3 style="margin: 10px 0 4px">{{ row.title }}</h3>
            <p class="muted" style="margin: 0 0 8px">
              目标岗位：{{ row.targetPosition || '-' }}
            </p>
            <div class="muted" style="display: flex; justify-content: space-between; align-items: center">
              <span>
                进度 {{ row.completedQuestionCount }}/{{ row.questionCount }}
                <span v-if="row.matchScore != null"> · 匹配 {{ Math.round(row.matchScore as number) }} 分</span>
              </span>
              <span style="color: #1a237e">{{ fmtTime(row.createdAt) }}</span>
            </div>
            <div style="text-align: right; margin-top: 10px">
              <el-tag class="action" size="small" effect="dark">{{ actionText(row) }}</el-tag>
            </div>
          </el-card>
        </template>
        <el-empty v-else-if="!loading" description="还没有面试记录，点击上方按钮开启第一场模拟面试" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.welcome-card { background: linear-gradient(120deg, #e8eaf6 0%, #ffffff 60%); border: none; }
.welcome {
  display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap;
}
.cta-btn { background: #1a237e; border-color: #1a237e; padding: 12px 26px; font-size: 15px; }
.entry-card { cursor: pointer; margin-bottom: 12px; text-align: center; }
.entry-icon { font-size: 24px; }
.entry-title { font-weight: 600; margin: 4px 0 2px; color: #1a237e; }
.cat-row { display: flex; gap: 12px; flex-wrap: wrap; }
.cat-card {
  border: 1px solid #dcdfe6; border-radius: 8px; padding: 10px 18px;
  cursor: pointer; min-width: 120px; text-align: center;
}
.cat-card:hover { border-color: #1a237e; background: #eef0fb; }
.cat-name { font-weight: 600; margin-bottom: 4px; color: #1a237e; }
.recent-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; min-height: 120px; }
.recent-card { cursor: default; }
.recent-card.clickable { cursor: pointer; }
.recent-card.clickable:hover { border-color: #1a237e; }
.action { background: #1a237e; }
</style>
