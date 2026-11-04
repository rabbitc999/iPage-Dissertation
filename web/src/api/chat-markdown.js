// 将助手回复转换为 HTML，同时禁止原始 HTML 和需要信任的 LaTeX 命令。
import MarkdownIt from 'markdown-it'
import { katex } from '@mdit/plugin-katex'

const markdown = new MarkdownIt({ html: false, breaks: true })
  .use(katex, { delimiters: 'all', trust: false, throwOnError: false })

export function renderChatMarkdown(text) {
  return markdown.render(text ?? '')
}
