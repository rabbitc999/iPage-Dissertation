import { reactive } from 'vue'
import { api } from '../api/http'

/**
 * 站点品牌信息（来自公开接口 /api/brand）。
 * <p>
 * 登录页与侧边栏都读这里；接口未配置的键给出默认值，
 * 因此接口挂掉时页面也不会缺标题。
 */
export const brand = reactive({
  siteName: '信息管理系统',
  logoUrl: '',
  loginImageUrl: '',
  loaded: false
})

/**
 * 拉取品牌信息；失败时保持默认值（登录页在无后端兜底时也能渲染）。
 * @returns {Promise<void>}
 */
export async function loadBrand() {
  try {
    const data = await api('/brand')
    if (data?.siteName) brand.siteName = data.siteName
    brand.logoUrl = data?.logoUrl || ''
    brand.loginImageUrl = data?.loginImageUrl || ''
    brand.loaded = true
  } catch (error) {
    // 静默失败：公开接口不可用时展示内置默认值
  }
}
