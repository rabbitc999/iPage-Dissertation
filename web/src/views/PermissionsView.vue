<script setup>
/**
 * 角色与权限矩阵。
 * <p>
 * 左侧角色列表，右侧按分组勾选权限点，保存时整体替换（先删后插）。
 * 写操作需要 {@code system:role:manage}，没有该权限点时按钮置灰并由服务端兜底拦截。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api/http'
import { auth, can } from '../stores/auth'
import { toast, toastError } from '../api/toast'

const roles = ref([])
const permissions = ref([])
const selectedId = ref(null)
const checked = ref([])
const loading = ref(true)
const saving = ref(false)
const createOpen = ref(false)

const form = reactive({ code: '', name: '', level: 1, dataScope: 'SELF', remark: '' })

const selected = computed(() => roles.value.find((role) => role.id === selectedId.value) || null)
const groups = computed(() => {
  const map = new Map()
  for (const permission of permissions.value) {
    const name = permission.group || '其它'
    if (!map.has(name)) map.set(name, [])
    map.get(name).push(permission)
  }
  return [...map.entries()].map(([name, items]) => ({ name, items }))
})

const canManage = computed(() => can('system:role:manage'))

const scopeLabels = { ALL: '全部数据', DEPT: '本部门', SELF: '仅本人' }

function levelLabel(level) {
  if (level >= 4) return '超级管理员'
  if (level === 3) return '管理员'
  if (level === 2) return '主管'
  if (level === 1) return '员工'
  return '受限'
}

async function loadRoles() {
  roles.value = await api('/roles')
  if (!selectedId.value && roles.value.length) {
    selectedId.value = roles.value[0].id
  }
}

async function loadPermissions() {
  permissions.value = await api('/permissions')
}

async function loadChecked() {
  if (!selectedId.value) return
  const result = await api(`/roles/${selectedId.value}/permissions`)
  checked.value = result.permissionIds || []
}

async function selectRole(role) {
  selectedId.value = role.id
  await loadChecked()
}

async function save() {
  saving.value = true
  try {
    await api(`/roles/${selectedId.value}/permissions`, {
      method: 'PUT',
      body: { permissionIds: checked.value }
    })
    toast('权限已保存', 'success')
  } catch (e) {
    toastError(e)
  } finally {
    saving.value = false
  }
}

function toggleGroup(items, on) {
  const ids = items.map((item) => item.id)
  if (on) {
    checked.value = [...new Set([...checked.value, ...ids])]
  } else {
    checked.value = checked.value.filter((id) => !ids.includes(id))
  }
}

function groupChecked(items) {
  return items.every((item) => checked.value.includes(item.id))
}

async function createRole() {
  try {
    await api('/roles', { method: 'POST', body: { ...form } })
    toast('角色已创建', 'success')
    createOpen.value = false
    form.code = ''
    form.name = ''
    form.level = 1
    form.dataScope = 'SELF'
    form.remark = ''
    await loadRoles()
  } catch (e) {
    toastError(e)
  }
}

async function removeRole() {
  if (!selected.value || selected.value.builtin) return
  if (!window.confirm(`确定删除角色「${selected.value.name}」？`)) return
  try {
    await api(`/roles/${selected.value.id}`, { method: 'DELETE' })
    toast('角色已删除', 'success')
    selectedId.value = null
    await loadRoles()
    await loadChecked()
  } catch (e) {
    toastError(e)
  }
}

onMounted(async () => {
  try {
    await Promise.all([loadRoles(), loadPermissions()])
    await loadChecked()
  } catch (e) {
    toastError(e)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-if="loading" class="empty">加载中…</div>

  <div v-else class="perm">
    <div class="card roles">
      <div class="roles-head">
        <h3>角色</h3>
        <button v-if="canManage" class="btn sm primary" @click="createOpen = true">新建</button>
      </div>
      <button
        v-for="role in roles"
        :key="role.id"
        class="role-item"
        :class="{ active: role.id === selectedId }"
        @click="selectRole(role)"
      >
        <div class="role-line">
          <span class="role-name">{{ role.name }}</span>
          <span class="badge grey">{{ levelLabel(role.level) }}</span>
        </div>
        <div class="muted small">
          {{ role.code }} · {{ scopeLabels[role.dataScope] || role.dataScope }}
          <span v-if="role.builtin"> · 内置</span>
        </div>
      </button>
      <p class="muted small tip">
        等级决定模型级权限：查看/编辑/删除注解里写的是最低等级，用户取所有角色中的最高等级。
      </p>
    </div>

    <div class="card matrix">
      <div class="matrix-head">
        <div>
          <h3>{{ selected?.name || '选择角色' }}</h3>
          <p class="muted small">
            勾选该角色可用的功能权限点。超级管理员无需勾选即拥有一切。
          </p>
        </div>
        <div class="row">
          <button v-if="canManage && selected && !selected.builtin" class="btn danger" @click="removeRole">删除角色</button>
          <button class="btn primary" :disabled="!canManage || saving || !selected" @click="save">
            {{ saving ? '保存中…' : '保存权限' }}
          </button>
        </div>
      </div>

      <div v-for="group in groups" :key="group.name" class="group-block">
        <div class="group-head">
          <label class="check-line">
            <input
              type="checkbox"
              :checked="groupChecked(group.items)"
              @change="toggleGroup(group.items, $event.target.checked)"
            />
            <b>{{ group.name }}</b>
          </label>
          <span class="muted small">{{ group.items.length }} 项</span>
        </div>
        <div class="perm-grid">
          <label v-for="permission in group.items" :key="permission.id" class="perm-item">
            <input v-model="checked" type="checkbox" :value="permission.id" />
            <span>
              <b>{{ permission.name }}</b>
              <span class="mono muted small">{{ permission.code }}</span>
            </span>
          </label>
        </div>
      </div>

      <p v-if="!groups.length" class="empty">暂无权限点</p>
      <p v-if="!canManage" class="muted small tip">
        当前账号缺少 system:role:manage 权限点，只能查看矩阵，不能保存。
      </p>
    </div>

    <div v-if="createOpen" class="modal-mask" @mousedown.self="createOpen = false">
      <div class="modal narrow">
        <header>
          <h3>新建角色</h3>
          <button class="btn ghost sm" @click="createOpen = false">关闭</button>
        </header>
        <div class="body">
          <div class="field">
            <label>角色编码<span class="req">*</span></label>
            <input v-model="form.code" class="input" placeholder="例如 AUDITOR" />
          </div>
          <div class="field">
            <label>角色名称</label>
            <input v-model="form.name" class="input" placeholder="例如 审计员" />
          </div>
          <div class="field">
            <label>等级</label>
            <select v-model.number="form.level" class="select">
              <option :value="0">0 · 受限</option>
              <option :value="1">1 · 员工</option>
              <option :value="2">2 · 主管</option>
              <option :value="3">3 · 管理员</option>
              <option :value="4">4 · 超级管理员</option>
            </select>
            <div class="hint">等级越高，能看到的模型与操作越多。</div>
          </div>
          <div class="field">
            <label>数据范围</label>
            <select v-model="form.dataScope" class="select">
              <option value="SELF">仅本人</option>
              <option value="DEPT">本部门</option>
              <option value="ALL">全部数据</option>
            </select>
          </div>
          <div class="field">
            <label>备注</label>
            <input v-model="form.remark" class="input" />
          </div>
        </div>
        <footer>
          <button class="btn" @click="createOpen = false">取消</button>
          <button class="btn primary" @click="createRole">创建</button>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.perm { display: grid; grid-template-columns: 260px 1fr; gap: 14px; align-items: start; }
.roles { padding: 14px; position: sticky; top: 80px; }
.roles-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.role-item {
  display: block;
  width: 100%;
  text-align: left;
  padding: 10px 10px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  margin-bottom: 4px;
  color: var(--text);
}
.role-item:hover { background: var(--panel-2); }
.role-item.active { background: var(--primary-weak); border-color: var(--primary); }
.role-line { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.role-name { font-weight: 600; }
.tip { margin: 12px 4px 0; line-height: 1.7; }

.matrix { padding: 16px 18px; }
.matrix-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 10px; }
.group-block { border-top: 1px solid var(--border); padding-top: 12px; margin-top: 12px; }
.group-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.check-line { display: inline-flex; align-items: center; gap: 8px; cursor: pointer; }
.perm-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 260px), 1fr)); gap: 8px; }
.perm-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 10px;
  cursor: pointer;
  background: var(--panel-2);
}
.perm-item span { display: flex; flex-direction: column; }
.modal.narrow { max-width: 440px; }

@media (max-width: 860px) {
  .perm { grid-template-columns: 1fr; }
  .roles { position: static; }
}
</style>
