<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import * as api from '../../api';
import { fmtTime } from '../../utils';

// 黑名单 = 停用账号（users.active=false）列表，可搜索并一键启用。
const rows = ref<api.AdminUserView[]>([]);
const total = ref(0);
const loading = ref(false);
const size = 10;
const query = reactive({ keyword: '', page: 0 });

function roleText(r?: string): string {
  return r === 'admin' ? '管理员' : '普通用户';
}

async function load() {
  loading.value = true;
  try {
    const res = await api.listAdminUsers({
      keyword: query.keyword.trim() || undefined,
      active: false,
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

async function enableRow(row: api.AdminUserView) {
  try {
    await ElMessageBox.confirm(`确认启用 ${row.email}？启用后该账号可正常登录。`, '操作确认', {
      type: 'warning', confirmButtonText: '确认启用', cancelButtonText: '取消',
    });
  } catch { return; }
  try {
    await api.adminSetActive(row.id, true);
    ElMessage.success('已启用该账号');
    load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <template #header><span>黑名单管理</span></template>
      <p class="muted" style="margin: 0 0 12px">
        黑名单为已停用（active=false）的账号，停用即禁止登录；可在此一键启用恢复。
      </p>

      <div style="display: flex; gap: 10px; margin-bottom: 14px; align-items: center">
        <el-input v-model="query.keyword" placeholder="搜索邮箱 / 昵称…" clearable style="width: 260px" @keyup.enter="onSearch" @clear="onSearch" />
        <el-button type="primary" style="background: #1a237e" @click="onSearch">查询</el-button>
        <span class="muted">共 {{ total }} 个停用账号</span>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe empty-text="黑名单为空" size="default">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />
        <el-table-column prop="nickname" label="昵称" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'primary' : 'info'" size="small" effect="plain">{{ roleText(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="最近登录" width="150">
          <template #default="{ row }">{{ fmtTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="success" @click="enableRow(row)">启用</el-button>
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
  </div>
</template>
