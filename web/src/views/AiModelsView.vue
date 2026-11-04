<script setup>
/** 独立管理页在助手关闭时也可用。密钥字段始终从空白开始。 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { api } from '../api/http'
import { providers } from '../api/ai-providers'
import { toolLevels } from '../api/tool-access'
import { toast, toastError } from '../api/toast'
import { features } from '../stores/features'

const rows = ref([])
const draft = ref(null)
const editor = ref(null)
const busy = ref(false)
const loaded = ref(false)
const options = ref({ departments: [], users: [] })
const departmentQuery = ref('')
const userQuery = ref('')
const scopeModes = { ALL: '所有符合角色要求的账号', ALLOW: '仅允许所选对象', DENY: '禁止所选对象' }

const preset = computed(() => providers.find(provider =>
  provider.url === draft.value?.baseUrl && provider.protocol === draft.value?.provider
))

function applyPreset(event) {
  const provider = providers.find(item => item.url === event.target.value)
  if (!provider) return
  draft.value.baseUrl = provider.url
  draft.value.provider = provider.protocol
}

function visibleOptions(choices, selected, query) {
  const known = new Set(choices.map(item => item.id))
  const unavailable = selected.filter(id => !known.has(id)).map(id => ({ id, label: '已停用或删除的对象 ' + id }))
  return [...unavailable, ...choices].filter(item => item.label.toLowerCase().includes(query.trim().toLowerCase()))
}

async function load() {
  busy.value = true
  try {
    const [config, accessOptions] = await Promise.all([
      api('/ai/config'),
      api('/ai/config/access-options')
    ])
    options.value = accessOptions
    features.ai = config.enabled
    rows.value = config.models
    loaded.value = true
  } catch (error) {
    toastError(error)
  } finally {
    busy.value = false
  }
}

async function edit(row) {
  departmentQuery.value = ''
  userQuery.value = ''
  draft.value = row ? {
    ...row, apiKey: '', keyAccess: {
      mode: row.keyAccess.mode,
      departmentIds: [...row.keyAccess.departmentIds],
      userIds: [...row.keyAccess.userIds]
    }
  } : {
    name: '', modelId: '', provider: 'openai', baseUrl: '', apiKey: '', keyName: '默认密钥',
    enabled: true, vision: false, allowTools: false, minLevel: 1, maxToolAccess: 'READ_ONLY',
    keyAccess: { mode: 'ALL', departmentIds: [], userIds: [] }
  }
  await nextTick()
  editor.value?.showModal()
}

function closeEditor() {
  if (!busy.value) editor.value.close()
}

async function updateModule(enabled) {
  busy.value = true
  try {
    const result = await api('/ai/config/module', { method: 'PUT', body: { enabled } })
    features.ai = result.enabled
    toast(enabled ? 'AI 助手已启用' : 'AI 助手已关闭', 'success')
  } catch (error) {
    toastError(error)
  } finally {
    busy.value = false
  }
}

async function save() {
  const { id, hasKey, ...body } = draft.value
  busy.value = true
  try {
    const result = await api('/ai/config/models' + (id ? '/' + id : ''), {
      method: id ? 'PUT' : 'POST', body
    })
    rows.value = [...rows.value.filter(row => row.id !== result.id), result]
      .sort((a, b) => a.name.localeCompare(b.name))
    editor.value.close()
    toast('模型已保存', 'success')
  } catch (error) {
    toastError(error)
  } finally {
    busy.value = false
  }
}

async function remove(row) {
  if (!window.confirm('删除模型「' + row.name + '」？')) return
  busy.value = true
  try {
    await api('/ai/config/models/' + row.id, { method: 'DELETE' })
    rows.value = rows.value.filter(item => item.id !== row.id)
    if (draft.value?.id === row.id) draft.value = null
    toast('模型已删除', 'success')
  } catch (error) {
    toastError(error)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="config-page">
    <section class="card config-card">
      <div class="row wrap">
        <h3>AI 助手模块</h3>
        <span class="badge" :class="features.ai ? 'ok' : 'grey'">{{ features.ai ? '已启用' : '已关闭' }}</span>
        <div class="grow"></div>
        <button class="btn" :disabled="busy || !loaded" @click="updateModule(!features.ai)">
          {{ features.ai ? '关闭助手' : '启用助手' }}
        </button>
      </div>
      <p class="muted small">关闭后隐藏对话入口并停用对话接口，模型配置可继续管理。设置保存后立即生效。</p>
    </section>

    <section class="card config-card">
      <div class="row wrap">
        <h3>模型列表</h3>
        <div class="grow"></div>
        <button class="btn" :disabled="busy" @click="load">刷新</button>
        <button class="btn primary" :disabled="busy || !loaded" @click="edit()">新增模型</button>
      </div>
      <div v-if="rows.length" class="model-table">
        <table class="data">
          <thead><tr><th>模型</th><th>协议</th><th>最低等级</th><th>密钥</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td><b>{{ row.name }}</b><div class="muted small">{{ row.modelId }}</div></td>
              <td>{{ row.provider }}</td>
              <td>{{ row.minLevel }}</td>
              <td>
                <b>{{ row.keyName }}</b>
                <span class="badge" :class="row.hasKey ? 'ok' : 'grey'">{{ row.hasKey ? '已配置' : '未配置' }}</span>
                <div class="muted small">{{ scopeModes[row.keyAccess.mode] }}</div>
              </td>
              <td><span class="badge" :class="row.enabled ? 'ok' : 'grey'">{{ row.enabled ? '启用' : '停用' }}</span></td>
              <td class="actions">
                <button class="btn ghost sm" :disabled="busy" @click="edit(row)">编辑</button>
                <button class="btn ghost sm" :disabled="busy" @click="remove(row)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else-if="loaded" class="muted small">添加一个供应商模型，配置密钥后即可启用。</p>

    </section>

    <dialog ref="editor" class="modal model-editor" aria-labelledby="model-editor-title"
            @cancel.prevent="closeEditor" @close="draft = null">
      <form v-if="draft" class="model-form" @submit.prevent="save">
        <header>
          <h3 id="model-editor-title">{{ draft.id ? '编辑模型' : '新增模型' }}</h3>
          <button class="btn ghost sm" type="button" :disabled="busy" @click="closeEditor">关闭</button>
        </header>
        <div class="editor-body">
        <fieldset :disabled="busy">
          <legend>模型与供应商</legend>
          <label>显示名称<input v-model.trim="draft.name" required maxlength="255" class="input" /></label>
          <label>供应商模型 ID<input v-model.trim="draft.modelId" required maxlength="255" class="input" placeholder="deepseek-chat" /></label>
          <label>供应商预设
            <select :value="preset?.url || ''" class="input" @change="applyPreset">
              <option value="" disabled>自定义地址或选择供应商</option>
              <option v-for="provider in providers" :key="provider.url" :value="provider.url">{{ provider.name }}</option>
            </select>
            <a v-if="preset" :href="preset.docs" target="_blank" rel="noopener noreferrer" class="small">查看官方接口文档</a>
          </label>
          <label>接口协议
            <select v-model="draft.provider" class="input">
              <option value="openai">OpenAI 兼容</option><option value="anthropic">Anthropic</option>
            </select>
          </label>
          <label class="full-width">服务地址<input v-model.trim="draft.baseUrl" required maxlength="255" class="input" type="url"
                             placeholder="https://api.deepseek.com/v1" /></label>
        </fieldset>
        <fieldset :disabled="busy">
          <legend>密钥与账号范围</legend>
          <label>密钥名称<input v-model.trim="draft.keyName" class="input" maxlength="255" required
                                placeholder="例如：技术部专用" /></label>
          <label>API Key
            <input v-model.trim="draft.apiKey" :required="!draft.hasKey" maxlength="4096" class="input" type="password"
                   autocomplete="new-password" aria-describedby="ai-key-help"
                   :placeholder="draft.hasKey ? '输入新密钥可替换，留空保留' : '粘贴完整的供应商密钥'" />
            <span id="ai-key-help" class="muted small">
              支持 <code>sk-</code> 开头和不带 <code>sk-</code> 前缀的密钥。直接粘贴密钥，不需要添加 <code>Bearer</code>。
            </span>
            <span v-if="draft.id" class="muted small">更换地址或协议时需重新输入密钥。</span>
          </label>
          <label>最低角色等级
            <select v-model.number="draft.minLevel" class="input" aria-label="最低角色等级">
              <option :value="0">0 · 所有拥有 AI 使用权限的账号</option>
              <option :value="1">1 · 普通员工</option><option :value="2">2 · 部门主管</option>
              <option :value="3">3 · 管理员</option><option :value="4">4 · 超级管理员</option>
            </select>
          </label>
          <label>密钥使用范围
            <select v-model="draft.keyAccess.mode" class="input" aria-label="密钥使用范围" aria-describedby="key-access-help">
              <option v-for="(label, mode) in scopeModes" :key="mode" :value="mode">{{ label }}</option>
            </select>
            <span id="key-access-help" class="muted small">部门和员工账号任一匹配即命中名单。最低角色等级仍需满足。</span>
          </label>
          <div v-if="draft.keyAccess.mode !== 'ALL'" class="access-options">
            <div class="access-group">
              <label>部门（已选 {{ draft.keyAccess.departmentIds.length }}）
                <input v-model="departmentQuery" class="input" type="search" placeholder="搜索部门" />
              </label>
              <div class="access-list">
                <label v-for="item in visibleOptions(options.departments, draft.keyAccess.departmentIds, departmentQuery)" :key="item.id">
                  <input v-model="draft.keyAccess.departmentIds" :value="item.id" type="checkbox" />{{ item.label }}
                </label>
              </div>
            </div>
            <div class="access-group">
              <label>员工账号（已选 {{ draft.keyAccess.userIds.length }}）
                <input v-model="userQuery" class="input" type="search" placeholder="按姓名或账号搜索" />
              </label>
              <div class="access-list">
                <label v-for="item in visibleOptions(options.users, draft.keyAccess.userIds, userQuery)" :key="item.id">
                  <input v-model="draft.keyAccess.userIds" :value="item.id" type="checkbox" />{{ item.label }}
                </label>
              </div>
            </div>
          </div>
        </fieldset>
        <fieldset :disabled="busy">
          <legend>模型能力</legend>
          <div class="row wrap flags">
            <label><input v-model="draft.enabled" type="checkbox" /> 启用模型</label>
            <label><input v-model="draft.vision" type="checkbox" /> 支持图片识别</label>
            <label><input v-model="draft.allowTools" type="checkbox" /> 允许调用工具</label>
          </div>
          <label>工具权限上限
            <select v-model="draft.maxToolAccess" class="input" :disabled="!draft.allowTools">
              <option v-for="level in toolLevels" :key="level.id" :value="level.id">{{ level.label }}</option>
            </select>
            <span class="muted small">删除需要「所有权限」，并且仍须满足账号的业务权限。</span>
          </label>
        </fieldset>
        </div>
        <footer>
          <button class="btn" :disabled="busy" type="button" @click="closeEditor">取消</button>
          <button class="btn primary" :disabled="busy" type="submit">保存模型</button>
        </footer>
      </form>
    </dialog>
  </div>
</template>

<style scoped>
.config-page { display: grid; gap: 14px; }
.config-card { padding: 16px 18px; min-width: 0; }
.config-card h3 { margin: 0; }
.model-table { overflow: auto; margin-top: 12px; }
.actions { white-space: nowrap; }
/* 表单独立滚动，标题和保存按钮始终位于可视区。 */
.model-editor { position: fixed; width: min(820px, calc(100% - 24px)); max-width: none; max-height: calc(100dvh - 24px); padding: 0; overflow: hidden; color: var(--text); }
.model-editor::backdrop { background: rgb(9 12 20 / 50%); }
.model-form { display: flex; flex-direction: column; max-height: calc(100dvh - 26px); }
.model-form header, .model-form footer { flex: none; }
.model-form h3 { margin: 0; }
.editor-body { padding: 16px 20px; overflow: auto; overscroll-behavior: contain; }
.full-width { grid-column: 1 / -1; }
.model-form fieldset { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: start; gap: 12px 16px; border: 0; padding: 0; margin: 0 0 18px; min-width: 0; }
.model-form fieldset:last-child { margin-bottom: 0; }
.model-form legend { padding: 0; margin-bottom: 10px; font-weight: 700; }
.model-form label { display: grid; gap: 4px; min-width: 0; }
.access-options { grid-column: 1 / -1; display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 250px), 1fr)); gap: 12px; }
.access-group { display: grid; gap: 6px; }
.access-list { min-height: 48px; max-height: 160px; overflow: auto; padding: 6px; border: 1px solid var(--border); border-radius: 8px; }
.access-list label { display: flex; align-items: center; gap: 6px; padding: 3px; }
.flags { grid-column: 1 / -1; }
.flags label { display: flex; align-items: center; }
@media (max-width: 580px) {
  .model-form fieldset { grid-template-columns: minmax(0, 1fr); }
}
</style>
