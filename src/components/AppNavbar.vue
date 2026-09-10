<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { ArrowDown } from '@element-plus/icons-vue';
import { useAuthStore } from '../store';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();

const isAdmin = computed(() => auth.isAdmin);

const activeMenu = computed(() => {
  const p = route.path;
  if (p === '/home' || p === '/') return '/home';
  if (p === '/bank') return '/bank';
  if (p.startsWith('/interviews/new')) return '/interviews/new';
  if (p.startsWith('/history')) return '/history';
  return '';
});

async function onLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录吗？', '提示', {
      type: 'warning', confirmButtonText: '退出登录', cancelButtonText: '取消',
    });
  } catch { return; }
  await auth.logout();
  router.push('/login');
}

// 管理员视角没有用户首页/工作台：点品牌直达管理后台（个人中心页顶栏仍显示本组件）
function goHome() { router.push(auth.isAdmin ? '/admin' : '/home'); }
function onMenuSelect(path: string) {
  if (path) router.push(path);
}

function onProfileMenu(cmd: string) {
  if (cmd === 'center') router.push('/center');
  else if (cmd === 'weak') router.push('/weak?tab=skills');
  else if (cmd === 'wrong') router.push('/weak?tab=wrong');
  else if (cmd === 'feedback') router.push('/feedback');
  else if (cmd === 'compare') router.push('/compare');
}

function onAdminMenu(cmd: string) {
  if (cmd === 'overview') router.push('/admin');
  else if (cmd === 'users') router.push('/admin/users');
  else if (cmd === 'blacklist') router.push('/admin/blacklist');
  else if (cmd === 'bank') router.push('/admin/bank');
  else if (cmd === 'feedback') router.push('/admin/feedback');
  else if (cmd === 'llm') router.push('/admin/llm');
}
</script>

<template>
  <header class="nav-bar">
    <div class="nav-inner">
      <div class="brand" role="button" @click="goHome">智能面试问答系统</div>
      <!-- 用户区横向菜单（admin 不展示：管理员只能进后台管理 + 个人中心） -->
      <el-menu
        v-if="!isAdmin"
        mode="horizontal"
        :default-active="activeMenu"
        :ellipsis="false"
        class="nav-menu"
        @select="onMenuSelect"
      >
        <el-menu-item index="/home">工作台</el-menu-item>
        <el-menu-item index="/bank">题库</el-menu-item>
        <el-menu-item index="/interviews/new">新建面试</el-menu-item>
        <el-menu-item index="/history">历史记录</el-menu-item>
      </el-menu>

      <div class="user-area">
        <!-- 个人中心下拉（仅普通用户） -->
        <el-dropdown v-if="!isAdmin" trigger="click" @command="onProfileMenu">
          <span class="drop-link">个人中心<el-icon class="drop-icon"><ArrowDown /></el-icon></span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="center">个人中心</el-dropdown-item>
              <el-dropdown-item command="weak">薄弱技能库</el-dropdown-item>
              <el-dropdown-item command="wrong">错题本</el-dropdown-item>
              <el-dropdown-item command="feedback">用户反馈</el-dropdown-item>
              <el-dropdown-item command="compare">简历对比</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <!-- 后台管理下拉（仅 admin）：在个人中心页也能快速跳回后台控制台 -->
        <el-dropdown v-if="isAdmin" trigger="click" @command="onAdminMenu">
          <span class="drop-link admin-link">后台管理<el-icon class="drop-icon"><ArrowDown /></el-icon></span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="overview">管理概览</el-dropdown-item>
              <el-dropdown-item command="users">用户管理</el-dropdown-item>
              <el-dropdown-item command="blacklist">黑名单管理</el-dropdown-item>
              <el-dropdown-item command="bank">题库管理</el-dropdown-item>
              <el-dropdown-item command="feedback">反馈处理</el-dropdown-item>
              <el-dropdown-item command="llm">DeepSeek 调用统计</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <span class="email">{{ auth.user?.email }}</span>
        <el-button text bg class="logout-btn" @click="onLogout">退出登录</el-button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.nav-bar { background: #1a237e; color: #fff; }
.nav-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  height: 56px;
}
.brand {
  font-size: 17px; font-weight: 600; color: #fff;
  white-space: nowrap; cursor: pointer; user-select: none;
}
.nav-menu { flex: 1; background: transparent; border-bottom: none; }
.nav-menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.82);
  border-bottom: none; height: 56px; line-height: 56px;
}
.nav-menu :deep(.el-menu-item.is-active) {
  color: #fff; border-bottom: 2px solid #fff; background: rgba(255, 255, 255, 0.08);
}
.nav-menu :deep(.el-menu-item:hover) {
  color: #fff; background: rgba(255, 255, 255, 0.12);
}
.user-area { display: flex; align-items: center; gap: 12px; color: #fff; white-space: nowrap; margin-left: auto; }
.drop-link {
  color: rgba(255, 255, 255, 0.9); cursor: pointer; font-size: 13px;
  display: inline-flex; align-items: center; outline: none;
}
.drop-link:hover { color: #fff; }
.drop-icon { margin-left: 2px; }
.admin-link {
  border: 1px solid rgba(255, 255, 255, 0.4); border-radius: 12px;
  padding: 2px 10px; background: rgba(255, 255, 255, 0.06);
}
.email { font-size: 13px; opacity: 0.9; }
.logout-btn { color: #fff; }
.logout-btn:hover { color: #ffd54f; }
</style>
