import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 前端 /api → 网关 8080，网关再按 /api/{module} 转发
      // 使用 127.0.0.1 而非 localhost：Windows 下 localhost 会优先解析 IPv6 ::1，
      // 而 WSL 的 wslrelay.exe 可能在 [::1]:8080 转发到 Ubuntu 内服务，导致请求不进 Docker
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
