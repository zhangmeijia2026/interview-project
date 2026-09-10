// 各页面共用的中文映射与格式化工具（不涉及组件与网络）。

/** 面试状态 → 中文（页面可读文案）。 */
export function statusText(s?: string | null): string {
  const map: Record<string, string> = {
    draft: '草稿', resume_uploaded: '简历已就绪', jd_uploaded: '岗位已就绪',
    matched: '匹配完成', ready: '待开始', in_progress: '面试中',
    completed: '已完成', abandoned: '已放弃',
  };
  return map[s || ''] || (s || '-');
}

/** 面试状态 → 徽标色（Element Plus tag type）。 */
export function statusTagType(s?: string | null): string {
  if (s === 'completed') return 'success';
  if (s === 'in_progress') return 'primary';
  if (s === 'abandoned') return 'danger';
  return 'info';
}

/** 评级字母 → 中文。 */
export function ratingText(r?: string | null): string {
  const map: Record<string, string> = { S: '卓越', A: '优秀', B: '良好', C: '待加强', D: '薄弱' };
  return map[r || ''] || (r || '-');
}

/** 评级字母 → 徽标色。 */
export function ratingTagType(r?: string | null): string {
  if (r === 'S' || r === 'A') return 'success';
  if (r === 'B') return 'primary';
  if (r === 'C') return 'warning';
  return 'danger';
}

/** 六维维度 key → 中文标签。 */
export const DIMENSION_LABELS: Record<string, string> = {
  job_match: '岗位匹配', professional: '专业技能', expression: '表达流畅',
  logic: '逻辑条理', adaptability: '应变追问', learning: '学习改进',
};

/** 维度 key 或已是中文时均返回可读中文（未知透传）。 */
export function labelOf(key: string): string {
  return DIMENSION_LABELS[key] || key;
}

/** ISO 时间 → 本地展示（秒级截断，兼容 YYYY-MM-DDTHH:mm 与 Date 序列化）。 */
export function fmtTime(s?: string | null): string {
  if (!s) return '-';
  const t = s.replace('T', ' ');
  return t.length > 16 ? t.slice(0, 16) : t;
}

/** 简历是否已经准备完成（draft/resume_uploaded/jd_uploaded 为未完成态）。 */
export function setupDone(status?: string | null): boolean {
  return !!status && ['matched', 'ready', 'in_progress', 'completed'].includes(status);
}

/** 从任意对象（JsonNode/ResumeParsed）安全取字符串字段。 */
export function pickString(obj: unknown, key: string): string {
  if (obj && typeof obj === 'object') {
    const v = (obj as Record<string, unknown>)[key];
    if (typeof v === 'string') return v;
  }
  return '';
}

/** 面试模式 → 中文。 */
export function modeText(mode?: string | null): string {
  if (mode === 'formal') return '正式';
  if (mode === 'practice') return '练习';
  return '正式';
}

/** 面试模式 → 徽标色。 */
export function modeTagType(mode?: string | null): string {
  return mode === 'practice' ? 'warning' : 'primary';
}

/** 难度(1~5) → 中文。 */
export function difficultyText(d: number): string {
  const map: Record<number, string> = { 1: '入门', 2: '基础', 3: '进阶', 4: '较难', 5: '困难' };
  return map[d] || String(d);
}

/** 难度 → 徽标色。 */
export function difficultyTag(d: number): string {
  if (d >= 4) return 'danger';
  if (d === 3) return 'warning';
  return 'info';
}

/** 题库题目类型中文（后端 questionType 可能为 java/sql/algorithm/…，展示友好化）。 */
export function questionTypeText(t?: string | null): string {
  const map: Record<string, string> = {
    qa: '问答题',
    java: 'Java', sql: '数据库', algorithm: '算法', network: '网络', os: '操作系统',
    spring: '框架', project: '项目', behavior: '行为', fundamental: '基础',
    concurrent: '并发', middleware: '中间件',
  };
  return map[t || ''] || t || '综合';
}

/** 反馈分类中文。 */
export function feedbackCategoryText(c?: string | null): string {
  const map: Record<string, string> = {
    suggestion: '建议', bug: '缺陷', question: '咨询', praise: '表扬', other: '其他',
  };
  return map[c || ''] || c || '其他';
}

/** 反馈状态中文。 */
export function feedbackStatusText(s?: string | null): string {
  const map: Record<string, string> = { new: '待处理', processing: '处理中', done: '已处理' };
  return map[s || ''] || s || '待处理';
}
