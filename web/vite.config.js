import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 开发期把 /api 转给 Spring Boot，前端不需要处理跨域与端口
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    // 构建产物直接可复制到 server/src/main/resources/static
    assetsDir: 'assets'
  }
})
