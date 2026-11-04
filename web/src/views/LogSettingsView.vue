<script setup>
/** 超级管理员配置服务端滚动日志的留存期限。 */
import { onMounted, ref } from 'vue'
import { api } from '../api/http'
import { toast } from '../api/toast'

const days = ref(null)
const logFile = ref('')
const busy = ref(false)
const error = ref('')
const endpoint = '/system/log-settings'

async function load() {
  busy.value = true
  error.value = ''
  try {
    const settings = await api(endpoint)
    days.value = settings.retentionDays
    logFile.value = settings.logFile
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}

async function save() {
  if (busy.value || !Number.isInteger(days.value)) return
  busy.value = true
  error.value = ''
  try {
    const settings = await api(endpoint, { method: 'PUT', body: { retentionDays: days.value } })
    days.value = settings.retentionDays
    toast('日志保留策略已保存', 'success')
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="card logs-panel">
    <h2>运行日志</h2>
    <p class="muted">服务使用 UTF-8 写入日志文件，每天或达到大小上限时生成压缩归档。</p>
    <p v-if="logFile">当前日志 <code class="log-path">{{ logFile }}</code></p>
    <form class="log-form" @submit.prevent="save">
      <div class="field">
        <label for="retention-days">归档保留天数</label>
        <input id="retention-days" v-model.number="days" type="number" class="input"
               min="0" max="3650" step="1" required :disabled="busy || days === null"
               aria-describedby="retention-help" />
        <span id="retention-help" class="muted small">0 表示长期保留。缩短期限后，超期归档会被清理。</span>
      </div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <button class="btn primary" type="submit" :disabled="busy || days === null">保存保留策略</button>
      <button v-if="days === null && error" class="btn" type="button" @click="load">重试</button>
    </form>
  </section>
</template>

<style scoped>
.logs-panel { max-width: 680px; padding: 20px; }
.logs-panel p { margin: 10px 0; }
.log-path { overflow-wrap: anywhere; }
.log-form { max-width: 420px; margin-top: 18px; }
.error { color: var(--danger); }
</style>
