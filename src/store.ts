import { defineStore } from 'pinia';
import { login as apiLogin, logout as apiLogout, tokenStorage, type LoginResult, type UserSummary } from './api';

interface AuthState {
  token: string;
  refreshToken: string;
  user: UserSummary | null;
}

function readStoredUser(): UserSummary | null {
  try {
    const raw = sessionStorage.getItem('user');
    return raw ? (JSON.parse(raw) as UserSummary) : null;
  } catch {
    return null;
  }
}

// token 存内存(sessionStorage)；退出清空。见 docs/08 §3 与 SRS §5.2。
export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: tokenStorage.access,
    refreshToken: tokenStorage.refresh,
    user: readStoredUser(),
  }),
  getters: {
    loggedIn: (s) => !!s.token,
    isAdmin: (s) => s.user?.role === 'admin',
  },
  actions: {
    async login(email: string, password: string) {
      const res: LoginResult = await apiLogin(email, password);
      this.token = res.accessToken;
      this.refreshToken = res.refreshToken;
      this.user = res.user;
      tokenStorage.save(res.accessToken, res.refreshToken);
      sessionStorage.setItem('user', JSON.stringify(res.user));
    },
    async logout() {
      try { await apiLogout(); } catch { /* 登出接口失败也继续本地清理 */ }
      this.token = '';
      this.refreshToken = '';
      this.user = null;
      tokenStorage.clear();
    },
    setUser(user: UserSummary) {
      this.user = user;
      if (user) sessionStorage.setItem('user', JSON.stringify(user));
    },
  },
});
