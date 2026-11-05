<script setup>
/**
 * 工作台。
 * <p>
 * 数据来自 {@code GET /api/stats}：统计卡片 + 最近审计。
 * 所有计数都已按当前用户的数据范围过滤，因此不同账号看到的数字不同。
 * 卡片由模型注册表推导，新增 {@code @ModelClass} 会自动多一张卡。
 */
import { features } from '../stores/features'
import { can } from '../stores/auth'
import { onMounted, ref } from 'vue'
import { api } from '../api/http'
import { auth } from '../stores/auth'

const data = ref(null)
const error = ref('')

const actionLabels = {
  CREATE: '新增',
  UPDATE: '修改',
  DELETE: '删除',
  LOGIN: '登录'
}

function greeting() {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
}

function scopeText() {
  const scope = auth.user?.dataScope
  if (scope === 'ALL') return '全部数据'
  if (scope === 'DEPT') return '本部门数据'
  return '仅本人数据'
}

onMounted(async () => {
  try {
    data.value = await api('/stats')
  } catch (e) {
    error.value = e.message
  }
})
</script>

<template>
  <div class="dash">
    <div class="hello">
      <div>
        <h2>{{ greeting() }}，{{ auth.user?.name }}</h2>
        <p class="muted small">
          当前权限等级 {{ auth.user?.level }} · 数据范围 {{ scopeText() }}
        </p>
      </div>
      <router-link v-if="auth.user?.isAdmin" to="/permissions" class="btn">权限矩阵</router-link>
    </div>

    <div v-if="error" class="card error-card">{{ error }}</div>

    <template v-else-if="data">
      <div class="cards">
        <router-link v-for="card in data.cards" :key="card.key" :to="`/m/${card.key}`" class="card stat">
          <div class="stat-top">
            <span class="stat-name">{{ card.name }}</span>
            <span class="arrow">→</span>
          </div>
          <div class="stat-value">{{ card.count }}</div>
          <div class="muted small">可访问记录</div>
        </router-link>
      </div>

      <div class="card panel">
        <div class="panel-head">
          <h3>最近操作</h3>
          <span class="muted small">仅管理员可见</span>
        </div>
        <ul v-if="data.recent.length" class="audit">
          <li v-for="item in data.recent" :key="item.id">
            <span class="badge grey">{{ actionLabels[item.action] || item.action }}</span>
            <span class="grow">
              <b>{{ item.model }}</b>
              <span class="muted small"> · {{ item.detail }}</span>
            </span>
            <span class="muted small">{{ item.actor }} {{ item.time }}</span>
          </li>
        </ul>
        <div v-else class="empty">暂无记录或无权查看</div>
        <router-link v-if="auth.user?.isAdmin" to="/m/auditLog" class="panel-foot">全部审计日志 →</router-link>
      </div>

      <div class="card quick">
        <div v-if="features.ai && can('ai:use')" class="quick-item">
          <div>
            <b>用一句话维护数据</b>
            <div class="muted small">智能助手可以查询、新增、修改数据，删除前会向你确认。</div>
          </div>
          <router-link to="/ai" class="btn primary">打开助手</router-link>
        </div>
        <div class="quick-item">
          <div>
            <b>表格由模型定义驱动</b>
            <div class="muted small">改模型类 → 重启 → 表格、表单、搜索自动跟着变。</div>
          </div>
          <router-link to="/m/paper" class="btn">看看论文表</router-link>
        </div>
      </div>
    </template>

    <div v-else class="empty">加载中…</div>
  </div>
</template>

<style scoped>
.hello { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.hello h2 { font-size: 22px; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 14px; margin-bottom: 16px; }
.stat { padding: 16px 18px; color: var(--text); transition: 0.15s ease; }
.stat:hover { transform: translateY(-2px); border-color: var(--primary); }
.stat-top { display: flex; justify-content: space-between; color: var(--muted); font-size: 13px; }
.arrow { opacity: 0.4; }
.stat-value { font-size: 30px; font-weight: 700; margin: 6px 0 2px; letter-spacing: -0.02em; }

.panel { padding: 16px 18px; display: flex; flex-direction: column; margin-bottom: 14px; }
.panel-head { display: flex; flex-wrap: wrap; gap: 6px; align-items: baseline; justify-content: space-between; margin-bottom: 12px; }
.panel-head h3 { font-size: 15px; }
.panel-foot { margin-top: auto; padding-top: 12px; font-size: 13px; }

.audit { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
.audit li { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.audit li .grow { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.quick { margin-top: 14px; padding: 6px 18px; }
.quick-item { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 0; border-bottom: 1px solid var(--border); }
.quick-item:last-child { border-bottom: none; }

.error-card { padding: 18px; color: var(--danger); background: var(--danger-weak); border-color: transparent; }
</style>
