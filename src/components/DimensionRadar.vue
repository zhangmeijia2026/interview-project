<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as echarts from 'echarts';
import { labelOf } from '../utils';

const props = withDefaults(defineProps<{ dimensions?: Record<string, number> | null }>(), {
  dimensions: null,
});

const el = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;

function hasData(): boolean {
  return !!props.dimensions && Object.keys(props.dimensions).length > 0;
}

function render() {
  if (!el.value) return;
  if (!hasData()) {
    chart?.dispose();
    chart = null;
    return;
  }
  if (!chart) chart = echarts.init(el.value);
  const dims = props.dimensions!;
  const keys = Object.keys(dims);
  chart.setOption({
    radar: { indicator: keys.map((k) => ({ name: labelOf(k), max: 100 })) },
    series: [{
      type: 'radar',
      data: [{
        value: keys.map((k) => dims[k]),
        name: '综合能力',
        areaStyle: { color: 'rgba(26, 35, 126, 0.25)' },
        lineStyle: { color: '#1a237e' },
      }],
    }],
    tooltip: {},
  });
}

function onResize() { chart?.resize(); }

onMounted(() => {
  render();
  window.addEventListener('resize', onResize);
});
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize);
  chart?.dispose();
  chart = null;
});
watch(() => props.dimensions, () => nextTick(render), { deep: true });
</script>

<template>
  <div>
    <div v-if="hasData" ref="el" class="radar-box" />
    <el-empty v-else description="暂无维度数据" :image-size="72" />
  </div>
</template>

<style scoped>
.radar-box { width: 100%; height: 320px; }
</style>
