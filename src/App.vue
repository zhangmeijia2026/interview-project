<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AppNavbar from './components/AppNavbar.vue';

const route = useRoute();
// /login 为独立登录页；/admin 后台自带 AdminLayout 顶栏/侧栏，均不显示 AppNavbar
const showNavbar = computed(() => route.path !== '/login' && !route.path.startsWith('/admin'));
</script>

<template>
  <AppNavbar v-if="showNavbar" />
  <!-- key=fullPath：同一视图组件跨路由复用（/interviews/new 与 /setup）时强制按路径重建，避免状态残留 -->
  <router-view :key="route.fullPath" />
</template>
