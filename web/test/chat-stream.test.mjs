/** 验证真实流读取路径：中文跨分块、事件顺序、服务端错误与缺失完成信号。 */
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { streamChat } from '../src/api/http.js'

globalThis.localStorage = { getItem: () => 'test-token', removeItem: () => {} }
function mockStream(events, chunkSize = 1) {
  const bytes = new TextEncoder().encode(events.map(e => JSON.stringify(e)).join('\n'))
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/ai/chat/stream')
    assert.equal(options.headers.Authorization, 'Bearer test-token')
    return new Response(new ReadableStream({
      start(controller) {
        for (let i = 0; i < bytes.length; i += chunkSize) controller.enqueue(bytes.slice(i, i + chunkSize))
        controller.close()
      }
    }))
  }
}
test('中文跨字节分块且末行无换行时，依次显示多个回复和工具', async () => {
  const expected = [
    { type: 'assistant', text: '先查询部门' },
    { type: 'tool', audit: { name: 'demo_query' } },
    { type: 'assistant', text: '查询已完成' },
    { type: 'done' }
  ]
  mockStream(expected)
  const actual = []
  await streamChat({ message: '查询' }, event => actual.push(event))
  assert.deepEqual(actual, expected)
})
test('模型失败保留前面的消息并报错', async () => {
  mockStream([{ type: 'assistant', text: '已收到' }, { type: 'error', message: '模型不可用' }])
  const actual = []
  await assert.rejects(streamChat({}, e => actual.push(e)), /模型不可用/)
  assert.equal(actual.length, 1)
})
test('提前断流不能误判为完成', async () => {
  mockStream([{ type: 'assistant', text: '正在处理' }])
  await assert.rejects(streamChat({}, () => {}), /对话连接中断/)
})
