/** 对话警告保留模型服务状态，避免把上游 404 误当成 IMS 附件丢失。 */
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { describeChatError } from '../src/api/chat-errors.js'

test('模型接口和 IMS 附件的 404 提供不同提示', () => {
  const model = describeChatError({ source: 'model', status: 404, detail: 'HTTP status 404' })
  const app = describeChatError({ source: 'app', status: 404, message: '附件不存在' })
  assert.match(model.text, /模型 ID/)
  assert.match(model.detail, /HTTP status 404/)
  assert.match(app.text, /附件/)
})

test('HTTP 0 和 429 提供可理解的连接和用量提示', () => {
  assert.match(describeChatError(new Error('请求失败（HTTP 0）')).text, /连不上模型服务/)
  assert.match(describeChatError({ source: 'model', status: 429 }).text, /频率|用量/)
})
