import { reactive } from 'vue'
import { api, getToken, setToken } from '../api/http'

/**
 * 登录状态。
 * 只保存令牌与用户快照，权限布尔量由服务端在 schema 里下发，前端不自己算权限。
 */
export const auth = reactive({
  token: getToken(),
  user: null,
  ready: false
})

/**
 * 登录。
 * @param {string} username 账号
 * @param {string} password 密码
 * @returns {Promise<object>} 用户
 */
export async function login(username, password) {
  const result = await api('/auth/login', { method: 'POST', body: { username, password } })
  setToken(result.token)
  auth.token = result.token
  auth.user = result.user
  auth.ready = true
  return result.user
}

/**
 * 拉取当前用户；令牌失效时清空。
 * @returns {Promise<object|null>} 用户
 */
export async function loadMe() {
  if (!auth.token) {
    auth.ready = true
    return null
  }
  try {
    auth.user = await api('/auth/me')
  } catch (error) {
    auth.token = null
    auth.user = null
    setToken(null)
  } finally {
    auth.ready = true
  }
  return auth.user
}

/**
 * 退出登录。
 */
export async function logout() {
  try {
    await api('/auth/logout', { method: 'POST' })
  } catch (error) {
    // 令牌已失效时忽略
  }
  setToken(null)
  auth.token = null
  auth.user = null
}

/**
 * 修改密码，成功后替换本地令牌。
 * @param {string} oldPassword 旧密码
 * @param {string} newPassword 新密码
 */
export async function changePassword(oldPassword, newPassword) {
  const result = await api('/auth/password', {
    method: 'POST',
    body: { oldPassword, newPassword }
  })
  setToken(result.token)
  auth.token = result.token
  await loadMe()
}

/**
 * 当前用户是否拥有权限点（超级管理员的判断由服务端在登录时给出 isAdmin）。
 * @param {string} code 权限编码
 * @returns {boolean} 拥有返回 true
 */
export function can(code) {
  if (!auth.user) return false
  if (auth.user.isAdmin) return true
  return (auth.user.permissions || []).includes(code)
}
