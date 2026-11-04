/** 轻量功能状态用于路由和入口显隐。权限仍由后端检查。 */
import { reactive } from 'vue'
import { api } from '../api/http'

export const features = reactive({ ai: false, aiAvailable: false })

export async function loadFeatures() {
  try {
    const module = await api('/ai/module')
    features.ai = module.enabled === true
    features.aiAvailable = module.available === true
  } catch {
    features.ai = false
    features.aiAvailable = false
  }
}
