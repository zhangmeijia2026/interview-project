<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import * as api from '../../api';
import {
  fmtTime, labelOf, modeTagType, modeText, ratingTagType, ratingText,
  statusTagType, statusText,
} from '../../utils';

// ---------- 查询条件与列表 ----------
const rows = ref<api.AdminUserView[]>([]);
const total = ref(0);
const loading = ref(false);
const size = 10;
const query = reactive({ keyword: '', role: '', active: '' as '' | 'true' | 'false', page: 0 });

const ROLE_OPTIONS = [
  { label: '全部角色', value: '' },
  { label: '普通用户', value: 'user' },
  { label: '管理员', value: 'admin' },
];
const ACTIVE_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '启用', value: 'true' },
  { label: '停用', value: 'false' },
];

function roleText(r?: string): string {
  return r === 'admin' ? '管理员' : '普通用户';
}

function activeTag(active: boolean) {
  return active
    ? { type: 'success' as const, text: '启用' }
    : { type: 'danger' as const, text: '停用' };
}

async function load() {
  loading.value = true;
  try {
    const res = await api.listAdminUsers({
      keyword: query.keyword.trim() || undefined,
      role: query.role || undefined,
      active: query.active ? query.active === 'true' : undefined,
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

// ---------- 新增管理员 ----------
const create = reactive({ visible: false, submitting: false, email: '', password: '', nickname: '' });

function openCreate() {
  create.email = '';
  create.password = '';
  create.nickname = '';
  create.visible = true;
}

async function saveCreate() {
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(create.email.trim())) {
    ElMessage.warning('请输入有效的邮箱');
    return;
  }
  if (!create.password || create.password.length < 8) {
    ElMessage.warning('密码至少 8 位');
    return;
  }
  create.submitting = true;
  try {
    await api.adminCreateAdmin({
      email: create.email.trim(),
      password: create.password,
      nickname: create.nickname.trim() || undefined,
    });
    ElMessage.success('管理员账号已创建');
    create.visible = false;
    // 若当前过滤不含管理员则切回全部，便于看到新账号
    if (query.role && query.role !== 'admin') query.role = '';
    onSearch();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    create.submitting = false;
  }
}

// ---------- 停用 / 启用 ----------
async function toggleActive(row: api.AdminUserView) {
  const disabling = row.active;
  try {
    await ElMessageBox.confirm(
      disabling ? `确认停用 ${row.email}？停用即拉黑，将立即禁止该账号登录。` : `确认启用 ${row.email}？该账号将恢复登录。`,
      '操作确认',
      { type: 'warning', confirmButtonText: disabling ? '确认停用' : '确认启用', cancelButtonText: '取消' },
    );
  } catch { return; }
  try {
    await api.adminSetActive(row.id, !disabling);
    ElMessage.success(disabling ? '已停用该账号（加入黑名单）' : '已启用该账号');
    load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

// ---------- 查看某用户面试 ----------
const drawer = reactive({ visible: false, loading: false, userId: 0, userLabel: '', interviews: [] as api.AdminInterviewView[] });

async function openInterviews(row: api.AdminUserView) {
  drawer.userId = row.id;
  drawer.userLabel = `${row.nickname || ''}（${row.email}）`;
  drawer.interviews = [];
  drawer.visible = true;
  drawer.loading = true;
  try {
    drawer.interviews = await api.adminUserInterviews(row.id);
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    drawer.loading = false;
  }
}

function canReport(row: api.AdminInterviewView): boolean {
  return row.status === 'completed' || row.hasReport;
}

// ---------- 只读报告 ----------
const report = reactive({
  visible: false,
  loading: false,
  title: '',
  data: null as api.ReportResponse | null,
});

const reportDims = computed(() => {
  const r = report.data;
  if (!r || !r.dimensions) return [] as Array<{ label: string; value: number }>;
  return Object.entries(r.dimensions).map(([k, v]) => ({ label: labelOf(k), value: Number(v) || 0 }));
});

function scoreTag(score?: number | null) {
  if (score == null) return 'info';
  if (score >= 85) return 'success';
  if (score >= 70) return 'primary';
  if (score >= 60) return 'warning';
  return 'danger';
}

async function openReport(iv: api.AdminInterviewView) {
  report.title = `${iv.title || '面试'}#${iv.id}`;
  report.data = null;
  report.visible = true;
  report.loading = true;
  try {
    report.data = await api.adminUserInterviewReport(drawer.userId, iv.id);
  } catch (e) {
    ElMessage.error((e as Error).message);
    report.visible = false;
  } finally {
    report.loading = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>用户管理</span>
          <el-button type="primary" style="background: #1a237e" @click="openCreate">新增管理员</el-button>
        </div>
      </template>

      <!-- 搜索区 -->
      <div style="display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; align-items: center">
        <el-input v-model="query.keyword" placeholder="搜索邮箱 / 昵称…" clearable style="width: 240px" @keyup.enter="onSearch" @clear="onSearch" />
        <el-select v-model="query.role" style="width: 140px" @change="onSearch">
          <el-option v-for="o in ROLE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.active" style="width: 140px" @change="onSearch">
          <el-option v-for="o in ACTIVE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button type="primary" style="background: #1a237e" @click="onSearch">查询</el-button>
        <span class="muted">共 {{ total }} 个账号</span>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe empty-text="暂无用户" size="default">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />
        <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'primary' : 'info'" size="small" effect="plain">{{ roleText(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="activeTag(row.active).type" size="small">{{ activeTag(row.active).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="最近登录" width="150">
          <template #default="{ row }">{{ fmtTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openInterviews(row)">查看面试</el-button>
            <el-button
              size="small"
              text
              :type="row.active ? 'danger' : 'success'"
              @click="toggleActive(row)"
            >{{ row.active ? '停用' : '启用' }}</el-button>
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

    <!-- 新增管理员对话框 -->
    <el-dialog v-model="create.visible" title="新增管理员" width="440px">
      <el-form label-width="80px">
        <el-form-item label="邮箱">
          <el-input v-model="create.email" placeholder="admin@example.com" @keyup.enter="saveCreate" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="create.password" type="password" show-password placeholder="至少 8 位" @keyup.enter="saveCreate" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="create.nickname" placeholder="可空" @keyup.enter="saveCreate" />
        </el-form-item>
      </el-form>
      <p class="muted">新增的管理员账号将立即拥有后台管理权限。</p>
      <template #footer>
        <el-button @click="create.visible = false">取消</el-button>
        <el-button type="primary" style="background: #1a237e" :loading="create.submitting" @click="saveCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 某用户面试抽屉 -->
    <el-drawer v-model="drawer.visible" :title="`查看面试 — ${drawer.userLabel}`" size="860px">
      <el-table v-loading="drawer.loading" :data="drawer.interviews" border stripe empty-text="该用户暂无面试" size="small">
        <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="targetPosition" label="目标岗位" min-width="130" show-overflow-tooltip />
        <el-table-column label="模式" width="80">
          <template #default="{ row }">
            <el-tag :type="modeTagType(row.mode)" size="small" effect="plain">{{ modeText(row.mode) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="90">
          <template #default="{ row }">{{ row.completedQuestionCount }}/{{ row.questionCount }}</template>
        </el-table-column>
        <el-table-column label="匹配分" width="80">
          <template #default="{ row }">{{ row.matchScore != null ? Math.round(row.matchScore) : '-' }}</template>
        </el-table-column>
        <el-table-column label="评级" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.overallRating" :type="ratingTagType(row.overallRating)" size="small">{{ row.overallRating }} {{ ratingText(row.overallRating) }}</el-tag>
            <span v-else class="muted">未评级</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" :disabled="!canReport(row)" @click="openReport(row)">查看报告</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 只读报告对话框 -->
    <el-dialog v-model="report.visible" :title="`综合报告 — ${report.title}`" width="820px" top="4vh">
      <div v-loading="report.loading" style="min-height: 200px; max-height: 70vh; overflow-y: auto">
        <template v-if="report.data">
          <!-- 评级概览 -->
          <div class="report-head">
            <template v-if="report.data.mode === 'practice' || !report.data.rating">
              <div class="head-badge muted" style="font-size: 14px">练习复盘 · 无评级</div>
            </template>
            <template v-else>
              <div class="head-badge">
                <span class="rating-letter" :style="{ color: ratingTagType(report.data.rating) === 'success' ? '#00c853' : ratingTagType(report.data.rating) === 'primary' ? '#1a237e' : ratingTagType(report.data.rating) === 'warning' ? '#ff9100' : '#f56c6c' }">{{ report.data.rating }}</span>
                <span class="rating-text">{{ ratingText(report.data.rating) }}</span>
              </div>
            </template>
            <div class="head-meta">
              <span v-if="report.data.matchScore != null" class="meta-item">综合匹配分：{{ Math.round(report.data.matchScore) }}</span>
              <span class="meta-item">模式：{{ modeText(report.data.mode) }}</span>
            </div>
          </div>

          <!-- 六维 -->
          <div v-if="reportDims.length" class="dims">
            <div v-for="d in reportDims" :key="d.label" class="dim-chip">
              {{ d.label }} <b>{{ d.value }}</b>
            </div>
          </div>

          <!-- 优势 / 待改进 -->
          <el-row v-if="(report.data.strengths || []).length || (report.data.improvements || []).length" :gutter="12">
            <el-col :span="12">
              <div class="sec-title">优势</div>
              <ul class="tag-list">
                <li v-for="(s, i) in (report.data.strengths || [])" :key="i">{{ s }}</li>
              </ul>
            </el-col>
            <el-col :span="12">
              <div class="sec-title">待改进</div>
              <ul class="tag-list weak">
                <li v-for="(s, i) in (report.data.improvements || [])" :key="i">{{ s }}</li>
              </ul>
            </el-col>
          </el-row>

          <!-- 逐题回放 -->
          <div class="sec-title">逐题回放</div>
          <template v-if="report.data.questions && report.data.questions.length">
            <div v-for="q in report.data.questions" :key="q.orderIndex" class="q-item">
              <div class="q-head">
                <b>第 {{ q.orderIndex }} 题</b>
                <el-tag :type="scoreTag(q.score)" size="small">{{ q.score != null ? q.score + ' 分' : '未评分' }}</el-tag>
              </div>
              <p class="q-text">{{ q.question }}</p>
              <p v-if="q.answer" class="q-answer">回答：{{ q.answer }}</p>
              <div v-if="q.strong?.length" class="q-chips">
                <el-tag v-for="(s, i) in q.strong" :key="i" size="small" type="success" effect="plain" style="margin: 0 6px 6px 0">{{ s }}</el-tag>
              </div>
              <div v-if="q.weak?.length" class="q-chips">
                <el-tag v-for="(w, i) in q.weak" :key="i" size="small" type="danger" effect="plain" style="margin: 0 6px 6px 0">{{ w }}</el-tag>
              </div>
              <p v-if="q.suggestion" class="q-suggestion">{{ q.suggestion }}</p>
            </div>
          </template>
          <el-empty v-else description="无逐题数据" :image-size="60" />

          <div v-if="report.data.recommendation" class="recommend">
            <div class="sec-title">综合建议</div>
            <p style="margin: 0; white-space: pre-line">{{ report.data.recommendation }}</p>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.report-head { display: flex; align-items: center; gap: 20px; padding: 6px 0 12px; }
.head-badge { display: flex; align-items: center; gap: 8px; }
.rating-letter { font-size: 40px; font-weight: 800; line-height: 1; }
.rating-text { font-size: 16px; font-weight: 600; }
.head-meta { display: flex; gap: 16px; color: #909399; font-size: 13px; }
.dims { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.dim-chip { border: 1px solid #ebeef5; border-radius: 6px; padding: 4px 10px; font-size: 13px; color: #606266; background: #fafafa; }
.dim-chip b { color: #1a237e; margin-left: 4px; }
.sec-title { font-weight: 600; margin: 10px 0 6px; color: #1a237e; }
.tag-list { margin: 0; padding-left: 18px; color: #303133; font-size: 13px; }
.tag-list li { margin-bottom: 4px; }
.q-item { border: 1px solid #ebeef5; border-radius: 8px; padding: 10px 12px; margin-bottom: 10px; }
.q-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.q-text { margin: 0 0 6px; font-size: 14px; }
.q-answer { margin: 0 0 6px; font-size: 13px; color: #606266; white-space: pre-line; }
.q-suggestion { margin: 4px 0 0; font-size: 13px; color: #b3541e; }
.recommend { margin-top: 8px; }
</style>
