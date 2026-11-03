/**
 * 前端入口。
 * <p>
 * 启动顺序：恢复主题 → 创建应用 → 挂路由 → 挂载。
 * 主题在挂载**之前**恢复，避免深色模式下先闪一下白屏。
 */
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles.css'

const saved = localStorage.getItem('ims_theme')
if (saved === 'dark' || (!saved && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
  document.documentElement.dataset.theme = 'dark'
}

createApp(App).use(router).mount('#app')
