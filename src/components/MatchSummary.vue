<script setup lang="ts">
import type { MatchResult } from '../api';

defineProps<{ match: MatchResult }>();

function severityTag(sev: string): string {
  if (sev === 'HIGH') return 'danger';
  if (sev === 'MED') return 'warning';
  return 'info';
}
</script>

<template>
  <div class="match-summary">
    <el-space wrap class="mt">
      <el-tag type="primary" size="large">综合 {{ match.overall }}</el-tag>
      <el-tag>技能 {{ match.skill }}</el-tag>
      <el-tag>经验 {{ match.experience }}</el-tag>
      <el-tag>学历 {{ match.education }}</el-tag>
    </el-space>
    <p class="muted">{{ match.summary }}</p>
    <el-collapse class="mt">
      <el-collapse-item title="查看差距分析">
        <div v-for="(g, i) in match.gap" :key="i" class="muted" style="margin-bottom: 4px">
          <el-tag size="small" :type="severityTag(g.severity)">{{ g.severity }}</el-tag>
          {{ g.dimension }} · {{ g.item }}
        </div>
        <div v-if="!match.gap || !match.gap.length" class="muted">无明显差距</div>
      </el-collapse-item>
      <el-collapse-item title="查看面试重点（出题依据）">
        <div v-for="f in match.focus" :key="f.index" style="margin-bottom: 6px">
          <b>#{{ f.index }} {{ f.direction }}</b>
          <div class="muted">考察点：{{ f.examinePoint }}</div>
          <div class="muted">建议准备：{{ f.prepare }}</div>
        </div>
        <div v-if="!match.focus || !match.focus.length" class="muted">暂无面试重点</div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>
