import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

// 本地开发代理：/api → Spring Boot :8080（已开 CORS http://localhost:5173）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
});
