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
 * @param {{method?:string, body?:any, params?:object}} options 选项
 * @returns {Promise<any>} 响应体
 */
export async function api(path, options = {}) {
  const { method = 'GET', body, params } = options
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
