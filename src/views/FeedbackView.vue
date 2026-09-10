<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as api from '../api';
import { feedbackCategoryText, feedbackStatusText, fmtTime } from '../utils';

const list = ref<api.FeedbackView[]>([]);
const loading = ref(false);
const submitting = ref(false);

const form = reactive<{ category: api.FeedbackCategory; content: string; contact: string }>({
  category: 'suggestion',
  content: '',
  contact: '',
});

async function load() {
  loading.value = true;
  try {
    list.value = await api.listMyFeedback();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

async function onSubmit() {
  if (!form.content.trim()) {
    ElMessage.warning('请填写反馈内容');
    return;
  }
  submitting.value = true;
  try {
    await api.submitFeedback({
      category: form.category,
      content: form.content.trim(),
      contact: form.contact.trim() || undefined,
    });
    ElMessage.success('反馈已提交，感谢你的建议');
    form.content = '';
    form.contact = '';
    load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    submitting.value = false;
  }
}

function statusTag(s?: string): string {
  if (s === 'done') return 'success';
  if (s === 'processing') return 'warning';
  return 'info';
}

onMounted(load);
</script>

<template>
  <div class="page">
    <el-row :gutter="16">
      <!-- 提交反馈 -->
      <el-col :xs="24" :md="10">
        <el-card shadow="never">
          <template #header><b>提交反馈</b></template>
          <el-form label-width="80px" @submit.prevent="onSubmit">
            <el-form-item label="分类">
              <el-select v-model="form.category" style="width: 100%">
                <el-option label="建议" value="suggestion" />
                <el-option label="缺陷反馈" value="bug" />
                <el-option label="咨询" value="question" />
                <el-option label="表扬" value="praise" />
                <el-option label="其他" value="other" />
              </el-select>
            </el-form-item>
            <el-form-item label="内容">
              <el-input v-model="form.content" type="textarea" :rows="6" maxlength="2000" show-word-limit placeholder="请描述你的建议或遇到的问题…" />
            </el-form-item>
            <el-form-item label="联系方式">
              <el-input v-model="form.contact" maxlength="100" placeholder="邮箱 / 电话（可选，便于回复）" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" style="background: #1a237e" :loading="submitting" @click="onSubmit">
                提交反馈
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 我的反馈列表 -->
      <el-col :xs="24" :md="14">
        <el-card shadow="never">
          <template #header><b>我的反馈</b></template>
          <div v-loading="loading">
            <template v-if="list.length">
              <el-card v-for="f in list" :key="f.id" shadow="hover" class="fb-item">
                <div style="display: flex; justify-content: space-between; align-items: flex-start">
                  <span>
                    <el-tag size="small" effect="plain">{{ feedbackCategoryText(f.category) }}</el-tag>
                    <el-tag size="small" :type="statusTag(f.status) as any" style="margin-left: 6px">{{ feedbackStatusText(f.status) }}</el-tag>
                  </span>
                  <span class="muted">{{ fmtTime(f.createdAt) }}</span>
                </div>
                <p style="margin: 10px 0 4px; line-height: 1.6">{{ f.content }}</p>
                <el-alert v-if="f.status === 'done' && f.reply" type="success" :closable="false" class="mt">
                  <template #title>管理员回复（{{ fmtTime(f.repliedAt) }}）：{{ f.reply }}</template>
                </el-alert>
                <p v-else class="muted" style="margin-top: 8px">
                  {{ f.status === 'done' ? '已处理（无文字回复）' : f.status === 'processing' ? '处理中，请耐心等待…' : '待处理…' }}
                </p>
              </el-card>
            </template>
            <el-empty v-else-if="!loading" description="暂无反馈记录" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.fb-item { margin-bottom: 10px; }
</style>
