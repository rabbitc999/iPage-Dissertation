const TOKEN_KEY = 'ims_token'

/**
 * 读取本地令牌。
 * @returns {string|null} 令牌
 */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

/**
 * 写入或清除令牌。
 * @param {string|null} token 令牌
 */
export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

/** 带 HTTP 状态的接口错误。 */
export class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

/**
 * 调用后端接口。
 * @param {string} path 以 / 开头的路径，会自动加 /api 前缀
 * @param {{method?:string, body?:any, params?:object, responseType?:string}} options 选项
 * @returns {Promise<any>} 响应体
 */
export async function api(path, options = {}) {
  const { method = 'GET', body, params, responseType } = options
  const url = new URL('/api' + path, window.location.origin)
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value === undefined || value === null || value === '') continue
      url.searchParams.set(key, value)
    }
  }

  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let response
  try {
    response = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    })
  } catch (error) {
    throw new ApiError('无法连接服务端，请确认后端已启动', 0)
  }

  if (response.ok && responseType === 'blob') return response.blob()
  const text = await response.text()
  let payload = null
  if (text) {
    try {
      payload = JSON.parse(text)
    } catch (error) {
      payload = { message: text }
    }
  }

  if (!response.ok) {
    if (response.status === 401) {
      setToken(null)
    }
    throw new ApiError(payload?.message || `请求失败（${response.status}）`, response.status)
  }
  return payload
}

/**
 * 上传文件（multipart/form-data）。
 * <p>
 * 注意不要手动设置 Content-Type：浏览器需要自己生成带 boundary 的头。
 *
 * @param {string} path 以 / 开头的路径，会自动加 /api 前缀
 * @param {File}   file 用户选择的文件
 * @returns {Promise<any>} 响应体（含 id/name/url 等字段）
 */
export async function upload(path, file) {
  const form = new FormData()
  form.append('file', file)
  const headers = { Accept: 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let response
  try {
    response = await fetch(new URL('/api' + path, window.location.origin), {
      method: 'POST',
      headers,
      body: form
    })
  } catch (error) {
    throw Object.assign(new ApiError('无法连接服务端，请确认后端已启动', 0), { detail: String(error) })
  }
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(payload?.message || `上传失败（${response.status}）`, response.status)
  }
  return payload
}

/**
 * 把对象拼成查询串，空值自动忽略。
 * @param {object} params 参数
 * @returns {string} 查询串
 */
export function query(params) {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    search.set(key, value)
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

/** 消费逐行 JSON 对话事件；TextDecoder 保留跨网络分块的中文字符。 */
export async function streamChat(body, onEvent) {
  const headers = { Accept: 'application/x-ndjson', 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  let response
  try {
    response = await fetch('/api/ai/chat/stream', {
      method: 'POST', headers, body: JSON.stringify(body)
    })
  } catch (cause) {
    throw Object.assign(new ApiError('无法连接服务端', 0), { detail: String(cause) })
  }
  if (!response.ok) {
    if (response.status === 401) setToken(null)
    const error = await response.json().catch(() => null)
    throw new ApiError(error?.message || `请求失败（${response.status}）`, response.status)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let pending = ''
  let finished = false
  const consume = line => {
    if (!line.trim()) return
    const event = JSON.parse(line)
    if (event.type === 'error') {
      throw Object.assign(new ApiError(event.message || '模型调用失败', event.status), {
        source: event.source,
        detail: event.detail
      })
    }
    if (event.type === 'done') finished = true
    onEvent(event)
  }
  try {
    while (true) {
      const { value, done } = await reader.read()
      pending += done ? decoder.decode() : decoder.decode(value, { stream: true })
      let end
      while ((end = pending.indexOf('\n')) >= 0) {
        consume(pending.slice(0, end))
        pending = pending.slice(end + 1)
      }
      if (done) break
    }
    consume(pending)
    if (!finished) throw new Error('对话连接中断，已显示的回复和已执行的工具操作仍然有效')
  } finally {
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}
