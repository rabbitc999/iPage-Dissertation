<script setup>
/**
 * 工作台。
 * <p>
 * 数据来自 {@code GET /api/stats}：统计卡、工单状态分布、最近审计。
 * 所有计数都已按当前用户的数据范围过滤，因此不同账号看到的数字不同。
 */
import { features } from '../stores/features'
import { can } from '../stores/auth'
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api/http'
import { auth } from '../stores/auth'
import { toastError } from '../api/toast'

const data = ref(null)
const error = ref('')
const reportError = ref('')
const loadingReport = ref(false)
const exporting = ref(false)
const draft = reactive({ from: '', to: '', department: '' })
const applied = ref({})

const statusLabels = {
  OPEN: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已解决',
  CLOSED: '已关闭'
}

const ticketRows = computed(() => {
  const stats = data.value?.tickets || {}
  const entries = Object.entries(stats)
  const max = Math.max(1, ...entries.map(([, value]) => Number(value) || 0))
  return entries.map(([key, value]) => ({
    key,
    label: statusLabels[key] || key,
    value: Number(value) || 0,
    width: `${Math.round(((Number(value) || 0) / max) * 100)}%`
  }))
})

const totalTickets = computed(() => data.value?.ticketTotal ?? 0)

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

async function applyFilters() {
  loadingReport.value = true
  reportError.value = ''
  const params = { ...draft }
  try {
    data.value = await api('/stats', { params })
    applied.value = params
  } catch (e) {
    reportError.value = e.message
  } finally {
    loadingReport.value = false
  }
}

async function downloadCsv() {
  exporting.value = true
  reportError.value = ''
  try {
    const blob = await api('/stats/tickets.csv', { params: applied.value, responseType: 'blob' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '工单报表.csv'
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (e) {
    reportError.value = e.message
  } finally {
    exporting.value = false
  }
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

      <div class="split">
        <div v-if="data.tickets" class="card panel">
          <div class="panel-head">
            <h3>工单报表</h3>
            <span class="muted small">共 {{ totalTickets }} 条</span>
          </div>
          <form class="row wrap report-filters" @submit.prevent="applyFilters">
            <label>创建起日 <input v-model="draft.from" class="input tiny" type="date" /></label>
            <label>创建止日 <input v-model="draft.to" class="input tiny" type="date" /></label>
            <label>部门
              <select v-model="draft.department" class="select tiny">
                <option value="">全部可见部门</option>
                <option v-for="(name, id) in data.ticketDepartments" :key="id" :value="id">{{ name }}</option>
              </select>
            </label>
            <button class="btn sm primary" :disabled="loadingReport">筛选</button>
          </form>
          <p v-if="reportError" class="small" role="alert">{{ reportError }}</p>
          <div class="report-summary small">逾期 {{ data.overdueTickets }} 条 · 截止日已过且仍待处理或处理中</div>
          <div v-if="totalTickets" class="bars">
            <div v-for="row in ticketRows" :key="row.key" class="bar-line">
              <span class="bar-label">{{ row.label }}</span>
              <div class="bar-track">
                <div class="bar-fill" :class="`st-${row.key}`" :style="{ width: row.width }"></div>
              </div>
              <span class="bar-value">{{ row.value }}</span>
            </div>
          </div>
          <div v-else class="empty">没有可查看的工单</div>
          <div class="row wrap panel-foot">
            <router-link :to="{ path: '/m/ticket', query: applied }">查看筛选明细 →</router-link>
            <button class="btn sm" :disabled="loadingReport || exporting || !totalTickets" @click="downloadCsv">导出 CSV</button>
          </div>
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
          <router-link to="/m/employee" class="btn">看看员工表</router-link>
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

.split { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.panel { padding: 16px 18px; display: flex; flex-direction: column; }
.panel-head { display: flex; flex-wrap: wrap; gap: 6px; align-items: baseline; justify-content: space-between; margin-bottom: 12px; }
.panel-head h3 { font-size: 15px; }
.panel-foot { margin-top: auto; padding-top: 12px; font-size: 13px; }

.report-filters { gap: 8px; align-items: end; }
.report-filters label { display: grid; gap: 4px; font-size: 12px; }
.report-summary { margin: 12px 0; color: var(--muted); }
.panel-foot.row { justify-content: space-between; gap: 8px; }
.bars { display: grid; gap: 12px; }
.bar-line { display: grid; grid-template-columns: 64px 1fr 32px; align-items: center; gap: 10px; }
.bar-label { color: var(--muted); font-size: 13px; }
.bar-track { height: 8px; background: var(--panel-2); border-radius: 999px; overflow: hidden; border: 1px solid var(--border); }
.bar-fill { height: 100%; background: var(--primary); border-radius: 999px; }
.bar-fill.st-OPEN { background: #f59e0b; }
.bar-fill.st-PROCESSING { background: #6366f1; }
.bar-fill.st-RESOLVED { background: #10b981; }
.bar-fill.st-CLOSED { background: #94a3b8; }
.bar-value { text-align: right; font-variant-numeric: tabular-nums; }

.audit { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
.audit li { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.audit li .grow { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.quick { margin-top: 14px; padding: 6px 18px; }
.quick-item { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 0; border-bottom: 1px solid var(--border); }
.quick-item:last-child { border-bottom: none; }

.error-card { padding: 18px; color: var(--danger); background: var(--danger-weak); border-color: transparent; }

@media (max-width: 960px) {
  .split { grid-template-columns: minmax(0, 1fr); }
}
</style>
