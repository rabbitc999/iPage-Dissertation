<script setup>
/**
 * 根组件。
 * <p>
 * 只做两件事：渲染当前路由页面、挂载全局 toast 容器。
 * 布局（侧边栏/顶栏）在 AppLayout.vue 里，登录页不套布局。
 */
import { toasts } from './api/toast'
</script>

<template>
  <router-view />

  <div class="toasts">
    <div v-for="item in toasts" :key="item.id" class="toast" :class="item.type">
      {{ item.message }}
    </div>
  </div>
</template>
