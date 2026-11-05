<script setup>
/**
 * 系统图像设置（需要 system:setting:manage 权限点）。
 * <p>
 * 三个键值：站点名、侧边栏 Logo、登录页品牌图。
 * 图像支持两种填法：直接粘贴链接（相对 / 绝对 / data: 均可），
 * 或点「上传」走 /api/files 接口落库后自动回填相对链接。
 * 保存是整体 PUT /api/settings，服务端只接受键白名单。
 */
import { onMounted, reactive, ref } from 'vue'
import { api, upload } from '../api/http'
import { toast, toastError } from '../api/toast'
import { applyBrand } from '../stores/brand'

const form = reactive({ siteName: '', logoUrl: '', loginImageUrl: '' })
const loading = ref(true)
const saving = ref(false)
const refreshing = ref(false)
const logoInput = ref(null)
const loginInput = ref(null)

async function load() {
  loading.value = true
  try {
    const data = await api('/settings')
    form.siteName = data?.siteName || ''
    form.logoUrl = data?.logoUrl || ''
    form.loginImageUrl = data?.loginImageUrl || ''
  } catch (e) {
    toastError(e)
  } finally {
    loading.value = false
  }
}

function pick(which) {
  const input = which === 'logo' ? logoInput.value : loginInput.value
  input?.click()
}

/** 上传图片并回填相对链接（/api/files/<id>/raw）。 */
async function onFile(event, which) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  try {
    const result = await upload('/files', file)
    if (which === 'logo') form.logoUrl = result.url
    else form.loginImageUrl = result.url
    toast('已上传，记得点击保存', 'success')
  } catch (e) {
    toastError(e)
  }
}

async function save() {
  if (!form.siteName.trim()) {
    toast('站点名不能为空', 'error')
    return
  }
  saving.value = true
  try {
    const data = await api('/settings', {
      method: 'PUT',
      body: {
        siteName: form.siteName.trim(),
        logoUrl: form.logoUrl || '',
        loginImageUrl: form.loginImageUrl || ''
      }
    })
    form.siteName = data?.siteName || form.siteName
    applyBrand(data)
    toast('设置已保存', 'success')
  } catch (e) {
    toastError(e)
  } finally {
    saving.value = false
  }
}

async function refreshBrandCache() {
  refreshing.value = true
  try {
    const { status, brand } = await api('/settings/brand-cache/refresh', { method: 'POST' })
    applyBrand(brand)
    if (status === 'unavailable') {
      toast('Redis 暂不可用，当前页面已从数据库更新，共享缓存未清理', 'error')
    } else {
      toast(status === 'disabled' ? 'Redis 未启用，已从数据库更新' : '品牌缓存已刷新', 'success')
    }
  } catch (e) {
    toastError(e)
  } finally {
    refreshing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="settings">
    <div class="card panel">
      <div class="head">
        <div>
          <h2>系统图像设置</h2>
          <p class="muted small">站点名显示在侧边栏与登录页；Logo 与品牌图支持相对链接、绝对链接，或直接上传。</p>
        </div>
      </div>

      <div v-if="loading" class="empty">加载中…</div>

      <form v-else class="form" @submit.prevent="save">
        <div class="field">
          <label>站点名</label>
          <input v-model="form.siteName" class="input" maxlength="60" placeholder="例如：毕业论文管理系统" />
        </div>

        <div class="field image-row">
          <label>侧边栏 Logo</label>
          <div class="image-editor">
            <img v-if="form.logoUrl" :src="form.logoUrl" class="preview small" alt="Logo 预览" />
            <div v-else class="preview small placeholder">无</div>
            <div class="image-editor-body">
              <input v-model="form.logoUrl" class="input" placeholder="图片链接，或点击上传" />
              <div class="actions">
                <button class="btn ghost sm" type="button" @click="pick('logo')">上传 Logo</button>
                <button v-if="form.logoUrl" class="btn ghost sm" type="button" @click="form.logoUrl = ''">清除</button>
              </div>
              <input ref="logoInput" type="file" accept="image/*" class="visually-hidden" @change="(e) => onFile(e, 'logo')" />
            </div>
          </div>
          <p class="muted small">建议 1:1 的 PNG/SVG，会以 34px 圆角方块展示。</p>
        </div>

        <div class="field image-row">
          <label>登录页品牌图</label>
          <div class="image-editor">
            <img v-if="form.loginImageUrl" :src="form.loginImageUrl" class="preview" alt="品牌图预览" />
            <div v-else class="preview placeholder">无</div>
            <div class="image-editor-body">
              <input v-model="form.loginImageUrl" class="input" placeholder="图片链接，或点击上传" />
              <div class="actions">
                <button class="btn ghost sm" type="button" @click="pick('login')">上传品牌图</button>
                <button v-if="form.loginImageUrl" class="btn ghost sm" type="button" @click="form.loginImageUrl = ''">清除</button>
              </div>
              <input ref="loginInput" type="file" accept="image/*" class="visually-hidden" @change="(e) => onFile(e, 'login')" />
            </div>
          </div>
          <p class="muted small">展示在登录页左侧品牌区，建议宽度 800px 以上的横图。</p>
        </div>

        <div class="actions-row">
          <button class="btn primary" type="submit" :disabled="saving || refreshing">
            {{ saving ? '保存中…' : '保存设置' }}
          </button>
          <button class="btn" type="button" :disabled="saving || refreshing" @click="refreshBrandCache">
            {{ refreshing ? '刷新中…' : '刷新页面' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.settings { max-width: 780px; display: grid; gap: 14px; }
.panel { padding: 20px; }
.head { margin-bottom: 16px; }
.head h2 { font-size: 20px; }
.form { display: grid; gap: 18px; max-width: 560px; }
.field label { display: block; margin-bottom: 6px; font-size: 13px; color: var(--muted); }
.image-editor { display: flex; gap: 14px; align-items: flex-start; }
.image-editor-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8px; }
.preview {
  width: 160px;
  height: 100px;
  border-radius: 10px;
  border: 1px solid var(--border);
  object-fit: cover;
  background: var(--panel);
  display: grid;
  place-items: center;
  color: var(--muted);
  font-size: 13px;
}
.preview.small { width: 64px; height: 64px; }
.placeholder { border-style: dashed; }
.actions { display: flex; flex-wrap: wrap; gap: 6px; }
.actions-row { margin-top: 4px; display: flex; flex-wrap: wrap; gap: 8px; }
.visually-hidden { display: none; }
</style>
