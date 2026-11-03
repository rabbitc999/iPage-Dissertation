<script setup>
/**
 * 个人设置：账号信息、权限点清单、修改密码。
 * <p>
 * 改密成功后服务端会返回新令牌（旧令牌立即失效），这里必须替换本地令牌，
 * 否则下一个请求就会 401。
 */
import { reactive, ref } from 'vue'
import { auth, changePassword, loadMe } from '../stores/auth'
import { api, upload } from '../api/http'
import { toast, toastError } from '../api/toast'

const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const saving = ref(false)
const uploading = ref(false)
const fileInput = ref(null)

const scopeLabels = { ALL: '全部数据', DEPT: '本部门', SELF: '仅本人' }

/** 触发文件选择框。 */
function pickAvatar() {
  fileInput.value?.click()
}

/**
 * 上传头像并刷新当前用户快照，让侧边栏头像立即生效。
 */
async function onAvatarChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  uploading.value = true
  try {
    await upload('/profile/avatar', file)
    await loadMe()
    toast('头像已更新', 'success')
  } catch (e) {
    toastError(e)
  } finally {
    uploading.value = false
  }
}

/** 清除头像（保留历史文件记录，只把引用置空）。 */
async function clearAvatar() {
  uploading.value = true
  try {
    await api('/profile/avatar', { method: 'DELETE' })
    await loadMe()
    toast('头像已清除', 'success')
  } catch (e) {
    toastError(e)
  } finally {
    uploading.value = false
  }
}

function levelText(level) {
  if (level >= 4) return '超级管理员'
  if (level === 3) return '管理员'
  if (level === 2) return '部门主管'
  if (level === 1) return '普通员工'
  return '受限账号'
}

async function submit() {
  if (form.newPassword.length < 6) {
    toast('新密码至少 6 位', 'error')
    return
  }
  if (form.newPassword !== form.confirm) {
    toast('两次输入的新密码不一致', 'error')
    return
  }
  saving.value = true
  try {
    await changePassword(form.oldPassword, form.newPassword)
    form.oldPassword = ''
    form.newPassword = ''
    form.confirm = ''
    toast('密码已修改，其他设备上的登录已失效', 'success')
  } catch (e) {
    toastError(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="profile">
    <div class="card info">
      <div class="avatar-wrap">
        <img v-if="auth.user?.avatar" :src="auth.user.avatar" class="avatar avatar-img" alt="头像" />
        <div v-else class="avatar">{{ (auth.user?.name || '?').slice(0, 1) }}</div>
        <input ref="fileInput" type="file" accept="image/*" class="visually-hidden" @change="onAvatarChange" />
        <div class="avatar-actions">
          <button class="btn ghost sm" :disabled="uploading" @click="pickAvatar">
            {{ uploading ? '处理中…' : '更换头像' }}
          </button>
          <button v-if="auth.user?.avatar" class="btn ghost sm" :disabled="uploading" @click="clearAvatar">
            清除
          </button>
        </div>
      </div>
      <div>
        <h2>{{ auth.user?.name }}</h2>
        <p class="muted small">{{ auth.user?.username }}</p>
        <div class="row wrap chips">
          <span class="badge">{{ levelText(auth.user?.level) }}</span>
          <span class="badge grey">数据范围：{{ scopeLabels[auth.user?.dataScope] || '仅本人' }}</span>
          <span v-if="auth.user?.isAdmin" class="badge grey">管理员</span>
        </div>
      </div>
    </div>

    <div class="card panel">
      <h3>权限点</h3>
      <p class="muted small">权限点控制功能入口；模型级权限由角色等级决定，数据范围决定能看到哪些行。</p>
      <div class="perm-list">
        <span v-for="code in auth.user?.permissions || []" :key="code" class="badge grey mono">{{ code }}</span>
        <span v-if="!auth.user?.permissions?.length" class="muted small">没有额外权限点</span>
      </div>
      <p v-if="auth.user?.isAdmin" class="muted small">管理员默认可以进入所有管理入口。</p>
    </div>

    <div class="card panel">
      <h3>修改密码</h3>
      <p class="muted small">修改成功后当前令牌会更新，其他设备需要重新登录。</p>
      <form class="pwd" @submit.prevent="submit">
        <div class="field">
          <label>当前密码</label>
          <input v-model="form.oldPassword" class="input" type="password" autocomplete="current-password" />
        </div>
        <div class="field">
          <label>新密码</label>
          <input v-model="form.newPassword" class="input" type="password" autocomplete="new-password" />
        </div>
        <div class="field">
          <label>确认新密码</label>
          <input v-model="form.confirm" class="input" type="password" autocomplete="new-password" />
        </div>
        <button class="btn primary" type="submit" :disabled="saving">
          {{ saving ? '保存中…' : '修改密码' }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.profile { display: grid; gap: 14px; max-width: 780px; }
.info { display: flex; gap: 18px; align-items: center; padding: 20px; }
.avatar-wrap { display: flex; flex-direction: column; gap: 8px; align-items: center; }
.avatar-actions { display: flex; gap: 6px; }
.visually-hidden { display: none; }
.avatar {
  width: 58px;
  height: 58px;
  border-radius: 18px;
  background: linear-gradient(135deg, #6366f1, #22d3ee);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 22px;
  font-weight: 700;
}
.avatar-img { object-fit: cover; }
.info h2 { font-size: 20px; }
.chips { margin-top: 8px; }
.panel { padding: 18px 20px; }
.perm-list { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.pwd { max-width: 340px; margin-top: 12px; }
</style>
