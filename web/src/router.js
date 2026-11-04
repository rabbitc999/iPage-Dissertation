/**
 * 路由表与登录守卫。
 * <p>
 * 采用 hash 模式（{@code /#/m/employee}）：前端产物直接放进 Spring Boot 的
 * static 目录即可，不需要服务端配置 SPA 回退，也不会出现刷新 404。
 * <p>
 * 守卫只判断"有没有令牌"，不判断细粒度权限——权限由服务端在 schema 与接口里判定，
 * 前端隐藏按钮只是体验优化，不是安全边界。
 */
import { createRouter, createWebHashHistory } from 'vue-router'
import { auth, can, loadMe } from './stores/auth'
import { features, loadFeatures } from './stores/features'

const routes = [
  { path: '/login', name: 'login', component: () => import('./views/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('./components/AppLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', name: 'dashboard', component: () => import('./views/DashboardView.vue'), meta: { title: '工作台' } },
      { path: 'm/:key', name: 'model', component: () => import('./views/ModelView.vue'), meta: { title: '数据' } },
      { path: 'permissions', name: 'permissions', component: () => import('./views/PermissionsView.vue'), meta: { title: '角色与权限' } },
      { path: 'settings', name: 'settings', component: () => import('./views/SettingsView.vue'), meta: { title: '图像设置' } },
      { path: 'log-settings', name: 'log-settings', component: () => import('./views/LogSettingsView.vue'), meta: { title: '运行日志' } },
      { path: 'data-reset', name: 'data-reset', component: () => import('./views/DataResetView.vue'), meta: { title: '清空系统数据' } },
      { path: 'ai-models', name: 'ai-models', component: () => import('./views/AiModelsView.vue'), meta: { title: 'AI 模型管理' } },
      { path: 'ai', name: 'ai', component: () => import('./views/AiView.vue'), meta: { title: 'AI 助手' } },
      { path: 'profile', name: 'profile', component: () => import('./views/ProfileView.vue'), meta: { title: '个人设置' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  // 用 hash 路由：前端构建产物可以直接丢进 Spring Boot 的 static 目录，不需要服务端做 SPA 回退
  history: createWebHashHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || (to.path !== from.path ? { top: 0 } : undefined)
  }
})

router.beforeEach(async (to) => {
  if (!auth.ready) {
    await loadMe()
  }
  if (to.meta.public) {
    return auth.token ? '/dashboard' : true
  }
  if (!auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if ((to.name === 'data-reset' || to.name === 'log-settings') && (auth.user?.level ?? 0) < 4) return '/dashboard'
  await loadFeatures()
  if (to.name === 'ai' && (!features.ai || !can('ai:use'))) return '/dashboard'
  if (to.name === 'ai-models' && (!features.aiAvailable || !auth.user?.isAdmin)) return '/dashboard'
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 信息管理系统` : '信息管理系统'
})

export default router
