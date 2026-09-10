import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';

// 后端统一信封：{ code, message, data }，code=0 表示成功。拦截器自动解包 data。
// token 存内存(sessionStorage)，不用 localStorage（SRS §5.2 敏感信息保护）。
const AUTH_KEYS = { access: 'accessToken', refresh: 'refreshToken', user: 'user' };

export const tokenStorage = {
  get access() { return sessionStorage.getItem(AUTH_KEYS.access) || ''; },
  get refresh() { return sessionStorage.getItem(AUTH_KEYS.refresh) || ''; },
  save(access: string, refresh: string) {
    sessionStorage.setItem(AUTH_KEYS.access, access);
    sessionStorage.setItem(AUTH_KEYS.refresh, refresh);
  },
  clear() {
    sessionStorage.removeItem(AUTH_KEYS.access);
    sessionStorage.removeItem(AUTH_KEYS.refresh);
    sessionStorage.removeItem(AUTH_KEYS.user);
  },
};

interface Envelope<T> { code: number; message: string; data: T }

export interface UserSummary { id: number; email: string; nickname?: string; role?: string }
export interface TokenResult { accessToken: string; refreshToken: string; user: UserSummary }

const http = axios.create({ baseURL: '/api', timeout: 60000 });

http.interceptors.request.use((cfg) => {
  const token = tokenStorage.access;
  if (token) cfg.headers.Authorization = `Bearer ${token}`;
  return cfg;
});

// ---------- 401 → 单次 refresh（轮换 refresh token） ----------
let refreshing: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const rt = tokenStorage.refresh;
  if (!rt) throw new Error('无有效刷新令牌');
  if (!refreshing) {
    refreshing = (async () => {
      const res = await axios.post<Envelope<TokenResult>>('/api/auth/refresh', { refreshToken: rt });
      const body = res.data;
      if (!body || body.code !== 0 || !body.data?.accessToken) {
        throw new Error(body?.message || '刷新令牌失败');
      }
      tokenStorage.save(body.data.accessToken, body.data.refreshToken);
      // 刷新同时返回的 user 不更新（登录态 user 仅登录写）；避免误覆盖角色
      return body.data.accessToken;
    })().finally(() => { refreshing = null; });
  }
  return refreshing;
}

http.interceptors.response.use(
  (resp) => {
    // blob / 文件流不包信封，直接返回
    if (resp.config.responseType === 'blob' || resp.data instanceof Blob) return resp.data as never;
    const body = resp.data as Envelope<unknown>;
    if (body.code !== 0) return Promise.reject(new Error(body.message || '请求失败'));
    return body.data as never;
  },
  async (error: AxiosError) => {
    const cfg = (error.config || {}) as InternalAxiosRequestConfig & { _retry?: boolean };
    const status = error.response?.status;
    const isAuthReq = !!cfg.url && (cfg.url.includes('/auth/login') || cfg.url.includes('/auth/register'));
    if (status === 401 && !cfg._retry && !isAuthReq) {
      cfg._retry = true;
      let token: string;
      try {
        token = await refreshAccessToken();
      } catch (refreshErr) {
        tokenStorage.clear();
        if (window.location.pathname !== '/login') window.location.assign('/login');
        return Promise.reject(new Error('登录已过期，请重新登录'));
      }
      cfg.headers.Authorization = `Bearer ${token}`;
      try {
        return await http(cfg);
      } catch (retryErr) {
        return Promise.reject(retryErr instanceof Error ? retryErr : new Error('请求失败'));
      }
    }
    const msg = (error.response?.data as { message?: string } | undefined)?.message
      || error.message || '网络错误';
    return Promise.reject(new Error(msg));
  }
);

/** 从受保护接口拉回文件 Blob（携带 Bearer；历史 JD/简历原文件预览用）。 */
function fetchBlob(url: string): Promise<Blob> {
  return http.get<never, Blob>(url, { responseType: 'blob' });
}

// ---------- 认证 ----------
export interface LoginResult { accessToken: string; refreshToken: string; user: UserSummary }
export const login = (email: string, password: string) =>
  http.post<never, LoginResult>('/auth/login', { email, password });
export const register = (email: string, password: string, verifyCode: string) =>
  http.post<never, UserSummary>('/auth/register', { email, password, verifyCode });
export const logout = () => http.post<never, unknown>('/auth/logout');

// ---------- 用户中心 ----------
export interface SettingsResponse {
  modelProvider: string; modelName: string; language: string;
  notifyEnabled: boolean; theme?: string | null;
}
export interface MeResponse {
  id: number; email: string; nickname?: string | null; avatarUrl?: string | null;
  role?: string; settings?: SettingsResponse | null;
}
export const getMe = () => http.get<never, MeResponse>('/user/me');
export const updateMe = (payload: { nickname: string; avatarUrl?: string }) =>
  http.put<never, MeResponse>('/user/me', payload);
export const changePassword = (oldPassword: string, newPassword: string) =>
  http.put<never, unknown>('/user/password', { oldPassword, newPassword });
export const updateSettings = (payload: {
  modelProvider: string; modelName: string; language: string; notifyEnabled: boolean;
}) => http.put<never, SettingsResponse>('/user/settings', payload);
export const getModels = () =>
  http.get<never, Array<{ id: string; name: string }>>('/settings/models');

/** 个人中心统计 */
export interface UserStatsResponse {
  interviewCount: number; formalCount: number; practiceCount: number;
  completedCount: number; wrongQuestionCount: number; weakSkillCount: number;
  averageScore: number; latestMode?: string | null; latestRating?: string | null;
}
export const getUserStats = () => http.get<never, UserStatsResponse>('/user/stats');

// ---------- 面试会话 ----------
export interface Interview {
  id: number; title: string; targetPosition: string; status: string;
  resumeId?: number | null; jdId?: number | null;
  questionCount: number; completedQuestionCount: number;
  createdAt?: string;
  mode?: 'formal' | 'practice' | null;
  weakBoost?: boolean;
  practiceCategory?: string | null;
  matchScore?: number | null; overallRating?: string | null;
}
export interface CreateInterviewPayload {
  title: string; targetPosition: string; questionCount: number;
  mode?: 'formal' | 'practice'; weakBoost?: boolean; practiceCategory?: string;
}
export const createInterview = (payload: CreateInterviewPayload) =>
  http.post<never, Interview>('/interviews', payload);
export const getInterview = (id: number) => http.get<never, Interview>(`/interviews/${id}`);
export const listInterviews = (status = 'all') =>
  http.get<never, Interview[]>('/interviews', { params: { status } });

// ---------- 简历（Interview 绑定 + 简历库共用同构结构） ----------
export interface ResumeParsed {
  name?: string; email?: string; phone?: string;
  education?: Array<{ school?: string; degree?: string; major?: string; period?: string }>;
  experience?: Array<{ company?: string; title?: string; period?: string; summary?: string; tech?: string[] }>;
  projects?: Array<{ name?: string; period?: string; description?: string; role?: string; tech?: string[] }>;
  skills?: Array<{ name?: string; level?: string }>;
}
export interface ResumeRecord {
  id: number; fileName: string; status: string;
  parsedData?: ResumeParsed | null; createdAt?: string;
}
export const uploadResume = (id: number, file: File) => {
  const form = new FormData();
  form.append('file', file);
  return http.post<never, ResumeRecord>(`/interviews/${id}/resume`, form);
};
export const getResume = (id: number) => http.get<never, ResumeRecord>(`/interviews/${id}/resume`);
export const confirmResume = (id: number) => http.put<never, ResumeRecord>(`/interviews/${id}/resume/confirm`);
// 简历库（仅解析完成可复用）
export const listResumes = () => http.get<never, ResumeRecord[]>('/resumes');
export const getResumeDetail = (id: number) => http.get<never, ResumeRecord>(`/resumes/${id}`);
export const reuseResume = (interviewId: number, resumeId: number) =>
  http.post<never, ResumeRecord>(`/interviews/${interviewId}/resume/reuse`, { resumeId });
export const getResumeFileBlob = (resumeId: number) => fetchBlob(`/resumes/${resumeId}/file`);

// ---------- JD（文本 / 文件 / 历史库） ----------
export interface JdParsed {
  hardSkills?: string[]; softSkills?: string[]; minExperienceYears?: number | null;
  educationRequirement?: string | null; responsibilities?: string[];
}
export interface JdRecord {
  id: number; fileName?: string | null; status: string;
  parsedData?: JdParsed | null; createdAt?: string;
}
export const submitJdText = (id: number, rawText: string) =>
  http.post<never, JdRecord>(`/interviews/${id}/jd/text`, { rawText });
export const uploadJdFile = (id: number, file: File) => {
  const form = new FormData();
  form.append('file', file);
  return http.post<never, JdRecord>(`/interviews/${id}/jd`, form);
};
export const getJd = (id: number) => http.get<never, JdRecord>(`/interviews/${id}/jd`);
// 历史 JD 库
export interface JdLibraryItem {
  id: number; type: string; fileName?: string | null; status: string;
  textPreview?: string | null; createdAt?: string;
}
export interface JdDetailView {
  id: number; type: string; fileName?: string | null; status: string;
  rawText?: string | null; createdAt?: string;
}
export const listJdLibrary = () => http.get<never, JdLibraryItem[]>('/jds');
export const getJdDetail = (id: number) => http.get<never, JdDetailView>(`/jds/${id}`);
export const reuseJd = (interviewId: number, jdId: number) =>
  http.post<never, JdRecord>(`/interviews/${interviewId}/jd/reuse`, { jdId });
export const getJdFileBlob = (jdId: number) => fetchBlob(`/jds/${jdId}/file`);

// ---------- 匹配 ----------
export interface MatchGap { dimension: string; item: string; severity: string; evidence?: string }
export interface MatchFocus { index: number; direction: string; examinePoint: string; prepare: string }
export interface MatchResult {
  overall: number; skill: number; experience: number; education: number;
  gap: MatchGap[]; focus: MatchFocus[]; summary: string;
}
export const runMatch = (id: number) => http.post<never, MatchResult>(`/interviews/${id}/match`);
export const getMatch = (id: number) => http.get<never, MatchResult>(`/interviews/${id}/match`);
export const regenerateFocus = (id: number) =>
  http.post<never, MatchFocus[]>(`/interviews/${id}/focus/regenerate`);

// ---------- 面试引擎 ----------
export interface QuestionSummary { orderIndex: number; focusIndex: number; content: string }
export interface SessionResponse {
  status: string; questionCount: number; completedCount: number;
  current?: QuestionSummary | null;
}
export const startSession = (id: number) => http.post<never, SessionResponse>(`/interviews/${id}/start`);
export const getSession = (id: number) => http.get<never, SessionResponse>(`/interviews/${id}/session`);

export interface AnswerResponse {
  orderIndex: number; score: number; strong: string[]; weak: string[];
  suggestion: string; suggestFollowup: boolean;
  followUp?: { content: string } | null;
  nextQuestion?: QuestionSummary | null;
  interviewStatus: string; finished: boolean;
  resumeAdvice?: string | null;
  mode?: 'formal' | 'practice' | null;
  referenceAnswer?: string | null;
  hint?: string | null;
}
export const submitAnswer = (id: number, orderIndex: number, answer: string, elapsedSeconds?: number) =>
  http.post<never, AnswerResponse>(`/interviews/${id}/questions/${orderIndex}/answer`, {
    answer, elapsedSeconds: elapsedSeconds ?? null,
  });
export interface ReferenceView {
  mode?: string; answered: boolean; hint?: string | null; referenceAnswer?: string | null;
}
export const getReference = (id: number, orderIndex: number) =>
  http.get<never, ReferenceView>(`/interviews/${id}/questions/${orderIndex}/reference`);
export interface FollowUpResult { score: number; suggestion: string }
export const submitFollowUp = (id: number, orderIndex: number, answer: string) =>
  http.post<never, FollowUpResult>(`/interviews/${id}/followup/${orderIndex}/answer`, { answer });
export const skipFollowUp = (id: number, orderIndex: number) =>
  http.post<never, unknown>(`/interviews/${id}/questions/${orderIndex}/skip`);

// ---------- 题库（浏览 / 管理） ----------
export interface BankCategoryCount { category: string; count: number }
export interface BankQuestionView {
  id: number; category: string; questionType: string; difficulty: number;
  content: string; knowledgePoints?: string[] | null;
}
/** 后端通用分页（题库/错题本/后台列表均为 { items, total }，page 从 0 起）。 */
export interface ItemPage<T> { items: T[]; total: number }
export const listBankCategories = () => http.get<never, BankCategoryCount[]>('/bank/categories');
export const listBankQuestions = (params: {
  category?: string; keyword?: string; page?: number; size?: number;
}) => http.get<never, ItemPage<BankQuestionView>>('/bank/questions', { params });

export interface BankAdminView {
  id: number; category: string; questionType: string; difficulty: number;
  content: string; answer?: string | null; hint?: string | null;
  knowledgePoints?: string[] | null; sourceAi: boolean; enabled: boolean;
  usageCount: number; updatedAt?: string;
}
export interface BankUpsertPayload {
  category: string; questionType: string; difficulty: number; content: string;
  answer?: string; hint?: string; knowledgePoints?: string[];
}
export const adminBankPage = (params: {
  category?: string; keyword?: string; page?: number; size?: number;
}) => http.get<never, ItemPage<BankAdminView>>('/admin/bank/questions', { params });
export const adminBankCreate = (payload: BankUpsertPayload) =>
  http.post<never, BankAdminView>('/admin/bank/questions', payload);
export const adminBankUpdate = (id: number, payload: BankUpsertPayload) =>
  http.put<never, BankAdminView>(`/admin/bank/questions/${id}`, payload);
export const adminBankDelete = (id: number) =>
  http.delete<never, unknown>(`/admin/bank/questions/${id}`);
export const adminBankGenerate = (payload: { category: string; count: number; difficulty: number }) =>
  http.post<never, { inserted: number }>('/admin/bank/generate', payload);

// ---------- 薄弱技能库 / 错题本 ----------
export interface WeakSkillView {
  id: number; skillTag: string; category: string; severity: number;
  latestScore?: number | null; interviewCount: number; lastSeen?: string;
  status: string; evidence?: string[] | null;
}
export const listWeakSkills = () => http.get<never, WeakSkillView[]>('/weak-skills');
export const resolveWeakSkill = (id: number) =>
  http.post<never, unknown>(`/weak-skills/${id}/resolve`);

export interface WrongQuestionView {
  interviewId: number; title: string; mode?: string | null; orderIndex: number;
  questionBankId?: number | null; category?: string | null; question: string;
  myAnswer?: string | null; score: number; referenceAnswer?: string | null; hint?: string | null;
}
export const listWrongQuestions = (page = 0, size = 10) =>
  http.get<never, ItemPage<WrongQuestionView>>('/weak/wrong-questions', { params: { page, size } });

// ---------- 用户反馈 ----------
export type FeedbackCategory = 'suggestion' | 'bug' | 'question' | 'praise' | 'other';
export interface FeedbackView {
  id: number; userId: number; userEmail?: string | null; category: string;
  content: string; contact?: string | null; status: string;
  reply?: string | null; repliedAt?: string | null; createdAt?: string;
}
export const submitFeedback = (payload: { category: FeedbackCategory; content: string; contact?: string }) =>
  http.post<never, FeedbackView>('/feedback', payload);
export const listMyFeedback = () => http.get<never, FeedbackView[]>('/feedback');
export const adminFeedbackPage = (params: { status?: string; page?: number; size?: number }) =>
  http.get<never, ItemPage<FeedbackView>>('/admin/feedback', { params });
export const adminFeedbackReply = (id: number, payload: { status?: string; reply?: string }) =>
  http.put<never, FeedbackView>(`/admin/feedback/${id}`, payload);

// ---------- 简历对比 ----------
export interface CompareResumeRequest { resumeAId: number; resumeBId: number; targetPosition?: string }
export interface CompareResult {
  resumeASummary?: string; resumeBSummary?: string;
  fields: Array<{ field: string; aValue?: string; bValue?: string; note?: string }>;
  skillOverlap: string[]; onlyA: string[]; onlyB: string[];
  strengthsA: string[]; strengthsB: string[];
  differenceSummary?: string; advice: string[];
}
export interface CompareView {
  id: number; resumeAId: number; resumeBId: number; title: string;
  result?: CompareResult | null; createdAt?: string;
}
export interface CompareListItem {
  id: number; resumeAId: number; resumeBId: number; title: string;
  differenceSummary?: string; createdAt?: string;
}
export const runCompare = (payload: CompareResumeRequest) =>
  http.post<never, CompareView>('/compare/resumes', payload);
export const listCompareHistory = () => http.get<never, CompareListItem[]>('/compare/list');
export const getCompareDetail = (id: number) => http.get<never, CompareView>(`/compare/${id}`);
export const deleteCompare = (id: number) => http.delete<never, unknown>(`/compare/${id}`);

// ---------- 报告 / 历史 ----------
export interface ReportQuestion {
  orderIndex: number; question: string; answer?: string | null; score?: number | null;
  strong?: string[] | null; weak?: string[] | null; suggestion?: string | null;
  resumeAdvice?: string | null;
  referenceAnswer?: string | null; hint?: string | null;
  followUp?: { question?: string; answer?: string | null; score?: number | null } | null;
}
export interface DimensionAnalysis { dimension: string; score: number; analysis: string; advice: string }
export interface ReportDeepAnalysis {
  overallAnalysis?: string | null;
  dimensionAnalyses?: DimensionAnalysis[] | null;
  conclusion?: string | null;
}
export interface ResumeAdviceGroup { title: string; priority: 'HIGH' | 'MED' | 'LOW'; items: string[] }
export interface ResumeAdviceGroupList { intro?: string; groups?: ResumeAdviceGroup[] }
export interface ReportResponse {
  rating?: string | null; mode?: 'formal' | 'practice' | null;
  matchScore?: number | null;
  dimensions: Record<string, number>;
  questions: ReportQuestion[];
  strengths: string[]; improvements: string[];
  recommendation?: string | null;
  analysis?: ReportDeepAnalysis | null;
  resumeAdvice?: ResumeAdviceGroupList | null;
}
export const getReport = (id: number) => http.get<never, ReportResponse>(`/interviews/${id}/report`);

export interface HistoryItem {
  id: number; title: string; targetPosition: string; status: string;
  questionCount: number; completedQuestionCount: number;
  matchScore?: number | null; overallRating?: string | null;
  mode?: 'formal' | 'practice' | null; weakBoost?: boolean;
  createdAt?: string; completedAt?: string | null; hasReport: boolean;
}
export interface Paged<T> { list: T[]; total: number; page: number; size: number }
export const listHistory = (page = 1, size = 10) =>
  http.get<never, Paged<HistoryItem>>('/history', { params: { page, size } });
export const getHistoryDetail = (id: number) => http.get<never, ReportResponse>(`/history/${id}`);
export const deleteHistory = (id: number) => http.delete<never, unknown>(`/history/${id}`);
export const clearHistory = () => http.delete<never, unknown>('/history');

// ---------- 管理端：LLM 统计 / 用户 ----------
export interface LlmPromptStat {
  promptKey: string; calls: number; inChars: number; outChars: number;
  fallback: number; cost: number;
}
export interface LlmDailyStat {
  date: string; calls: number; inChars: number; outChars: number;
  fallback: number; cost: number;
  prompts: Array<{ promptKey: string; calls: number; cost: number; fallback: number }>;
}
export interface LlmStatsResponse {
  since: string; days: number;
  totalCalls: number; totalInChars: number; totalOutChars: number;
  totalCost: number; fallbackCalls: number; fallbackRate: number;
  promptBreakdown: LlmPromptStat[];
  daily: LlmDailyStat[];
}
export const getLlmStats = (days = 7) =>
  http.get<never, LlmStatsResponse>('/admin/llm/stats', { params: { days } });
export interface LlmCallView {
  id: number; userId: number; promptKey: string; model: string;
  inChars: number; outChars: number; estCost: number; latencyMs: number;
  fallback: boolean; status: string; createdAt?: string;
}
export const listLlmCalls = (page = 0, size = 20) =>
  http.get<never, ItemPage<LlmCallView>>('/admin/llm/calls', { params: { page, size } });

export interface AdminUserView {
  id: number; email: string; nickname?: string | null; role: string;
  active: boolean; createdAt?: string; lastLoginAt?: string | null;
}
/** 用户管理/黑名单分页列表：keyword 模糊搜索邮箱昵称；role 可选 user/admin；active 可选 true(启用)/false(停用)。 */
export const listAdminUsers = (params: {
  keyword?: string; role?: string; active?: boolean; page?: number; size?: number;
}) => http.get<never, ItemPage<AdminUserView>>('/admin/users', { params });

// ---------- 管理端：账号（停用/黑名单 + 新增管理员） ----------
/** 某用户的面试列表行（管理端只读视角）。 */
export interface AdminInterviewView {
  id: number; title: string; targetPosition: string; status: string;
  mode?: 'formal' | 'practice' | null;
  questionCount: number; completedQuestionCount: number;
  matchScore?: number | null; overallRating?: string | null;
  startedAt?: string | null; completedAt?: string | null;
  createdAt?: string; hasReport: boolean;
}
export const adminUserInterviews = (userId: number) =>
  http.get<never, AdminInterviewView[]>(`/admin/users/${userId}/interviews`);
/** 只读查看某用户某场面试的报告（结构与用户侧 ReportResponse 一致）。 */
export const adminUserInterviewReport = (userId: number, interviewId: number) =>
  http.get<never, ReportResponse>(`/admin/users/${userId}/interviews/${interviewId}/report`);
/** 停用(false=拉黑)/启用(true)账号；后端守卫：不能停用自己、不能停用最后一位启用管理员。 */
export const adminSetActive = (id: number, active: boolean) =>
  http.put<never, AdminUserView>(`/admin/accounts/${id}/active`, { active });
export const adminCreateAdmin = (payload: { email: string; password: string; nickname?: string }) =>
  http.post<never, AdminUserView>('/admin/accounts/admins', payload);

// ---------- 管理端：后台概览 ----------
/** 后台概览卡片统计（注册用户/管理员/停用/面试/反馈/题库等）。 */
export interface AdminOverviewStats {
  userCount: number; adminCount: number; disabledUserCount: number;
  interviewCount: number; completedInterviewCount: number;
  inProgressInterviewCount: number; pendingFeedbackCount: number;
  questionCount: number;
}
export const getAdminOverview = () =>
  http.get<never, AdminOverviewStats>('/admin/stats/overview');
