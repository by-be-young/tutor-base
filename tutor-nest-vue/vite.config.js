// vite.config.js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 3000,
    open: true,
    // 本地开发：前端同源代理到 Spring 后端，避免 CORS / Cookie 问题。
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_API_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  // GitHub Pages 部署时通过 --base 参数覆盖
  base: process.env.BASE_URL || '/'
})