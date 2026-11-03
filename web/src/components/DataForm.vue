<script setup>
/**
 * schema 驱动的通用表单。
 * <p>
 * 字段类型、必填、长度、正则、下拉选项全部来自服务端 schema，组件里不写死任何业务字段。
 * 校验在这里做一遍只为即时反馈，**服务端会再校验一次**。
 * <p>
 * 注意：主键与外键都是 UUID 字符串，取值时不要做 Number 转换（会变成 NaN）。
 */
import { computed, reactive, watch } from 'vue'

const props = defineProps({
  schema: { type: Object, required: true },
  row: { type: Object, default: null },
  mode: { type: String, default: 'create' },
  submitting: { type: Boolean, default: false }
})
const emit = defineEmits(['submit', 'cancel'])

const values = reactive({})
const errors = reactive({})

const fields = computed(() => (props.schema?.fields || []).filter((field) => field.form))

const title = computed(() => (props.mode === 'create' ? `新增${props.schema.name}` : `编辑${props.schema.name}`))

function init() {
  for (const key of Object.keys(values)) delete values[key]
  for (const key of Object.keys(errors)) delete errors[key]
  for (const field of fields.value) {
    const raw = props.row ? props.row[field.name] : undefined
    if (field.type === 'MULTI_SELECT') {
      // 主键是 UUID 字符串，这里不能转 Number，否则会得到 NaN
      values[field.name] = Array.isArray(raw) ? raw.map((item) => String(item)) : []
    } else if (field.type === 'BOOLEAN') {
      values[field.name] = raw === true
    } else if (field.type === 'PASSWORD') {
      values[field.name] = ''
    } else if (raw === null || raw === undefined) {
      values[field.name] = ''
    } else if (field.type === 'NUMBER') {
      values[field.name] = Number(raw)
    } else if (field.type === 'ASSOCIATION_ID') {
      // 关联外键同样是 UUID，保持字符串，才能和 <option> 的 value 对上
      values[field.name] = String(raw)
    } else {
      values[field.name] = raw
    }
  }
}

watch(() => [props.schema, props.row, props.mode], init, { immediate: true })

function optionsOf(field) {
  return Object.entries(field.options || {})
}

function validate() {
  let ok = true
  for (const key of Object.keys(errors)) delete errors[key]
  for (const field of fields.value) {
    const value = values[field.name]
    const required = field.required || (field.requiredOnCreate && props.mode === 'create')
    if (field.type === 'MULTI_SELECT') continue
    if (required && (value === '' || value === null || value === undefined)) {
      errors[field.name] = `请填写${field.text}`
      ok = false
      continue
    }
    if (typeof value === 'string' && field.maxLength > 0 && value.length > field.maxLength) {
      errors[field.name] = `${field.text}最多 ${field.maxLength} 个字符`
      ok = false
      continue
    }
    if (typeof value === 'string' && field.pattern && value) {
      try {
        if (!new RegExp(`^(?:${field.pattern})$`).test(value)) {
          errors[field.name] = `${field.text}格式不正确`
          ok = false
        }
      } catch (error) {
        // 正则写错时不在前端拦，交给服务端
      }
    }
  }
  return ok
}

function submit() {
  if (!validate()) return
  const payload = {}
  for (const field of fields.value) {
    const value = values[field.name]
    if (field.type === 'PASSWORD') {
      if (typeof value === 'string' && value.trim()) payload.password = value.trim()
      continue
    }
    if (field.type === 'MULTI_SELECT') {
      payload[field.name] = (value || []).map((item) => String(item))
      continue
    }
    if (value === '' || value === null || value === undefined) {
      // 新增时留空表示「不传」，让模型默认值生效；编辑时留空表示清空
      if (props.mode === 'edit') payload[field.name] = null
      continue
    }
    if (field.type === 'NUMBER') {
      payload[field.name] = Number(value)
    } else {
      payload[field.name] = value
    }
  }
  emit('submit', payload)
}
</script>

<template>
  <div class="modal-mask" @mousedown.self="emit('cancel')">
    <div class="modal">
      <header>
        <h3>{{ title }}</h3>
        <button class="btn ghost sm" @click="emit('cancel')">关闭</button>
      </header>

      <div class="body">
        <div class="grid">
          <div
            v-for="field in fields"
            :key="field.name"
            class="field"
            :class="{ wide: field.type === 'MULTI_SELECT' || field.maxLength > 120 }"
          >
            <label>
              {{ field.text }}
              <span v-if="field.required || (field.requiredOnCreate && mode === 'create')" class="req">*</span>
            </label>

            <select
              v-if="field.type === 'ENUM' || field.type === 'ASSOCIATION_ID'"
              v-model="values[field.name]"
              class="select"
            >
              <option value="">请选择</option>
              <option v-for="[key, label] in optionsOf(field)" :key="key" :value="key">{{ label }}</option>
            </select>

            <div v-else-if="field.type === 'MULTI_SELECT'" class="chips">
              <label
                v-for="[key, label] in optionsOf(field)"
                :key="key"
                class="chip"
                :class="{ on: (values[field.name] || []).includes(String(key)) }"
              >
                <input v-model="values[field.name]" type="checkbox" :value="String(key)" />
                {{ label }}
              </label>
              <span v-if="!optionsOf(field).length" class="muted small">暂无可选项</span>
            </div>

            <textarea
              v-else-if="field.type === 'STRING' && (field.maxLength > 120 || field.name === 'description' || field.name === 'remark')"
              v-model="values[field.name]"
              class="textarea"
              :maxlength="field.maxLength > 0 ? field.maxLength : undefined"
            ></textarea>

            <input
              v-else-if="field.type === 'PASSWORD'"
              v-model="values[field.name]"
              class="input"
              type="password"
              :placeholder="mode === 'edit' ? '留空表示不修改' : '至少 6 位'"
              autocomplete="new-password"
            />

            <input
              v-else-if="field.type === 'DATE'"
              v-model="values[field.name]"
              class="input"
              type="date"
            />

            <input
              v-else-if="field.type === 'NUMBER'"
              v-model="values[field.name]"
              class="input"
              type="number"
              step="any"
            />

            <label v-else-if="field.type === 'BOOLEAN'" class="switch">
              <input v-model="values[field.name]" type="checkbox" />
              <span>{{ values[field.name] ? '是' : '否' }}</span>
            </label>

            <input
              v-else
              v-model="values[field.name]"
              class="input"
              :maxlength="field.maxLength > 0 ? field.maxLength : undefined"
            />

            <div v-if="errors[field.name]" class="err">{{ errors[field.name] }}</div>
          </div>
        </div>
      </div>

      <footer>
        <button class="btn" @click="emit('cancel')">取消</button>
        <button class="btn primary" :disabled="submitting" @click="submit">
          {{ submitting ? '保存中…' : '保存' }}
        </button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}
.field.wide { grid-column: 1 / -1; }
.switch { display: inline-flex; align-items: center; gap: 8px; height: 34px; }
.chips { display: flex; flex-wrap: wrap; gap: 8px; }
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border: 1px solid var(--border-strong);
  border-radius: 999px;
  cursor: pointer;
  color: var(--muted);
}
.chip.on { border-color: var(--primary); color: var(--primary); background: var(--primary-weak); }
.chip input { display: none; }
@media (max-width: 680px) {
  .grid { grid-template-columns: 1fr; }
}
</style>
