<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { ChatDotRound, CircleClose, Collection, DataLine, Odometer, User } from '@element-plus/icons-vue';
import { useAuthStore } from '../../store';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

// 左侧导航高亮：按当前路径精确匹配菜单 index
const activeMenu = computed(() => route.path);

async function onLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录吗？', '提示', {
      type: 'warning', confirmButtonText: '退出登录', cancelButtonText: '取消',
    });
  } catch { return; }
  await auth.logout();
  router.push('/login');
}
</script>

<template>
  <div class="admin-shell">
    <!-- 顶部品牌条 -->
    <header class="admin-header">
      <div class="brand-title">智能面试问答系统 · 管理后台</div>
      <div class="header-right">
        <el-button text class="link-btn" @click="router.push('/center')">个人中心</el-button>
        <span class="email">{{ auth.user?.email }}</span>
        <el-button text class="logout-btn" @click="onLogout">退出登录</el-button>
      </div>
    </header>

    <div class="admin-body">
      <!-- 左侧导航 -->
      <aside class="admin-aside">
        <el-menu :default-active="activeMenu" router class="side-menu">
          <el-menu-item index="/admin">
            <el-icon><Odometer /></el-icon><span>概览</span>
          </el-menu-item>
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon><span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/blacklist">
            <el-icon><CircleClose /></el-icon><span>黑名单管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/bank">
            <el-icon><Collection /></el-icon><span>题库管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/feedback">
            <el-icon><ChatDotRound /></el-icon><span>反馈处理</span>
          </el-menu-item>
          <el-menu-item index="/admin/llm">
            <el-icon><DataLine /></el-icon><span>调用统计</span>
          </el-menu-item>
        </el-menu>
      </aside>

      <!-- 主体：各后台页在此渲染（复用原有 /admin/bank 等页面组件） -->
      <main class="admin-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-shell { height: 100%; display: flex; flex-direction: column; }
.admin-header {
  height: 56px; flex-shrink: 0; background: #1a237e; color: #fff;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 20px;
}
.brand-title { font-size: 17px; font-weight: 600; color: #fff; white-space: nowrap; }
.header-right { display: flex; align-items: center; gap: 10px; }
.header-right .link-btn { color: rgba(255, 255, 255, 0.9); }
.header-right .link-btn:hover { color: #fff; }
.header-right .email { font-size: 13px; opacity: 0.85; }
.header-right .logout-btn { color: #fff; }
.header-right .logout-btn:hover { color: #ffd54f; }

.admin-body { flex: 1; display: flex; min-height: 0; }
.admin-aside {
  width: 220px; flex-shrink: 0; background: #fff;
  border-right: 1px solid #ebeef5; overflow-y: auto; padding-top: 6px;
}
.side-menu { border-right: none; }
.side-menu :deep(.el-menu-item.is-active) {
  background: #eef0ff; color: #1a237e; border-right: 3px solid #1a237e;
}
.admin-main { flex: 1; min-width: 0; overflow-y: auto; background: #f5f6fa; }
</style>
