<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import * as api from '../api';
import { difficultyTag, difficultyText, questionTypeText } from '../utils';

const route = useRoute();
const router = useRouter();

const cats = ref<api.BankCategoryCount[]>([]);
const rows = ref<api.BankQuestionView[]>([]);
const total = ref(0);
const loading = ref(false);

const query = reactive({ category: '', keyword: '', page: 0 });
const size = 10;

/** 练习弹窗 */
const dialog = reactive({ visible: false, submitting: false });
const practiceForm = reactive({ category: '', count: 5, weakBoost: false });

async function loadCategories() {
  try {
    cats.value = await api.listBankCategories();
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

async function load() {
  loading.value = true;
  try {
    const res = await api.listBankQuestions({
      category: query.category || undefined,
      keyword: query.keyword.trim() || undefined,
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

function onCategoryClick(cat: string) {
  query.category = query.category === cat ? '' : cat;
  query.page = 0;
  load();
}

function onSearch() {
  query.page = 0;
  load();
}

function onPageChange(p: number) {
  query.page = p - 1;
  load();
}

function openPractice(category?: string) {
  practiceForm.category = category ?? query.category ?? '';
  practiceForm.count = 5;
  practiceForm.weakBoost = false;
  dialog.visible = true;
}

async function onPracticeSubmit() {
  if (!practiceForm.count || practiceForm.count < 1) {
    ElMessage.warning('请选择题目数量');
    return;
  }
  dialog.submitting = true;
  const category = practiceForm.category || '';
  try {
    const created = await api.createInterview({
      title: category ? `专项练习 · ${category}` : '综合练习',
      targetPosition: category ? `${category} 技能练习` : '综合技能练习',
      questionCount: practiceForm.count,
      mode: 'practice',
      weakBoost: practiceForm.weakBoost,
      practiceCategory: category || undefined,
    });
    dialog.visible = false;
    ElMessage.success('练习已创建，即将开始');
    router.push(`/interviews/${created.id}/engine`);
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    dialog.submitting = false;
  }
}

function pickFromRow(row: api.BankQuestionView) {
  openPractice(row.category);
}

onMounted(async () => {
  const q = route.query;
  if (q.category) query.category = String(q.category);
  await loadCategories();
  await load();
  if (q.start === '1') openPractice(String(q.category || '') || undefined);
});

watch(() => route.query.category, (val) => {
  query.category = val ? String(val) : '';
  query.page = 0;
  load();
});
</script>

<template>
  <div class="page">
    <!-- 筛选条 -->
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>题库浏览</span>
          <el-button type="primary" style="background: #1a237e" @click="openPractice()">
            开始练习
          </el-button>
        </div>
      </template>

      <!-- 分类 -->
      <div class="cats">
        <div class="cat-tag" :class="{ active: query.category === '' }" @click="onCategoryClick('')">全部</div>
        <div
          v-for="c in cats"
          :key="c.category"
          class="cat-tag"
          :class="{ active: query.category === c.category }"
          @click="onCategoryClick(c.category)"
        >
          {{ c.category }}（{{ c.count }}）
        </div>
      </div>

      <!-- 关键字搜索 -->
      <div class="search-row">
        <el-input
          v-model="query.keyword"
          placeholder="按关键词搜索题目…"
          clearable
          style="max-width: 320px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button type="primary" style="background: #1a237e" @click="onSearch">搜索</el-button>
      </div>

      <p class="muted" style="margin: 8px 0 0">列表仅展示题干与知识点（不含参考答案），避免提前剧透。</p>
    </el-card>

    <!-- 题目列表 -->
    <el-card shadow="never" class="mt">
      <div v-loading="loading" class="q-list">
        <template v-if="rows.length">
          <div v-for="row in rows" :key="row.id" class="q-card">
            <div style="display: flex; justify-content: space-between; gap: 8px">
              <span class="q-cat">{{ row.category }}</span>
              <span>
                <el-tag size="small" :type="difficultyTag(row.difficulty) as any" effect="plain">
                  {{ difficultyText(row.difficulty) }}
                </el-tag>
                <el-tag size="small" type="info" effect="plain" style="margin-left: 6px">
                  {{ questionTypeText(row.questionType) }}
                </el-tag>
              </span>
            </div>
            <p class="q-content">{{ row.content }}</p>
            <div class="q-foot">
              <span>
                <el-tag v-for="(k, i) in (row.knowledgePoints || [])" :key="i" size="small" effect="plain" class="kp" type="info">
                  {{ k }}
                </el-tag>
              </span>
              <el-button size="small" text type="primary" @click="pickFromRow(row)">练一题该分类 →</el-button>
            </div>
          </div>
        </template>
        <el-empty v-else-if="!loading" description="没有符合条件的题目" />
      </div>

      <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 14px">
        <span class="muted">共 {{ total }} 题</span>
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

    <!-- 开始练习弹窗 -->
    <el-dialog v-model="dialog.visible" title="开始练习" width="460px">
      <el-form label-width="96px">
        <el-form-item label="练习分类">
          <el-select v-model="practiceForm.category" placeholder="全部（跨分类随机抽题）" clearable style="width: 100%">
            <el-option v-for="c in cats" :key="c.category" :label="`${c.category}（${c.count} 题）`" :value="c.category" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目数量">
          <el-input-number v-model="practiceForm.count" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="针对薄弱点">
          <el-switch v-model="practiceForm.weakBoost" />
          <span class="muted" style="margin-left: 8px">开启后优先抽取你薄弱技能相关的题目</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" style="background: #1a237e" :loading="dialog.submitting" @click="onPracticeSubmit">
          开始练习
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cats { display: flex; gap: 10px; flex-wrap: wrap; }
.cat-tag {
  padding: 4px 14px; border: 1px solid #dcdfe6; border-radius: 16px; font-size: 13px;
  cursor: pointer; color: #606266;
}
.cat-tag.active { background: #1a237e; color: #fff; border-color: #1a237e; }
.search-row { display: flex; gap: 10px; margin-top: 14px; }
.q-card {
  border: 1px solid #ebeef5; border-radius: 8px; padding: 12px 14px; margin-bottom: 12px;
}
.q-cat { font-weight: 600; color: #1a237e; }
.q-content { margin: 8px 0; line-height: 1.7; }
.q-foot { display: flex; justify-content: space-between; align-items: center; }
.kp { margin-right: 6px; }
</style>
