<script setup>
/**
 * 智能助手页：对话 / 工具 / MCP / Skill 四个标签。
 * <p>
 * AI 未启用时页面不报错，而是展示状态栏与启用指引（{@code /api/ai/status} 的 reason）。
 * MCP 与 Skill 的表格直接复用通用 CRUD 接口，保存后服务端会作废旧 AI 会话，
 * 下一次对话自动按新配置重建。
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { api } from '../api/http'
import { auth, can } from '../stores/auth'
import { toast, toastError } from '../api/toast'
import DataForm from '../components/DataForm.vue'

const tab = ref('chat')
const status = ref(null)
const statusError = ref('')
const messages = ref([])
const input = ref('')
const allowTools = ref(true)
const sending = ref(false)
const chatBox = ref(null)

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
  } catch (e) {
    statusError.value = e.message
  }
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  messages.value.push({ role: 'user', text })
  input.value = ''
  sending.value = true
  await scrollBottom()
  try {
    const result = await api('/ai/chat', {
      method: 'POST',
      body: { message: text, allowTools: allowTools.value }
    })
    messages.value.push({ role: 'assistant', text: result.reply, audit: result.audit || [] })
    await scrollBottom()
  } catch (e) {
    messages.value.push({ role: 'error', text: e.message })
    await scrollBottom()
  } finally {
    sending.value = false
  }
}

async function resetChat() {
  try {
    await api('/ai/reset', { method: 'POST' })
    messages.value = []
    toast('对话已清空', 'success')
  } catch (e) {
    toastError(e)
  }
}

async function reloadSessions() {
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
        <span class="muted small">模型 {{ status?.model || '—' }} · 协议 {{ status?.provider || '—' }}</span>
        <span class="muted small">工具 {{ tools.length }} 个</span>
        <span class="muted small">MCP {{ mcpStatus.length }} 个</span>
        <span class="muted small">Skill {{ skills.length }} 个</span>
        <div class="grow"></div>
        <button class="btn sm" @click="loadStatus">刷新状态</button>
        <button v-if="can('ai:manage')" class="btn sm" @click="reloadSessions">重建会话</button>
      </div>
      <p v-if="status?.reason" class="notice">{{ status.reason }}</p>
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
            <div class="text">{{ message.text }}</div>
            <div v-if="message.audit?.length" class="audit">
              <span class="muted small">工具记录：</span>
              <span v-for="(item, i) in message.audit" :key="i" class="badge grey">{{ auditText(item) }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <textarea
          v-model="input"
          class="textarea"
          placeholder="输入问题，Ctrl+Enter 发送"
          @keydown.ctrl.enter.prevent="send"
        ></textarea>
        <div class="row wrap">
          <label class="switch">
            <input v-model="allowTools" type="checkbox" :disabled="!status?.allowTools" />
            <span class="small">允许调用工具</span>
          </label>
          <div class="grow"></div>
          <button class="btn" :disabled="!messages.length" @click="resetChat">清空</button>
          <button class="btn primary" :disabled="sending || !ready || !input.trim()" @click="send">
            {{ sending ? '思考中…' : '发送' }}
          </button>
        </div>
      </div>
    </div>

    <div v-else-if="tab === 'tools'" class="card panel">
      <h3>宿主工具</h3>
      <p class="muted small">
        这些工具由服务端注册，最终调用通用 CRUD；因此权限、数据范围、审计与页面操作完全一致。
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

      <table class="data">
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
      </table>
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

      <table class="data">
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
      </table>
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

.tabs { display: flex; gap: 6px; }
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

.msg { display: flex; gap: 10px; margin-bottom: 14px; align-items: flex-start; }
.msg .who {
  width: 42px;
  flex: none;
  text-align: center;
  font-size: 12px;
  color: var(--muted);
  padding-top: 8px;
}
.bubble { max-width: 78%; background: var(--panel-2); border: 1px solid var(--border); border-radius: 12px; padding: 10px 13px; }
.msg.user .bubble { background: var(--primary-weak); border-color: transparent; }
.msg.error .bubble { background: var(--danger-weak); border-color: transparent; color: var(--danger); }
.text { white-space: pre-wrap; word-break: break-word; }
.audit { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 8px; }

.chat-input { border-top: 1px solid var(--border); padding: 12px 14px; }
.chat-input .textarea { min-height: 60px; margin-bottom: 8px; }
.switch { display: inline-flex; align-items: center; gap: 6px; }

.panel { padding: 16px 18px; }
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
