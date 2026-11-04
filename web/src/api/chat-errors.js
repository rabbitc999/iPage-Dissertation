/** 解释对话错误，保留来源和原始诊断，避免混淆登录权限与模型服务鉴权。 */
const modelMessages = {
  0: '服务器暂时连不上模型服务，请检查服务地址和服务器网络。',
  400: '模型服务无法处理这条消息，请检查所选模型是否支持当前附件和工具。',
  401: '模型服务的身份验证失败，请联系管理员检查服务端 API 密钥。',
  402: '模型服务账户可能余额不足，请联系管理员检查计费状态。',
  403: '模型服务拒绝了请求，请联系管理员检查该密钥是否有权使用所选模型。',
  404: '模型服务找不到所选模型或接口，请尝试切换模型，或联系管理员检查模型 ID 和服务地址。',
  408: '等待模型回复超时。可以稍后重试，或缩短消息再发送。',
  413: '发送给模型的内容过大，请减少附件或缩短消息。',
  422: '模型服务无法处理当前内容，请检查图片格式和所选模型的能力。',
  429: '模型服务暂时限制了请求频率，或用量已达上限。请稍后重试，或联系管理员检查额度。'
}

const appMessages = {
  0: '浏览器暂时连不上 iPage-IMS，请检查网络和系统服务是否在线。',
  401: '登录已失效，请重新登录后再发送。',
  403: '当前账号没有执行此操作的权限，请检查所选模型或联系管理员。',
  404: '找不到所请求的接口或附件，请刷新页面或重新选择附件。',
  413: '附件超过上传大小限制，请选择更小的文件。',
  429: '系统收到的请求过多，请稍后再试。'
}

export function describeChatError(error) {
  const raw = error?.message || '未知错误'
  const legacy = raw.match(/HTTP(?:\s+status)?\s*(\d{1,3})\b/i)
  const status = error?.status ?? (legacy ? Number(legacy[1]) : undefined)
  const model = error?.source === 'model' || (legacy && error?.status == null)
  const detail = error?.detail || raw
  const code = Number.isInteger(status) && status >= 0 ? 'HTTP ' + status : ''
  const friendly = model
    ? modelMessages[status] || (status >= 500 ? '模型服务暂时无法响应，请稍后重试。' : '模型回复失败，请查看错误详情。')
    : appMessages[status] || (status >= 500 ? '系统暂时无法完成请求，请稍后重试。' : raw)
  return { text: friendly, detail: [code, detail].filter(Boolean).join(' · ') }
}
