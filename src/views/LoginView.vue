<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../store';
import { register } from '../api';

const auth = useAuthStore();
const router = useRouter();
const mode = ref<'login' | 'register'>('login');
const loading = ref(false);
const form = reactive({ email: '', password: '', verifyCode: '123456' });

async function submit() {
  if (!form.email || !form.password) {
    ElMessage.warning('请输入邮箱与密码');
    return;
  }
  if (mode.value === 'register' && !form.verifyCode) {
    ElMessage.warning('请输入注册验证码');
    return;
  }
  loading.value = true;
  try {
    if (mode.value === 'register') {
      await register(form.email.trim(), form.password, form.verifyCode.trim());
      ElMessage.success('注册成功，请登录');
      mode.value = 'login';
      form.password = '';
    } else {
      await auth.login(form.email.trim(), form.password);
      ElMessage.success('登录成功');
      // 管理员直达后台管理控制台；普通用户进工作台
      router.replace(auth.isAdmin ? '/admin' : '/home');
    }
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2 style="text-align: center; color: #1a237e">智能面试问答系统</h2>
      <p class="muted" style="text-align: center">简历-JD 匹配 · AI 模拟面试 · 综合报告（MVP）</p>
      <el-tabs v-model="mode" stretch>
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="you@example.com" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 8 位" @keyup.enter="submit" />
        </el-form-item>
        <el-form-item v-if="mode === 'register'" label="注册验证码（演示默认 123456）">
          <el-input v-model="form.verifyCode" placeholder="123456" @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" style="width: 100%; background: #1a237e" :loading="loading" @click="submit">
          {{ mode === 'login' ? '登录' : '注册' }}
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-wrap {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a237e 0%, #3949ab 100%);
}
.login-card { width: 400px; padding: 8px 6px; }
</style>
