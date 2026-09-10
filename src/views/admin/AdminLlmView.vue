<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import * as api from '../../api';
import { fmtTime } from '../../utils';

const loading = ref(false);
const days = ref(7);
const stats = ref<api.LlmStatsResponse | null>(null);
const calls = ref<api.LlmCallView[]>([]);
const callsTotal = ref(0);
const callsPage = ref(0);
const callsSize = 20;
const callsLoading = ref(false);

// 图表
const lineEl = ref<HTMLDivElement | null>(null);
const pieEl = ref<HTMLDivElement | null>(null);
let lineChart: echarts.ECharts | null = null;
let pieChart: echarts.ECharts | null = null;

const dailyEmpty = computed(() => !stats.value || !stats.value.daily.length);
const pieEmpty = computed(() => !stats.value || !stats.value.promptBreakdown.length);

const fallbackOk = computed(() => {
  if (!stats.value || stats.value.totalCalls === 0) return true;
  return (stats.value.fallbackCalls / stats.value.totalCalls) < 0.5;
});

function renderLine() {
  if (!lineEl.value || !stats.value) return;
  if (dailyEmpty.value) {
    lineChart?.dispose();
    lineChart = null;
    return;
  }
  if (!lineChart) lineChart = echarts.init(lineEl.value);
  const daily = stats.value.daily;
  const dates = daily.map((d) => d.date.slice(5)); // MM-DD
  lineChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['调用次数', '回退次数', '费用(¥)'] },
    grid: { left: 50, right: 60, top: 40, bottom: 28 },
    xAxis: { type: 'category', data: dates },
    yAxis: [
      { type: 'value', name: '次数' },
      { type: 'value', name: '费用(¥)', axisLabel: { formatter: (v: number) => v.toFixed(2) } },
    ],
    series: [
      { name: '调用次数', type: 'line', smooth: true, data: daily.map((d) => d.calls), itemStyle: { color: '#1a237e' } },
      { name: '回退次数', type: 'line', smooth: true, data: daily.map((d) => d.fallback), itemStyle: { color: '#ff9100' } },
      { name: '费用(¥)', type: 'line', smooth: true, yAxisIndex: 1, data: daily.map((d) => Number(d.cost.toFixed(4))), itemStyle: { color: '#00c853' } },
    ],
  });
}

function renderPie() {
  if (!pieEl.value || !stats.value) return;
  if (pieEmpty.value) {
    pieChart?.dispose();
    pieChart = null;
    return;
  }
  if (!pieChart) pieChart = echarts.init(pieEl.value);
  const items = stats.value.promptBreakdown;
  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 次（{d}%）' },
    legend: { type: 'scroll', bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['38%', '62%'],
      center: ['50%', '44%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{d}%' },
      data: items.map((p) => ({ name: p.promptKey, value: p.calls })),
    }],
  });
}

function onResize() {
  lineChart?.resize();
  pieChart?.resize();
}

async function loadStats() {
  loading.value = true;
  try {
    stats.value = await api.getLlmStats(days.value);
    await nextTick();
    renderLine();
    renderPie();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

async function loadCalls(page = 0) {
  callsLoading.value = true;
  try {
    const res = await api.listLlmCalls(page, callsSize);
    calls.value = res.items;
    callsTotal.value = res.total;
    callsPage.value = page;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    callsLoading.value = false;
  }
}

function onDaysChange() {
  loadStats();
}

function onCallsPageChange(p: number) {
  loadCalls(p - 1);
}

onMounted(() => {
  loadStats();
  loadCalls(0);
  window.addEventListener('resize', onResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize);
  lineChart?.dispose();
  lineChart = null;
  pieChart?.dispose();
  pieChart = null;
});
</script>

<template>
  <div v-loading="loading" class="page">
    <!-- 汇总 -->
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>DeepSeek 调用统计</span>
          <el-radio-group v-model="days" size="small" @change="onDaysChange">
            <el-radio-button value="7">近 7 天</el-radio-button>
            <el-radio-button value="30">近 30 天</el-radio-button>
            <el-radio-button value="90">近 90 天</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <template v-if="stats">
        <div class="stat-grid">
          <div class="stat-card"><div class="stat-num">{{ stats.totalCalls }}</div><div class="muted">总调用次数</div></div>
          <div class="stat-card"><div class="stat-num">¥{{ stats.totalCost.toFixed(4) }}</div><div class="muted">估算费用</div></div>
          <div class="stat-card"><div class="stat-num">{{ stats.totalInChars + stats.totalOutChars }}</div><div class="muted">输入/输出字符总量</div></div>
          <div class="stat-card"><div class="stat-num" :style="!fallbackOk ? 'color:#f56c6c' : ''">{{ stats.fallbackRate }}%</div><div class="muted">离线回退率</div></div>
          <div class="stat-card"><div class="stat-num">{{ stats.fallbackCalls }}</div><div class="muted">回退次数（共 {{ stats.days }} 天）</div></div>
        </div>
        <el-alert v-if="!fallbackOk" type="warning" :closable="false" style="margin-top: 10px" title="离线回退率偏高，请检查 DEEPSEEK_API_KEY 或网络（系统会自动回退离线引擎，不阻塞流程）。" />
      </template>
      <el-skeleton v-else :rows="3" animated />
    </el-card>

    <!-- 图表 -->
    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never">
          <template #header><b>每日调用趋势</b></template>
          <div v-if="!dailyEmpty" ref="lineEl" class="chart-box" />
          <el-empty v-else description="该时间窗内暂无调用数据" :image-size="72" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="9">
        <el-card shadow="never">
          <template #header><b>Prompt 分布</b></template>
          <div v-if="!pieEmpty" ref="pieEl" class="chart-box" />
          <el-empty v-else description="暂无调用数据" :image-size="72" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 调用明细 -->
    <el-card shadow="never" class="mt">
      <template #header><b>调用明细</b></template>
      <el-table v-loading="callsLoading" :data="calls" border stripe empty-text="暂无调用记录" size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="用户" width="110">
          <template #default="{ row }">{{ row.userId }}</template>
        </el-table-column>
        <el-table-column prop="promptKey" label="Prompt" width="150" show-overflow-tooltip />
        <el-table-column prop="model" label="模型" width="130" />
        <el-table-column label="输入字符" width="90">
          <template #default="{ row }">{{ row.inChars }}</template>
        </el-table-column>
        <el-table-column label="输出字符" width="90">
          <template #default="{ row }">{{ row.outChars }}</template>
        </el-table-column>
        <el-table-column label="费用(¥)" width="100">
          <template #default="{ row }">{{ row.estCost.toFixed(4) }}</template>
        </el-table-column>
        <el-table-column label="耗时(ms)" width="90">
          <template #default="{ row }">{{ row.latencyMs }}</template>
        </el-table-column>
        <el-table-column label="回退" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.fallback" size="small" type="warning">离线</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'success' ? 'success' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: flex-end; margin-top: 12px">
        <el-pagination
          v-if="callsTotal > callsSize"
          layout="prev, pager, next"
          :total="callsTotal"
          :page-size="callsSize"
          :current-page="callsPage + 1"
          background
          @current-change="onCallsPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.stat-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 12px; }
.stat-card { border: 1px solid #ebeef5; border-radius: 8px; padding: 14px; text-align: center; }
.stat-num { font-size: 22px; font-weight: 700; color: #1a237e; }
.chart-box { width: 100%; height: 360px; }
</style>
