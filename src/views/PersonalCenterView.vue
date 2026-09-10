<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import * as api from '../api';
import { useAuthStore } from '../store';
import { modeText, ratingText, ratingTagType } from '../utils';

const auth = useAuthStore();
const router = useRouter();

const loading = ref(false);
const me = ref<api.MeResponse | null>(null);
const stats = ref<api.UserStatsResponse | null>(null);
const models = ref<Array<{ id: string; name: string }>>([]);

const profileForm = reactive({ nickname: '', avatarUrl: '' });
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' });
const settingForm = reactive({
  modelProvider: 'deepseek',
  modelName: 'deepseek-chat',
  language: 'zh',
  notifyEnabled: true,
});

async function loadAll() {
  loading.value = true;
  try {
    const [m, st, modelList] = await Promise.all([
      api.getMe(),
      api.getUserStats(),
      api.getModels().catch(() => []),
    ]);
    me.value = m;
    stats.value = st;
    models.value = modelList;
    profileForm.nickname = m.nickname || '';
    profileForm.avatarUrl = m.avatarUrl || '';
    const s = m.settings;
    if (s) {
      settingForm.modelProvider = s.modelProvider || 'deepseek';
      settingForm.modelName = s.modelName || 'deepseek-chat';
      settingForm.language = s.language || 'zh';
      settingForm.notifyEnabled = s.notifyEnabled;
    }
    if (m.role) auth.setUser({ ...auth.user!, id: m.id, email: m.email, nickname: m.nickname, role: m.role });
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

async function onSaveProfile() {
  if (!profileForm.nickname.trim()) {
    ElMessage.warning('昵称不能为空');
    return;
  }
  loading.value = true;
  try {
    const updated = await api.updateMe({ nickname: profileForm.nickname.trim(), avatarUrl: profileForm.avatarUrl.trim() });
    me.value = updated;
    if (me.value) auth.setUser({ ...auth.user!, nickname: me.value.nickname || '', email: me.value.email });
    ElMessage.success('资料已更新');
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

async function onChangePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请输入原密码与新密码');
    return;
  }
  if (pwdForm.newPassword.length < 8) {
    ElMessage.warning('新密码至少 8 位');
    return;
  }
  if (pwdForm.newPassword !== pwdForm.confirm) {
    ElMessage.warning('两次输入的新密码不一致');
    return;
  }
  loading.value = true;
  try {
    await api.changePassword(pwdForm.oldPassword, pwdForm.newPassword);
    ElMessage.success('密码已修改');
    pwdForm.oldPassword = '';
    pwdForm.newPassword = '';
    pwdForm.confirm = '';
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

async function onSaveSettings() {
  loading.value = true;
  try {
    const s = await api.updateSettings({ ...settingForm });
    if (me.value) me.value.settings = s;
    ElMessage.success('设置已保存');
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

const statCards = [
  { key: 'interviewCount', label: '总面试次数', get: () => stats.value?.interviewCount ?? 0 },
  { key: 'formalCount', label: '正式场次', get: () => stats.value?.formalCount ?? 0 },
  { key: 'practiceCount', label: '练习场次', get: () => stats.value?.practiceCount ?? 0 },
  { key: 'completedCount', label: '已完成', get: () => stats.value?.completedCount ?? 0 },
  { key: 'wrongQuestionCount', label: '错题数', get: () => stats.value?.wrongQuestionCount ?? 0 },
  { key: 'weakSkillCount', label: '薄弱技能数', get: () => stats.value?.weakSkillCount ?? 0 },
  { key: 'averageScore', label: '平均得分', get: () => stats.value?.averageScore != null ? Math.round(stats.value!.averageScore) : '-' },
  { key: 'latestRating', label: '最近评级', get: () => stats.value?.latestRating ? `${stats.value.latestRating} ${ratingText(stats.value.latestRating)}` : '无' },
];

onMounted(loadAll);
</script>

<template>
  <div v-loading="loading" class="page">
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>个人中心</span>
          <el-button size="small" @click="router.push('/history')">查看我的面试历史 →</el-button>
        </div>
      </template>

      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="邮箱">{{ me?.email || auth.user?.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ me?.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ me?.role === 'admin' ? '管理员' : '普通用户' }}</el-descriptions-item>
        <el-descriptions-item label="模型">{{ settingForm.modelName }}</el-descriptions-item>
        <el-descriptions-item label="接口语言">{{ settingForm.language === 'zh' ? '中文' : 'English' }}</el-descriptions-item>
        <el-descriptions-item label="最近模式">
          <template v-if="stats?.latestMode">
            {{ modeText(stats.latestMode) }}
            <el-tag v-if="stats.latestRating" :type="ratingTagType(stats.latestRating)" size="small" style="margin-left: 6px">
              {{ stats.latestRating }}
            </el-tag>
          </template>
          <span v-else class="muted">-</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 学习统计卡 -->
    <el-card shadow="never" class="mt">
      <template #header><b>学习统计</b></template>
      <div class="stat-grid">
        <div v-for="c in statCards" :key="c.key" class="stat-card">
          <div class="stat-num">{{ c.get() }}</div>
          <div class="muted">{{ c.label }}</div>
        </div>
      </div>
    </el-card>

    <el-tabs class="mt">
      <!-- 资料 -->
      <el-tab-pane label="编辑资料" name="profile">
        <el-form label-width="90px" style="max-width: 520px">
          <el-form-item label="昵称">
            <el-input v-model="profileForm.nickname" placeholder="昵称" />
          </el-form-item>
          <el-form-item label="头像链接">
            <el-input v-model="profileForm.avatarUrl" placeholder="头像 URL（可选）" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" style="background: #1a237e" :loading="loading" @click="onSaveProfile">保存资料</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 设置 -->
      <el-tab-pane label="偏好设置" name="settings">
        <el-form label-width="110px" style="max-width: 520px">
          <el-form-item label="模型供应商">
            <el-select v-model="settingForm.modelProvider" style="width: 100%">
              <el-option label="DeepSeek" value="deepseek" />
              <el-option label="OpenAI 兼容" value="openai" />
            </el-select>
          </el-form-item>
          <el-form-item label="对话模型">
            <el-select v-model="settingForm.modelName" style="width: 100%">
              <el-option v-for="m in models" :key="m.id" :label="m.name" :value="m.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="界面语言">
            <el-radio-group v-model="settingForm.language">
              <el-radio-button value="zh">中文</el-radio-button>
              <el-radio-button value="en">English</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="通知">
            <el-switch v-model="settingForm.notifyEnabled" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" style="background: #1a237e" :loading="loading" @click="onSaveSettings">保存设置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 改密 -->
      <el-tab-pane label="修改密码" name="password">
        <el-form label-width="90px" style="max-width: 520px">
          <el-form-item label="原密码">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="原密码" />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 8 位" />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input v-model="pwdForm.confirm" type="password" show-password placeholder="再次输入新密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" style="background: #1a237e" :loading="loading" @click="onChangePassword">确认修改</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.stat-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 12px; }
.stat-card {
  border: 1px solid #ebeef5; border-radius: 8px; padding: 14px 16px; text-align: center;
}
.stat-num { font-size: 26px; font-weight: 700; color: #1a237e; }
</style>
