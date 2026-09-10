import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '../store';

// 路由表（docs/08 §2 页面映射扩展版）：工作台/题库/新建向导/答题页/报告页/历史页
// + 薄弱技能库/个人中心/用户反馈/简历对比 + 管理后台（左侧栏 AdminLayout）。
// /interviews/:id/setup 供"历史中未完成资料准备"的会话从断点继续完善（与 /interviews/new 共用向导组件）。
// 管理端 /admin 及其子页统一收进 AdminLayout children（URL 保持不变：/admin/bank、/admin/feedback、/admin/llm）。
const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/home' },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/home', name: 'home', component: () => import('../views/HomeView.vue') },
  { path: '/bank', name: 'bank', component: () => import('../views/QuestionBankView.vue') },
  { path: '/interviews/new', name: 'interview-new', component: () => import('../views/InterviewWizardView.vue') },
  { path: '/interviews/:id/setup', name: 'interview-setup', component: () => import('../views/InterviewWizardView.vue'), props: true },
  { path: '/interviews/:id/engine', name: 'interview-engine', component: () => import('../views/InterviewEngineView.vue'), props: true },
  { path: '/interviews/:id/report', name: 'interview-report', component: () => import('../views/ReportView.vue'), props: true },
  { path: '/history', name: 'history', component: () => import('../views/HistoryView.vue') },
  { path: '/weak', name: 'weak', component: () => import('../views/WeakSkillLibraryView.vue') },
  { path: '/center', name: 'center', component: () => import('../views/PersonalCenterView.vue') },
  { path: '/feedback', name: 'feedback', component: () => import('../views/FeedbackView.vue') },
  { path: '/compare', name: 'compare', component: () => import('../views/ResumeCompareView.vue') },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    meta: { admin: true },
    children: [
      { path: '', name: 'admin-overview', component: () => import('../views/admin/AdminOverviewView.vue'), meta: { admin: true } },
      { path: 'users', name: 'admin-users', component: () => import('../views/admin/AdminUsersView.vue'), meta: { admin: true } },
      { path: 'blacklist', name: 'admin-blacklist', component: () => import('../views/admin/AdminBlacklistView.vue'), meta: { admin: true } },
      { path: 'bank', name: 'admin-bank', component: () => import('../views/admin/AdminBankView.vue'), meta: { admin: true } },
      { path: 'feedback', name: 'admin-feedback', component: () => import('../views/admin/AdminFeedbackView.vue'), meta: { admin: true } },
      { path: 'llm', name: 'admin-llm', component: () => import('../views/admin/AdminLlmView.vue'), meta: { admin: true } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 全局前置守卫：
// 1) 未登录除 /login 外一律回登录页；
// 2) 已登录访问 /login → 按角色落地（admin→/admin，普通用户→/home）；
// 3) meta.admin 路由仅 admin 可进，普通用户回 /home；
// 4) admin 只能访问后台 /admin**、个人中心 /center 与登录页，其余用户区一律重定向 /admin
//    （管理员视角没有用户首页/工作台，只保留个人中心 + 后台管理）。
router.beforeEach((to) => {
  const auth = useAuthStore();
  if (!auth.loggedIn && to.path !== '/login') return { path: '/login' };
  if (auth.loggedIn && to.path === '/login') return { path: auth.isAdmin ? '/admin' : '/home' };
  const isAdminPath = to.path.startsWith('/admin');
  if (to.meta.admin && !auth.isAdmin) return { path: '/home' };
  if (auth.isAdmin && !isAdminPath && to.path !== '/center' && to.path !== '/login') {
    return { path: '/admin' };
  }
  return true;
});

export default router;
