<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import * as api from '../api';
import { fmtTime, modeText, modeTagType } from '../utils';

const route = useRoute();
const router = useRouter();

const activeTab = ref<string>(String(route.query.tab || 'skills'));

// ---------- Tab1 薄弱技能 ----------
const skills = ref<api.WeakSkillView[]>([]);
const skillsLoading = ref(false);

async function loadSkills() {
  skillsLoading.value = true;
  try {
    skills.value = await api.listWeakSkills();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    skillsLoading.value = false;
  }
}

function severityColor(sev: number): string {
  // severity 越低越弱
  if (sev < 35) return '#f56c6c';
  if (sev < 60) return '#e6a23c';
  return '#67c23a';
}

async function resolveSkill(row: api.WeakSkillView) {
  try {
    await ElMessageBox.confirm(`确认把「${row.skillTag}」标记为已攻克？`, '提示', {
      type: 'warning', confirmButtonText: '标记攻克', cancelButtonText: '取消',
    });
  } catch { return; }
  try {
    await api.resolveWeakSkill(row.id);
    ElMessage.success('已标记攻克');
    loadSkills();
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

function goStrengthen(row: api.WeakSkillView) {
  // 跳到新建练习并预选「针对薄弱点」
  router.push({
    path: '/interviews/new',
    query: { mode: 'practice', weakBoost: '1', category: row.category || '' },
  });
}

function goCategoryPractice(category?: string | null) {
  router.push({ path: '/bank', query: { category: category || '' } });
}

// ---------- Tab2 错题本 ----------
const wrongs = ref<api.WrongQuestionView[]>([]);
const wrongTotal = ref(0);
const wrongPage = ref(0);
const wrongSize = 10;
const wrongLoading = ref(false);

async function loadWrong() {
  wrongLoading.value = true;
  try {
    const res = await api.listWrongQuestions(wrongPage.value, wrongSize);
    wrongs.value = res.items;
    wrongTotal.value = res.total;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    wrongLoading.value = false;
  }
}

function onWrongPage(p: number) {
  wrongPage.value = p - 1;
  loadWrong();
}

function onTabChange(name: string | number | boolean) {
  const t = String(name);
  router.replace({ query: { tab: t } });
}

watch(() => route.query.tab, (val) => {
  const t = val ? String(val) : 'skills';
  if (t !== activeTab.value) activeTab.value = t;
});

onMounted(() => {
  loadSkills();
  loadWrong();
});
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <el-tabs :model-value="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="薄弱技能" name="skills">
          <div v-loading="skillsLoading" style="min-height: 120px">
            <template v-if="skills.length">
              <el-card v-for="s in skills" :key="s.id" shadow="hover" class="skill-card">
                <div class="skill-row">
                  <div>
                    <span class="skill-name">{{ s.skillTag }}</span>
                    <el-tag size="small" type="info" effect="plain" style="margin-left: 8px">{{ s.category }}</el-tag>
                    <el-tag v-if="s.status === 'resolved'" size="small" type="success" style="margin-left: 6px">已攻克</el-tag>
                  </div>
                  <div style="display: flex; align-items: center; gap: 10px">
                    <span class="muted">薄弱度</span>
                    <el-progress
                      :percentage="100 - s.severity"
                      :stroke-width="12"
                      :color="severityColor(s.severity)"
                      style="width: 140px"
                      :format="() => (100 - s.severity) + '%'"
                    />
                    <el-button v-if="s.status !== 'resolved'" type="primary" size="small" style="background: #1a237e" @click="goStrengthen(s)">
                      去加强 →
                    </el-button>
                    <el-button v-else size="small" text disabled>已标记攻克</el-button>
                  </div>
                </div>
                <div class="muted" style="margin-top: 6px">
                  命中 {{ s.interviewCount }} 场
                  <template v-if="s.latestScore != null"> · 最近得分 {{ s.latestScore }}</template>
                  <template v-if="s.lastSeen"> · 最近出现 {{ fmtTime(s.lastSeen) }}</template>
                </div>
                <div v-if="s.evidence && s.evidence.length" class="evi">
                  <span class="muted">依据：</span>
                  <span v-for="(e, i) in s.evidence" :key="i" class="muted" style="margin-right: 6px">· {{ e }}</span>
                </div>
              </el-card>
            </template>
            <el-empty v-else-if="!skillsLoading" description="暂无薄弱技能记录。完成几场面试/练习后，系统会自动识别你的薄弱项。" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="错题本" name="wrong">
          <p class="muted" style="margin: 0 0 10px">低分（&lt;60）题目快照：题目 / 你的作答 / 参考答案，供复盘重练。</p>
          <div v-loading="wrongLoading" style="min-height: 120px">
            <template v-if="wrongs.length">
              <el-card v-for="(w, idx) in wrongs" :key="w.interviewId + '-' + w.orderIndex" shadow="hover" class="wrong-card">
                <div style="display: flex; justify-content: space-between; align-items: flex-start">
                  <span style="font-weight: 600">{{ w.question }}</span>
                  <el-tag type="danger" size="small" effect="dark">{{ w.score }} 分</el-tag>
                </div>
                <div class="muted" style="margin: 4px 0">
                  来源：{{ w.title }}<el-tag :type="modeTagType(w.mode)" size="small" style="margin-left: 6px">{{ modeText(w.mode) }}</el-tag>
                  <template v-if="w.category"> · 分类：{{ w.category }}</template>
                </div>
                <p class="answer-block"><b>你的作答：</b>{{ w.myAnswer || '（未作答）' }}</p>
                <el-alert v-if="w.referenceAnswer" type="success" :closable="false">
                  <template #title><b>参考答案：</b>{{ w.referenceAnswer }}</template>
                </el-alert>
                <p v-if="w.hint" class="muted" style="margin: 4px 0 0">提示：{{ w.hint }}</p>
                <div style="text-align: right; margin-top: 8px">
                  <el-button v-if="w.category" size="small" text type="primary" @click="goCategoryPractice(w.category)">去练同类题 →</el-button>
                  <el-button size="small" text type="primary" @click="router.push(`/interviews/${w.interviewId}/report`)">查看原报告</el-button>
                </div>
              </el-card>
            </template>
            <el-empty v-else-if="!wrongLoading" description="暂无错题记录，继续保持答题质量！" />
          </div>
          <div style="display: flex; justify-content: flex-end; margin-top: 14px">
            <el-pagination
              v-if="wrongTotal > wrongSize"
              layout="prev, pager, next"
              :total="wrongTotal"
              :page-size="wrongSize"
              :current-page="wrongPage + 1"
              background
              @current-change="onWrongPage"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.skill-card { margin-bottom: 12px; }
.skill-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.skill-name { font-size: 16px; font-weight: 600; color: #1a237e; }
.evi { margin-top: 6px; border-top: 1px dashed #ebeef5; padding-top: 6px; }
.wrong-card { margin-bottom: 12px; }
.answer-block { background: #f7f8fa; padding: 8px 10px; border-radius: 6px; line-height: 1.6; }
</style>
