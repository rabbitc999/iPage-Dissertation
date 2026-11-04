<script setup>
/**
 * 智能助手页：对话 / 工具 / MCP / Skill 四个标签。
 * <p>
 * AI 未启用时页面不报错，而是展示状态栏与启用指引（{@code /api/ai/status} 的 reason）。
 * MCP 与 Skill 的表格直接复用通用 CRUD 接口，保存后服务端会作废旧 AI 会话，
 * 下一次对话自动按新配置重建。
 */
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { api, streamChat, upload } from '../api/http'
import { auth, can } from '../stores/auth'
import { toast, toastError } from '../api/toast'
import DataForm from '../components/DataForm.vue'
import { describeChatError } from '../api/chat-errors'
import { toolLevels } from '../api/tool-access'
import { renderChatMarkdown } from '../api/chat-markdown'
import 'katex/dist/katex.min.css'

const tab = ref('chat')
const status = ref(null)
const statusError = ref('')
const messages = ref([])
const input = ref('')
const allowTools = ref(true)
const toolAccess = ref('READ_ONLY')
const sending = ref(false)
const chatBox = ref(null)
const filePicker = ref(null)
const imagePicker = ref(null)
const attachments = ref([])
const selectedModel = ref('')
const allowFileWrites = ref(false)
const runId = ref('')
const stopping = ref(false)
const started = ref(false)
const previews = new Set()
const models = computed(() => status.value?.models || [])
const currentModel = computed(() => models.value.find(model => model.id === selectedModel.value))
const vision = computed(() => currentModel.value?.vision)
const toolsAllowed = computed(() => currentModel.value?.allowTools === true)
const toolAccessOptions = computed(() => {
  const max = toolLevels.findIndex(level => level.id === currentModel.value?.maxToolAccess)
  return toolLevels.slice(0, Math.max(0, max) + 1)
})
const canWriteFiles = computed(() => toolsAllowed.value && allowTools.value && toolAccess.value !== 'READ_ONLY')

watch([selectedModel, toolAccessOptions], () => {
  toolAccess.value = 'READ_ONLY'
  allowFileWrites.value = false
})

function addAttachments(event) {
  for (const file of event.target.files) {
    if (attachments.value.length >= 4) {
      toast('每轮最多发送 4 个附件')
      break
    }
    if (!file.size || file.size > 10 * 1024 * 1024) {
      toast('请选择 10MB 以内的非空文件')
      continue
    }
    const preview = /^image\/(png|jpeg|webp|gif)$/.test(file.type) ? URL.createObjectURL(file) : ''
    if (preview) previews.add(preview)
    attachments.value.push({ file, name: file.name, preview })
  }
  event.target.value = ''
}

function removeAttachment(index) {
  const [attachment] = attachments.value.splice(index, 1)
  if (attachment.preview) {
    URL.revokeObjectURL(attachment.preview)
    previews.delete(attachment.preview)
  }
}

async function downloadAttachment(item) {
  try {
    const blob = await api(item.downloadPath || '/files/' + item.id + '/download', { responseType: 'blob' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = item.name
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (error) {
    toastError(error)
  }
}

async function stop() {
  if (!started.value || stopping.value) return
  stopping.value = true
  try {
    const result = await api('/ai/cancel', { method: 'POST', body: { runId: runId.value } })
    if (!result.ok) stopping.value = false
  } catch (error) {
    stopping.value = false
    toastError(error)
  }
}

const mcpSchema = ref(null)
const skillSchema = ref(null)
const mcpRows = ref([])
const skillRows = ref([])
const dialog = reactive({ open: false, key: '', mode: 'create', row: null, submitting: false })

const isAdmin = computed(() => auth.user?.isAdmin === true)
const ready = computed(() => status.value?.ready === true)
const tools = computed(() => status.value?.tools || [])
const mcpStatus = computed(() => status.value?.mcp || [])
const skills = computed(() => status.value?.skills || [])
const skillErrors = computed(() => status.value?.skillErrors || [])

async function loadStatus() {
  statusError.value = ''
  try {
    status.value = await api('/ai/status')
    if (!models.value.some(model => model.id === selectedModel.value)) {
      selectedModel.value = models.value[0]?.id || ''
    }
  } catch (e) {
    statusError.value = e.message
  }
}

// Let the IME confirm its candidate before handling chat shortcuts.
function handleEnter(event) {
  if (event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  if (event.ctrlKey) {
    const field = event.currentTarget
    field.setRangeText('\n', field.selectionStart, field.selectionEnd, 'end')
    input.value = field.value
    return
  }
  send()
}

async function send() {
  const text = input.value.trim()
  if (!ready.value || (!text && !attachments.value.length) || sending.value) return
  sending.value = true
  runId.value = Array.from(crypto.getRandomValues(new Uint32Array(4)), word => word.toString(16)).join('-')
  try {
    for (const item of attachments.value) {
      if (!item.id) item.id = (await upload('/files?chatPrivate=true', item.file)).id
    }
    const sent = attachments.value
    messages.value.push({ role: 'user', text, attachments: sent })
    input.value = ''
    attachments.value = []
    await scrollBottom()
    const fileWrites = allowFileWrites.value && canWriteFiles.value
    allowFileWrites.value = false
    await streamChat({
      message: text,
      model: selectedModel.value,
      runId: runId.value,
      attachments: sent.map(item => item.id),
      allowTools: allowTools.value && toolsAllowed.value,
      toolAccess: toolAccess.value,
      allowFileWrites: fileWrites
    }, event => {
      if (event.type === 'started') {
        started.value = true
      } else if (event.type === 'cancelled') {
        messages.value.push({ role: 'assistant', text: '本轮已停止。' })
      } else if (event.type === 'assistant') {
        messages.value.push({ role: 'assistant', html: renderChatMarkdown(event.text) })
      } else if (event.type === 'tool') {
        if (event.audit.result?.code === 'tool_permission_denied') {
          messages.value.push({ role: 'error', text: event.audit.result.error, audit: [event.audit] })
          scrollBottom()
          return
        }
        const result = event.audit.result?.result
        const files = []
        if (event.audit.result?.ok && result?.id) {
          if (event.audit.name === 'ims_upload_text_file') files.push(result)
          if (event.audit.name === 'ims_attach_record_file') {
            files.push({ ...result, downloadPath: `/models/${result.model}/${result.recordId}/attachments/${result.id}/download` })
          }
        }
        messages.value.push({ role: 'assistant', audit: [event.audit], attachments: files })
      }
      scrollBottom()
    })
  } catch (e) {
    messages.value.push({ role: 'error', ...describeChatError(e) })
    await scrollBottom()
  } finally {
    started.value = false
    stopping.value = false
    sending.value = false
  }
}

async function resetChat() {
  if (sending.value) return
  try {
    await api('/ai/reset', { method: 'POST' })
    for (const message of messages.value) {
      for (const item of message.attachments || []) {
        if (item.preview) {
          URL.revokeObjectURL(item.preview)
          previews.delete(item.preview)
        }
      }
    }
    messages.value = []
    toast('对话已清空', 'success')
  } catch (e) {
    toastError(e)
  }
}

async function reloadSessions() {
  if (sending.value) return
  try {
    await api('/ai/reload', { method: 'POST' })
    toast('会话已重建', 'success')
    await loadStatus()
  } catch (e) {
    toastError(e)
  }
}

async function scrollBottom() {
  await nextTick()
  if (chatBox.value) chatBox.value.scrollTop = chatBox.value.scrollHeight
}

async function loadConfigRows() {
  if (!isAdmin.value) return
  try {
    const [mcpSchemaData, skillSchemaData, mcp, skill] = await Promise.all([
      api('/models/aiMcpServer/schema'),
      api('/models/aiSkill/schema'),
      api('/models/aiMcpServer', { params: { size: 100 } }),
      api('/models/aiSkill', { params: { size: 100 } })
    ])
    mcpSchema.value = mcpSchemaData
    skillSchema.value = skillSchemaData
    mcpRows.value = mcp.rows
    skillRows.value = skill.rows
  } catch (e) {
    toastError(e)
  }
}

function openCreate(key) {
  dialog.key = key
  dialog.mode = 'create'
  dialog.row = null
  dialog.open = true
}

function openEdit(key, row) {
  dialog.key = key
  dialog.mode = 'edit'
  dialog.row = row
  dialog.open = true
}

async function saveConfig(payload) {
  dialog.submitting = true
  try {
    if (dialog.mode === 'create') {
      await api(`/models/${dialog.key}`, { method: 'POST', body: payload })
    } else {
      await api(`/models/${dialog.key}/${dialog.row.id}`, { method: 'PUT', body: payload })
    }
    dialog.open = false
    toast('已保存，下一次对话生效', 'success')
    await loadConfigRows()
    await loadStatus()
  } catch (e) {
    toastError(e)
  } finally {
    dialog.submitting = false
  }
}

async function removeConfig(key, row) {
  if (!window.confirm(`确定删除「${row._display || row.id}」？`)) return
  try {
    await api(`/models/${key}/${row.id}`, { method: 'DELETE' })
    toast('已删除', 'success')
    await loadConfigRows()
    await loadStatus()
  } catch (e) {
    toastError(e)
  }
}

/** 把状态里的审计记录压成一行摘要。 */
function auditText(item) {
  const name = item.name || 'tool'
  const ok = item.result?.ok !== false
  return `${ok ? '调用' : '失败'} ${name}`
}

onUnmounted(() => previews.forEach(url => URL.revokeObjectURL(url)))

onMounted(async () => {
  await loadStatus()
  await loadConfigRows()
})
</script>

<template>
  <div class="ai-page">
    <div class="card status-bar">
      <div class="row wrap">
        <span class="badge" :class="ready ? 'ok' : 'grey'">{{ ready ? '已就绪' : '未启用' }}</span>
        <span class="muted small">模型 {{ currentModel?.name || '—' }} · 协议 {{ currentModel?.provider || '—' }}</span>
        <span class="muted small">工具 {{ tools.length }} 个</span>
        <span class="muted small">MCP {{ mcpStatus.length }} 个</span>
        <span class="muted small">Skill {{ skills.length }} 个</span>
        <div class="grow"></div>
        <button class="btn sm" @click="loadStatus">刷新状态</button>
        <button v-if="can('ai:manage')" class="btn sm" @click="reloadSessions">重建会话</button>
      </div>
      <p v-if="status?.reason" class="notice">
        {{ status.reason }}
        <router-link v-if="isAdmin" to="/ai-models">管理模型</router-link>
      </p>
      <p v-if="statusError" class="notice error">{{ statusError }}</p>
    </div>

    <div class="tabs">
      <button class="tab" :class="{ on: tab === 'chat' }" @click="tab = 'chat'">对话</button>
      <button class="tab" :class="{ on: tab === 'tools' }" @click="tab = 'tools'">工具</button>
      <button v-if="isAdmin" class="tab" :class="{ on: tab === 'mcp' }" @click="tab = 'mcp'">MCP 服务</button>
      <button v-if="isAdmin" class="tab" :class="{ on: tab === 'skill' }" @click="tab = 'skill'">Skill</button>
    </div>

    <div v-if="tab === 'chat'" class="card chat">
      <div ref="chatBox" class="chat-body">
        <div v-if="!messages.length" class="chat-empty">
          <h3>向数据提问，或让助手改数据</h3>
          <p class="muted small">
            试试：「技术部现在有多少员工？」「把工单 #1 的状态改成处理中」「列出状态为闲置的资产」。
          </p>
          <p class="muted small">助手只能看到你有权看到的数据；删除操作会先向你确认。</p>
        </div>

        <div v-for="(message, index) in messages" :key="index" class="msg" :class="message.role">
          <div class="who">{{ message.role === 'user' ? '我' : message.role === 'error' ? '错误' : '助手' }}</div>
          <div class="bubble">
            <div v-if="message.role === 'error'" class="warning">
              <div class="warning-summary" role="alert">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10.3 4.5 3 17a2 2 0 0 0 1.7 3h14.6a2 2 0 0 0 1.7-3L13.7 4.5a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4m0 3h.01"/></svg>
                <div><strong>请求遇到问题</strong><div class="text">{{ message.text }}</div></div>
              </div>
              <details v-if="message.detail">
                <summary>查看原始错误</summary>
                <pre>{{ message.detail }}</pre>
              </details>
            </div>
            <div v-else-if="message.html" class="markdown" v-html="message.html"></div>
            <div v-else class="text">{{ message.text }}</div>
            <div v-if="message.attachments?.length" class="attachments">
              <button v-for="item in message.attachments" :key="item.id" class="attachment"
                      type="button" @click="downloadAttachment(item)" :title="'下载 ' + item.name">
                <img v-if="item.preview" :src="item.preview" :alt="item.name" />
                <span>{{ item.name }}</span>
              </button>
            </div>
            <div v-if="message.audit?.length" class="audit">
              <span class="muted small">工具记录：</span>
              <span v-for="(item, i) in message.audit" :key="i" class="badge grey">{{ auditText(item) }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <div class="composer">
          <div v-if="attachments.length" class="attachments">
            <div v-for="(item, index) in attachments" :key="index" class="attachment">
              <img v-if="item.preview" :src="item.preview" :alt="item.name" />
              <span>{{ item.name }}</span>
              <button type="button" :disabled="sending" :aria-label="'移除 ' + item.name"
                      @click="removeAttachment(index)">×</button>
            </div>
          </div>
          <textarea v-model="input" class="textarea" rows="2" :disabled="sending"
                    placeholder="发消息，或添加文件和图片 · Enter 发送 · Ctrl+Enter 换行"
                    @keydown.enter="handleEnter"></textarea>
          <div class="row wrap composer-tools">
            <input ref="filePicker" type="file" multiple hidden @change="addAttachments" />
            <input ref="imagePicker" type="file" multiple hidden accept="image/png,image/jpeg,image/webp,image/gif"
                   @change="addAttachments" />
            <button type="button" class="btn icon-btn" title="添加文件" aria-label="添加文件"
                    :disabled="sending || !ready" @click="filePicker.click()">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 13 6-6a2 2 0 0 1 3 3l-8 8a4 4 0 0 1-6-6l9-9"/></svg>
            </button>
            <button type="button" class="btn icon-btn" title="添加图片" aria-label="添加图片"
                    :disabled="sending || !ready" @click="imagePicker.click()">
              <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="8" cy="9" r="1.5"/><path d="m4 17 5-4 3 2 4-5 5 5"/></svg>
            </button>
            <select v-model="selectedModel" class="model-picker" aria-label="选择模型"
                    :disabled="sending || !models.length" title="切换模型会开启新的上下文">
              <option v-if="!models.length" value="">无可用模型</option>
              <option v-for="model in models" :key="model.id" :value="model.id">
                {{ model.name }}{{ model.vision ? ' · 视觉' : '' }}
              </option>
            </select>
            <label class="switch">
              <input v-model="allowTools" type="checkbox" :disabled="sending || !toolsAllowed" />
              <span class="small">工具</span>
            </label>
            <select v-model="toolAccess" class="model-picker" aria-label="AI 工具权限"
                    :disabled="sending || !toolsAllowed || !allowTools">
              <option v-for="level in toolAccessOptions" :key="level.id" :value="level.id">{{ level.label }}</option>
            </select>
            <label class="switch" title="授权本轮生成文件、关联业务附件或更新个人头像">
              <input v-model="allowFileWrites" type="checkbox"
                     :disabled="sending || !canWriteFiles" />
              <span class="small">文件写入</span>
            </label>
            <div class="grow"></div>
            <button class="btn ghost sm" :disabled="sending || !messages.length" @click="resetChat">清空</button>
            <button v-if="sending" class="btn primary icon-btn send-btn" :disabled="!started || stopping"
                    :title="stopping ? '正在停止' : started ? '停止本轮' : '正在提交'"
                    aria-label="停止本轮" @click="stop">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 6v12m6-12v12"/></svg>
            </button>
            <button v-else class="btn primary icon-btn send-btn" title="发送" aria-label="发送"
                    :disabled="!ready || (!input.trim() && !attachments.length)" @click="send">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 19V5m-6 6 6-6 6 6"/></svg>
            </button>
          </div>
        </div>
        <p v-if="attachments.some(item => item.preview) && !vision" class="muted small attachment-note">
          当前模型只接收图片的文件信息。选择视觉模型可识别图片。
        </p>
      </div>
    </div>

    <div v-else-if="tab === 'tools'" class="card panel">
      <h3>宿主工具</h3>
      <p class="muted small">
        这些工具由服务端注册；数据工具复用通用 CRUD 权限，assistant_reply 发送消息，assistant_end 结束本次处理。
      </p>
      <div class="tool-grid">
        <div v-for="name in tools" :key="name" class="tool">
          <span class="mono">{{ name }}</span>
        </div>
      </div>
      <p v-if="!tools.length" class="empty">未启用或没有注册工具</p>
    </div>

    <div v-else-if="tab === 'mcp'" class="card panel">
      <div class="panel-head">
        <div>
          <h3>MCP 服务</h3>
          <p class="muted small">保存后自动作废旧会话，下一次对话按新配置启动。</p>
        </div>
        <button class="btn primary sm" @click="openCreate('aiMcpServer')">新增</button>
      </div>

      <div class="status-list" v-if="mcpStatus.length">
        <div v-for="item in mcpStatus" :key="item.name" class="status-item">
          <span class="badge" :class="item.ok ? 'ok' : 'danger'">{{ item.ok ? '运行中' : '启动失败' }}</span>
          <b>{{ item.name }}</b>
          <span class="muted small">前缀 {{ item.prefix }}</span>
          <span v-if="item.error" class="muted small">{{ item.error }}</span>
        </div>
      </div>

      <div class="config-table"><table class="data">
        <thead>
          <tr>
            <th>名称</th>
            <th>前缀</th>
            <th>启动命令</th>
            <th>状态</th>
            <th style="width: 120px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in mcpRows" :key="row.id">
            <td>{{ row.name }}</td>
            <td class="mono">{{ row.prefix }}</td>
            <td class="mono">{{ row.command }}</td>
            <td>
              <span class="badge" :class="row.enabled ? 'ok' : 'grey'">{{ row.enabled ? '启用' : '停用' }}</span>
            </td>
            <td>
              <button class="btn ghost sm" @click="openEdit('aiMcpServer', row)">编辑</button>
              <button class="btn ghost sm danger-text" @click="removeConfig('aiMcpServer', row)">删除</button>
            </td>
          </tr>
        </tbody>
      </table></div>
      <p v-if="!mcpRows.length" class="empty">还没有配置 MCP 服务</p>
    </div>

    <div v-else class="card panel">
      <div class="panel-head">
        <div>
          <h3>Skill</h3>
          <p class="muted small">Skill 是指令文本（SKILL.md），不执行脚本、不改变工具权限。</p>
        </div>
        <button class="btn primary sm" @click="openCreate('aiSkill')">新增</button>
      </div>

      <div class="status-list" v-if="skills.length || skillErrors.length">
        <div v-for="name in skills" :key="name" class="status-item">
          <span class="badge ok">已加载</span>
          <b class="mono">{{ name }}</b>
        </div>
        <div v-for="item in skillErrors" :key="item" class="status-item">
          <span class="badge danger">失败</span>
          <span class="muted small">{{ item }}</span>
        </div>
      </div>

      <div class="config-table"><table class="data">
        <thead>
          <tr>
            <th>名称</th>
            <th>目录</th>
            <th>自动激活</th>
            <th>状态</th>
            <th style="width: 120px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in skillRows" :key="row.id">
            <td>{{ row.name }}</td>
            <td class="mono">{{ row.directory }}</td>
            <td>{{ row.autoActivate ? '是' : '否' }}</td>
            <td>
              <span class="badge" :class="row.enabled ? 'ok' : 'grey'">{{ row.enabled ? '加载' : '停用' }}</span>
            </td>
            <td>
              <button class="btn ghost sm" @click="openEdit('aiSkill', row)">编辑</button>
              <button class="btn ghost sm danger-text" @click="removeConfig('aiSkill', row)">删除</button>
            </td>
          </tr>
        </tbody>
      </table></div>
      <p v-if="!skillRows.length" class="empty">还没有配置 Skill</p>
    </div>

    <DataForm
      v-if="dialog.open"
      :schema="dialog.key === 'aiMcpServer' ? mcpSchema : skillSchema"
      :row="dialog.row"
      :mode="dialog.mode"
      :submitting="dialog.submitting"
      @submit="saveConfig"
      @cancel="dialog.open = false"
    />
  </div>
</template>

<style scoped>
.ai-page { display: flex; flex-direction: column; gap: 14px; }
.status-bar { padding: 12px 16px; }
.notice { margin: 8px 0 0; padding: 8px 10px; border-radius: 8px; background: var(--primary-weak); color: var(--text); font-size: 13px; }
.notice.error { background: var(--danger-weak); color: var(--danger); }

.tabs { display: flex; flex-wrap: wrap; gap: 6px; }
.tab {
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--muted);
  cursor: pointer;
}
.tab.on { background: var(--primary-weak); border-color: var(--primary); color: var(--primary); font-weight: 600; }

.chat { display: flex; flex-direction: column; height: calc(100vh - 260px); min-height: 420px; }
.chat-body { flex: 1; overflow: auto; padding: 18px 18px 6px; }
.chat-empty { padding: 6vh 10px; text-align: center; }
.chat-empty h3 { margin-bottom: 8px; }
.chat-empty p { margin: 4px 0; }

.msg { display: flex; flex-direction: column; gap: 4px; margin-bottom: 16px; align-items: flex-start; }
.msg.user { align-items: flex-end; }
.msg .who {
  padding-inline: 6px;
  font-size: 12px;
  color: var(--muted);
  padding-top: 8px;
}
.bubble { min-width: 0; max-width: 78%; background: var(--panel-2); border: 1px solid var(--border); border-radius: 17px 17px 17px 5px; padding: 10px 14px; }
.msg.user .bubble { background: #7550c2; color: white; border-color: transparent; border-radius: 17px 17px 5px 17px; }
/* 警告与助手消息同列，技术详情默认收起。 */
.msg.error .bubble { background: #fff6d9; border-color: #e9c771; color: #614309; }
.warning-summary { display: flex; align-items: flex-start; gap: 8px; }
.warning-summary svg { flex: none; width: 19px; height: 19px; fill: none; stroke: #906000; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.warning .text { margin-top: 3px; }
.warning details { margin: 8px 0 0 27px; font-size: 12px; }
.warning summary { cursor: pointer; }
.warning pre { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 9rem; overflow: auto; margin-bottom: 0; }
.text { white-space: pre-wrap; word-break: break-word; }
.markdown { overflow-wrap: anywhere; }
.markdown :deep(> :first-child) { margin-top: 0; }
.markdown :deep(> :last-child) { margin-bottom: 0; }
.markdown :deep(pre) { overflow-x: auto; padding: 10px; border-radius: 6px; background: var(--panel); }
.markdown :deep(table) { display: block; overflow-x: auto; border-collapse: collapse; }
.markdown :deep(th), .markdown :deep(td) { padding: 5px 8px; border: 1px solid var(--border); }
.markdown :deep(img) { max-width: 100%; }
.markdown :deep(.katex-display) { overflow-x: auto; overflow-y: hidden; padding: 4px 0; }
.audit { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 8px; }

.chat-input { border-top: 1px solid var(--border); padding: 12px 14px; }
/* 输入与附件控制保留在同一面板，窄屏工具栏可以换行。 */
.composer { border: 1px solid var(--border); border-radius: 18px; padding: 9px; background: var(--panel); }
.composer:focus-within { border-color: var(--primary); }
.chat-input .textarea { width: 100%; min-height: 60px; resize: vertical; border: 0; outline: none; box-shadow: none; background: transparent; }
.composer-tools { gap: 6px; }
.model-picker { max-width: 100%; min-width: 0; padding: 6px; color: var(--text); background: var(--panel); border: 1px solid var(--border); border-radius: 8px; }
.icon-btn { display: inline-grid; place-items: center; padding: 0; width: 34px; height: 34px; }
.icon-btn svg { width: 18px; height: 18px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.send-btn { border-radius: 50%; }
.attachments { display: flex; flex-wrap: wrap; gap: 6px; margin: 4px 0; }
.attachment { display: inline-flex; align-items: center; gap: 8px; max-width: 100%; padding: 5px 8px; border: 1px solid var(--border); border-radius: 8px; background: var(--panel); color: var(--text); }
.attachment span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.attachment img { width: 44px; height: 44px; object-fit: cover; border-radius: 5px; }
.attachment button { background: none; border: 0; color: inherit; cursor: pointer; }
button.attachment { cursor: pointer; }
.attachment-note { margin: 5px 2px 0; }
.switch { display: inline-flex; align-items: center; gap: 6px; }

.config-table { overflow-x: auto; }
.config-table .mono { max-width: 28rem; white-space: normal; overflow-wrap: anywhere; }
.panel { min-width: 0; padding: 16px 18px; }
.panel-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 12px; }
.tool-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 8px; margin-top: 12px; }
.tool { padding: 9px 12px; border: 1px solid var(--border); border-radius: 9px; background: var(--panel-2); }
.status-list { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 12px; }
.status-item { display: flex; align-items: center; gap: 8px; padding: 6px 10px; border: 1px solid var(--border); border-radius: 9px; }
.danger-text { color: var(--danger); }

@media (max-width: 760px) {
  .bubble { max-width: 100%; }
}
</style>
