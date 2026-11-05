<script setup>
/**
 * 应用外壳：侧边栏 + 顶栏 + 内容区。
 * <p>
 * 侧边菜单来自 {@code GET /api/nav}，按 {@code group} 分组渲染，
 * 因此新增模型不需要改这里。移动端把侧边栏收进抽屉。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { features } from '../stores/features'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api/http'
import { auth, can, logout } from '../stores/auth'
import { brand, loadBrand } from '../stores/brand'
import { toastError } from '../api/toast'

const route = useRoute()
const router = useRouter()
const nav = ref([])
const loading = ref(true)
const open = ref(false)
const dark = ref(document.documentElement.dataset.theme === 'dark')

const groups = computed(() => {
  const order = ['业务数据', '系统管理', '智能助手']
  const map = new Map()
  for (const item of nav.value) {
    if (item.group === '智能助手' && !features.ai) continue
    if (!map.has(item.group)) map.set(item.group, [])
    map.get(item.group).push(item)
  }
  return order.filter((name) => map.has(name)).map((name) => ({ name, items: map.get(name) }))
})

const levelText = computed(() => {
  const level = auth.user?.level ?? 0
  if (level >= 4) return '超级管理员'
  if (level === 3) return '管理员'
  if (level === 2) return '指导教师'
  if (level === 1) return '普通账号'
  return '学生'
})

const scopeText = computed(() => {
  const scope = auth.user?.dataScope
  if (scope === 'ALL') return '全部数据'
  if (scope === 'DEPT') return '本部门'
  return '仅本人'
})

const title = computed(() => {
  if (route.name === 'model') {
    const item = nav.value.find((entry) => `m/${entry.key}` === route.path.replace(/^\//, ''))
    return item?.name || '数据'
  }
  return route.meta.title || '毕业论文管理系统'
})

async function loadNav() {
  try {
    nav.value = await api('/nav')
  } catch (error) {
    toastError(error)
  } finally {
    loading.value = false
  }
}

function toggleTheme() {
  dark.value = !dark.value
  document.documentElement.dataset.theme = dark.value ? 'dark' : 'light'
  localStorage.setItem('ims_theme', dark.value ? 'dark' : 'light')
}

// ------------------------------------------------------------------ 页面滚动条自动隐藏

/** 页面级滚动条「正在滚动」的定时器：停止滚动 0.9s 后摘掉类名。 */
let pageScrollTimer = null

/**
 * 滚动期间给 <html> 挂 is-scrolling，让页面滚动条显形，停止后自动隐藏。
 * 滚动条样式见 styles.css 的「页面滚动条自动隐藏」段。
 */
function onPageScroll() {
  document.documentElement.classList.add('is-scrolling')
  clearTimeout(pageScrollTimer)
  pageScrollTimer = setTimeout(
    () => document.documentElement.classList.remove('is-scrolling'),
    900
  )
}

async function signOut() {
  await logout()
  router.push('/login')
}

watch(() => features.ai, loadNav)

onMounted(() => {
  loadNav()
  loadBrand()
  window.addEventListener('scroll', onPageScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', onPageScroll)
  clearTimeout(pageScrollTimer)
})
</script>

<template>
  <div class="shell">
    <aside class="sidebar" :class="{ open }">
      <div class="brand">
        <img v-if="brand.logoUrl" :src="brand.logoUrl" class="logo logo-img" alt="logo" />
        <div v-else class="logo">论</div>
        <div>
          <div class="brand-name">{{ brand.siteName }}</div>
          <div class="brand-sub">选题 · 答辩 · 成绩</div>
        </div>
      </div>

      <nav class="nav">
        <router-link v-if="features.ai && can('ai:use')" to="/ai" class="nav-item" @click="open = false">
          <span class="dot">AI</span>
          <span class="grow">智能助手</span>
        </router-link>
        <router-link to="/dashboard" class="nav-item" @click="open = false">
          <span class="dot">台</span>
          <span class="grow">工作台</span>
        </router-link>

        <div v-for="group in groups" :key="group.name" class="group">
          <div class="group-title">{{ group.name }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.key"
            :to="`/m/${item.key}`"
            class="nav-item"
            @click="open = false"
          >
            <span class="dot">{{ item.name.slice(0, 1) }}</span>
            <span class="grow">{{ item.name }}</span>
          </router-link>
        </div>

        <div v-if="auth.user?.isAdmin" class="group">
          <div class="group-title">权限</div>
          <router-link to="/permissions" class="nav-item" @click="open = false">
            <span class="dot">权</span>
            <span class="grow">角色与权限</span>
          </router-link>
          <router-link
            v-if="can('system:setting:manage')"
            to="/settings"
            class="nav-item"
            @click="open = false"
          >
            <span class="dot">图</span>
            <span class="grow">图像设置</span>
          </router-link>
          <router-link v-if="(auth.user?.level ?? 0) >= 4" to="/log-settings" class="nav-item" @click="open = false">
            <span class="dot">志</span>
            <span class="grow">运行日志</span>
          </router-link>
          <router-link v-if="(auth.user?.level ?? 0) >= 4" to="/data-reset" class="nav-item" @click="open = false">
            <span class="dot">清</span>
            <span class="grow">清空系统数据</span>
          </router-link>
          <router-link v-if="features.aiAvailable" to="/ai-models" class="nav-item" @click="open = false">
            <span class="dot">AI</span>
            <span class="grow">AI 模型管理</span>
          </router-link>
        </div>

      </nav>

      <div class="side-foot">
        <div class="who">
          <img v-if="auth.user?.avatar" :src="auth.user.avatar" class="avatar avatar-img" alt="头像" />
          <div v-else class="avatar">{{ (auth.user?.name || '?').slice(0, 1) }}</div>
          <div class="grow">
            <div class="who-name">{{ auth.user?.name }}</div>
            <div class="who-role">{{ levelText }} · {{ scopeText }}</div>
          </div>
        </div>
        <router-link to="/profile" class="nav-item" @click="open = false">
          <span class="dot">我</span>
          <span class="grow">个人设置</span>
        </router-link>
        <button class="signout" @click="signOut">退出登录</button>
      </div>
    </aside>

    <div v-if="open" class="scrim" @click="open = false"></div>

    <main class="main">
      <header class="topbar">
        <button class="menu-btn" @click="open = !open">≡</button>
        <h1>{{ title }}</h1>
        <div class="grow"></div>
        <span class="badge grey">{{ scopeText }}</span>
        <button class="btn ghost" @click="toggleTheme">{{ dark ? '浅色' : '深色' }}</button>
      </header>

      <div class="content">
        <router-view v-if="!loading" />
        <div v-else class="empty">加载中…</div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
  background: var(--bg);
}

.sidebar {
  width: 238px;
  flex: none;
  background: var(--sidebar);
  color: var(--sidebar-text);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
  padding: 16px 12px;
  z-index: 50;
}

.brand {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 4px 8px 16px;
}
.logo {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #22d3ee);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 800;
}
.brand-name { color: #fff; font-weight: 650; font-size: 14px; }
.brand-sub { font-size: 11px; color: #7d879f; }
.logo-img { object-fit: cover; }

/* 菜单区可滚动但隐藏滚动条：视觉上保持干净，滚轮/触摸仍可滚动 */
.nav {
  flex: 1;
  overflow: auto;
  scrollbar-width: none;      /* Firefox */
  -ms-overflow-style: none;   /* 旧版 Edge/IE */
}
.nav::-webkit-scrollbar { display: none; }
.group { margin-top: 14px; }
.group-title {
  padding: 0 10px 6px;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: #6b7590;
  text-transform: uppercase;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 9px;
  color: var(--sidebar-text);
  font-size: 13.5px;
  margin-bottom: 2px;
}
.nav-item:hover { background: rgba(255, 255, 255, 0.06); color: #fff; }
.nav-item.router-link-active { background: var(--sidebar-active); color: #fff; }
.dot {
  width: 22px;
  height: 22px;
  flex: none;
  border-radius: 7px;
  background: rgba(255, 255, 255, 0.09);
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 700;
  color: #cbd3e6;
}
.nav-item.router-link-active .dot { background: var(--primary); color: #fff; }

.side-foot { border-top: 1px solid rgba(255, 255, 255, 0.07); padding-top: 10px; }
.who { display: flex; gap: 10px; align-items: center; padding: 6px 8px 12px; }
.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
}
.who-name { color: #eef1f8; font-size: 13px; }
.who-role { color: #7d879f; font-size: 11px; }
.avatar-img { object-fit: cover; }
.signout {
  width: 100%;
  margin-top: 6px;
  padding: 8px;
  border-radius: 9px;
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #aab2c6;
  cursor: pointer;
}
.signout:hover { color: #fff; border-color: rgba(255, 255, 255, 0.3); }

.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.topbar {
  height: 62px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 24px;
  background: var(--panel);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 30;
}
.topbar h1 { font-size: 16px; }
.menu-btn {
  display: none;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  border: 1px solid var(--border);
  background: var(--panel);
  cursor: pointer;
}
.content { padding: 22px 24px 40px; }

.scrim { display: none; }

@media (max-width: 900px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
  }
  .sidebar.open { transform: translateX(0); }
  .scrim {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.4);
    z-index: 40;
  }
  .menu-btn { display: block; }
  .content { padding: 16px 14px 30px; }
}
</style>
