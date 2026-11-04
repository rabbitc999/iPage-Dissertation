/** 供应商预设只提供接口协议和地址，模型 ID 与密钥由管理员填写。 */
export const providers = [
  { name: 'DeepSeek', protocol: 'openai', url: 'https://api.deepseek.com', docs: 'https://api-docs.deepseek.com/' },
  { name: 'OpenAI', protocol: 'openai', url: 'https://api.openai.com/v1', docs: 'https://developers.openai.com/api/reference/chat-completions/overview' },
  { name: 'Anthropic Claude', protocol: 'anthropic', url: 'https://api.anthropic.com/v1', docs: 'https://platform.claude.com/docs/en/api/overview' },
  { name: '阿里云百炼 / 通义千问（中国北京）', protocol: 'openai', url: 'https://dashscope.aliyuncs.com/compatible-mode/v1', docs: 'https://www.alibabacloud.com/help/en/model-studio/base-url' },
  { name: 'Google Gemini（OpenAI 兼容）', protocol: 'openai', url: 'https://generativelanguage.googleapis.com/v1beta/openai', docs: 'https://ai.google.dev/gemini-api/docs/openai' },
  { name: '智谱 AI / GLM', protocol: 'openai', url: 'https://open.bigmodel.cn/api/paas/v4', docs: 'https://docs.bigmodel.cn/cn/best-practice/case/ai-search-engine' }
]
