<script setup>
/** 密码验证和最终确认分别提交，验证成功后立即清除表单密码。 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/http'
import { toast } from '../api/toast'
import { logout } from '../stores/auth'

const router = useRouter()
const phrase = '清空所有数据'
const password = ref('')
const confirmation = ref('')
const token = ref('')
const busy = ref(false)
const error = ref('')
const confirmError = ref('')
const dialog = ref(null)
const expiresInSeconds = ref(0)

async function verify() {
  if (busy.value || !password.value) return
  busy.value = true
  error.value = ''
  try {
    const result = await api('/system/data-reset/verify', { method: 'POST', body: { password: password.value } })
    token.value = result.token
    expiresInSeconds.value = result.expiresInSeconds
    dialog.value.showModal()
  } catch (failure) {
    error.value = failure.message
  } finally {
    password.value = ''
    busy.value = false
  }
}

function cancel() {
  if (!busy.value) dialog.value.close()
}

function clearConfirmation() {
  token.value = ''
  confirmation.value = ''
  confirmError.value = ''
}

async function execute() {
  if (busy.value || !token.value || confirmation.value !== phrase) return
  busy.value = true
  confirmError.value = ''
  try {
    const result = await api('/system/data-reset/execute', {
      method: 'POST', body: { token: token.value, confirmation: confirmation.value }
    })
    dialog.value.close()
    await logout()
    await router.replace('/login')
    toast(`已清空 ${result.deleted} 条记录，请重新登录`, 'success')
  } catch (failure) {
    if (failure.status === 400) {
      dialog.value.close()
      error.value = failure.message
    } else {
      confirmError.value = failure.message
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="card reset-card">
    <h2>清空系统数据</h2>
    <div class="warning">
      <strong>此操作不可撤销</strong>
      <p>将删除业务记录、其他用户、附件、审计历史、AI 模型及密钥和系统设置。</p>
      <p>保留当前超级管理员的账号、姓名和密码，并恢复内置角色和权限。清空后需重新登录。</p>
    </div>

    <form class="reset-form" @submit.prevent="verify">
      <div class="field">
        <label for="reset-password">再次输入当前账号的密码</label>
        <input id="reset-password" v-model="password" class="input" type="password"
               autocomplete="off" maxlength="1024" required :disabled="busy" />
      </div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <button class="btn primary" type="submit" :disabled="busy || !password">验证密码</button>
    </form>

    <dialog ref="dialog" class="card confirmation-dialog" aria-labelledby="reset-confirm-title"
            @cancel.prevent="cancel" @close="clearConfirmation">
      <form @submit.prevent="execute">
        <h3 id="reset-confirm-title">二次确认</h3>
        <p>密码已验证。请在 {{ expiresInSeconds }} 秒内输入「{{ phrase }}」，然后点击确认。</p>
        <div class="field">
          <label for="reset-confirmation">确认文字</label>
          <input id="reset-confirmation" v-model="confirmation" class="input" autocomplete="off"
                 :disabled="busy" required autofocus />
        </div>
        <p v-if="confirmError" class="error" role="alert">{{ confirmError }}</p>
        <div class="row actions">
          <button class="btn" type="button" :disabled="busy" @click="cancel">取消</button>
          <button class="btn danger" type="submit" :disabled="busy || confirmation !== phrase">
            {{ busy ? '正在清空…' : '确认清空' }}
          </button>
        </div>
      </form>
    </dialog>
  </section>
</template>

<style scoped>
.reset-card { max-width: 720px; padding: 22px; }
.warning { margin: 14px 0 20px; padding: 14px; border: 1px solid var(--danger); border-radius: 8px; background: var(--danger-weak); }
.warning strong, .error { color: var(--danger); }
.warning p { margin: 5px 0 0; }
.reset-form { max-width: 360px; }
.confirmation-dialog { position: fixed; width: min(460px, calc(100vw - 32px)); padding: 20px; color: var(--text); }
.confirmation-dialog::backdrop { background: rgb(12 16 28 / 55%); }
.confirmation-dialog p { margin: 10px 0; }
.actions { justify-content: flex-end; }
</style>
