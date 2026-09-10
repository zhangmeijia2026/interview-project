<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import * as api from '../../api';
import { difficultyText, questionTypeText } from '../../utils';

const rows = ref<api.BankAdminView[]>([]);
const total = ref(0);
const loading = ref(false);
const cats = ref<api.BankCategoryCount[]>([]);

const query = reactive({ category: '', keyword: '', page: 0 });
const size = 10;

// 新增/编辑对话框
const edit = reactive({ visible: false, submitting: false, isEdit: false, id: 0 });
const form = reactive({
  category: '',
  questionType: 'qa',
  difficulty: 3,
  content: '',
  answer: '',
  hint: '',
  knowledgePoints: [] as string[],
});
const kpInput = ref('');

// AI 生成对话框
const gen = reactive({ visible: false, submitting: false, category: '', count: 5, difficulty: 3 });

// 当前引擎（练习模式）抽取的是开放问答题，后端默认/生成均为 "qa"；其它类型属预留扩展。
const QUESTION_TYPES = ['qa', 'java', 'sql', 'algorithm', 'network', 'os', 'spring', 'concurrent', 'middleware', 'project', 'behavior'];

async function loadCats() {
  try {
    cats.value = await api.listBankCategories();
  } catch { /* ignore */ }
}

async function load() {
  loading.value = true;
  try {
    const res = await api.adminBankPage({
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

function onSearch() {
  query.page = 0;
  load();
}

function onPageChange(p: number) {
  query.page = p - 1;
  load();
}

function resetForm() {
  form.category = '';
  form.questionType = 'qa';
  form.difficulty = 3;
  form.content = '';
  form.answer = '';
  form.hint = '';
  form.knowledgePoints = [];
  kpInput.value = '';
}

function openCreate() {
  resetForm();
  edit.isEdit = false;
  edit.id = 0;
  edit.visible = true;
}

function openEdit(row: api.BankAdminView) {
  edit.isEdit = true;
  edit.id = row.id;
  form.category = row.category;
  form.questionType = row.questionType;
  form.difficulty = row.difficulty;
  form.content = row.content;
  form.answer = row.answer || '';
  form.hint = row.hint || '';
  form.knowledgePoints = [...(row.knowledgePoints || [])];
  kpInput.value = '';
  edit.visible = true;
}

function addKp() {
  const v = kpInput.value.trim();
  if (!v) return;
  if (!form.knowledgePoints.includes(v)) form.knowledgePoints.push(v);
  kpInput.value = '';
}

async function saveRow() {
  if (!form.category.trim() || !form.content.trim()) {
    ElMessage.warning('请填写分类与题干');
    return;
  }
  edit.submitting = true;
  const payload = {
    category: form.category.trim(),
    questionType: form.questionType,
    difficulty: form.difficulty,
    content: form.content.trim(),
    answer: form.answer.trim() || undefined,
    hint: form.hint.trim() || undefined,
    knowledgePoints: form.knowledgePoints,
  };
  try {
    if (edit.isEdit) {
      await api.adminBankUpdate(edit.id, payload);
      ElMessage.success('题目已更新');
    } else {
      await api.adminBankCreate(payload);
      ElMessage.success('题目已新增');
    }
    edit.visible = false;
    load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    edit.submitting = false;
  }
}

async function removeRow(row: api.BankAdminView) {
  try {
    await ElMessageBox.confirm(`删除题目 #${row.id}？删除后不可恢复。`, '提示', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
    });
  } catch { return; }
  try {
    await api.adminBankDelete(row.id);
    ElMessage.success('已删除');
    load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

async function openGen() {
  gen.category = query.category || '';
  gen.count = 5;
  gen.difficulty = 3;
  gen.visible = true;
}

async function runGen() {
  if (!gen.category.trim()) {
    ElMessage.warning('请选择要生成的分类');
    return;
  }
  gen.submitting = true;
  try {
    const res = await api.adminBankGenerate({
      category: gen.category.trim(),
      count: gen.count,
      difficulty: gen.difficulty,
    });
    ElMessage.success(`AI 已生成并入库 ${res.inserted} 道题目`);
    gen.visible = false;
    load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    gen.submitting = false;
  }
}

onMounted(() => {
  loadCats();
  load();
});
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>题库管理</span>
          <div>
            <el-button type="primary" plain @click="openGen">AI 一键生成</el-button>
            <el-button type="primary" style="background: #1a237e" @click="openCreate">新增题目</el-button>
          </div>
        </div>
      </template>

      <div style="display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; align-items: center">
        <el-select v-model="query.category" placeholder="全部分类" clearable style="width: 200px" @change="onSearch">
          <el-option v-for="c in cats" :key="c.category" :label="`${c.category}（${c.count}）`" :value="c.category" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="搜索题干/答案…" clearable style="width: 260px" @keyup.enter="onSearch" @clear="onSearch" />
        <el-button type="primary" style="background: #1a237e" @click="onSearch">搜索</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe empty-text="暂无题目" size="default">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="category" label="分类" width="110" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ questionTypeText(row.questionType) }}</template>
        </el-table-column>
        <el-table-column label="难度" width="90">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ difficultyText(row.difficulty) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="题干" min-width="220" show-overflow-tooltip />
        <el-table-column label="知识点" width="160">
          <template #default="{ row }">
            <el-tag v-for="(k, i) in (row.knowledgePoints || []).slice(0, 2)" :key="i" size="small" effect="plain" style="margin-right: 4px">{{ k }}</el-tag>
            <span v-if="(row.knowledgePoints || []).length > 2" class="muted">+{{ row.knowledgePoints!.length - 2 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.sourceAi ? 'warning' : 'info'" effect="plain">{{ row.sourceAi ? 'AI' : '人工' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">{{ row.enabled ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="removeRow(row)">删除</el-button>
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

    <!-- 新增 / 编辑对话框 -->
    <el-dialog v-model="edit.visible" :title="edit.isEdit ? '编辑题目' : '新增题目'" width="640px">
      <el-form label-width="90px">
        <el-form-item label="分类">
          <el-input v-model="form.category" placeholder="如：Java、数据库" maxlength="32" />
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="form.questionType" style="width: 220px">
            <el-option v-for="t in QUESTION_TYPES" :key="t" :label="questionTypeText(t)" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-rate v-model="form.difficulty" :max="5" show-score :texts="['入门', '基础', '进阶', '较难', '困难']" score-template="{value}" />
        </el-form-item>
        <el-form-item label="题干">
          <el-input v-model="form.content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="参考答案">
          <el-input v-model="form.answer" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="提示">
          <el-input v-model="form.hint" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="知识点">
          <div style="width: 100%">
            <div style="display: flex; gap: 8px; margin-bottom: 6px">
              <el-input v-model="kpInput" placeholder="输入知识点后回车添加" @keyup.enter="addKp" />
              <el-button @click="addKp">添加</el-button>
            </div>
            <el-tag v-for="(k, i) in form.knowledgePoints" :key="i" closable type="info" effect="plain" style="margin: 0 6px 6px 0" @close="form.knowledgePoints.splice(i, 1)">
              {{ k }}
            </el-tag>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="edit.visible = false">取消</el-button>
        <el-button type="primary" style="background: #1a237e" :loading="edit.submitting" @click="saveRow">保存</el-button>
      </template>
    </el-dialog>

    <!-- AI 生成对话框 -->
    <el-dialog v-model="gen.visible" title="AI 一键扩充题库" width="480px">
      <el-form label-width="90px">
        <el-form-item label="分类">
          <el-select v-model="gen.category" filterable allow-create default-first-option placeholder="选择或输入分类" style="width: 100%">
            <el-option v-for="c in cats" :key="c.category" :label="c.category" :value="c.category" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="gen.count" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="难度">
          <el-rate v-model="gen.difficulty" :max="5" />
        </el-form-item>
      </el-form>
      <p class="muted">AI 将按给定分类生成（题干 + 参考答案 + 提示 + 知识点）并入库，默认启用。</p>
      <template #footer>
        <el-button @click="gen.visible = false">取消</el-button>
        <el-button type="primary" style="background: #1a237e" :loading="gen.submitting" @click="runGen">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>
