<script setup>
/**
 * 登录页。
 * <p>
 * 左侧品牌区在窄屏隐藏，右侧表单由用户填写登录凭据。
 * 登录成功后按 {@code ?redirect=} 回跳，没有则进工作台。
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../stores/auth'
import { brand, loadBrand } from '../stores/brand'

const router = useRouter()
const route = useRoute()
const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await login(username.value.trim(), password.value)
    const target = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(target)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (localStorage.getItem('ims_theme') === 'dark') {
    document.documentElement.dataset.theme = 'dark'
  }
  // 公开接口，登录前就能取到站点名与品牌图；失败时用内置默认值
  loadBrand()
})
</script>

<template>
  <div class="login">
    <section class="brand-side">
      <div class="brand-inner">
        <img v-if="brand.logoUrl" :src="brand.logoUrl" class="mark mark-img" alt="logo" />
        <div v-else class="mark">论</div>
        <h1>{{ brand.siteName }}</h1>
        <img v-if="brand.loginImageUrl" :src="brand.loginImageUrl" class="login-image" alt="品牌图" />
        <p class="lead">
          从选题、答辩到成绩归档，一套系统管到底。<br />
          院系、专业、班级、学生、教师与论文的关系自动维护。
        </p>
        <ul class="points">
          <li><b>选题</b> 一名学生一篇论文，状态与题目联动校验</li>
          <li><b>成绩</b> 答辩、论文分项录入，总成绩按权重自动合成</li>
          <li><b>权限</b> 角色等级 + 权限点 + 数据范围三层控制</li>
          <li><b>AI</b> 用自然语言查询与维护数据，受同一套权限约束</li>
        </ul>
      </div>
    </section>

    <section class="form-side">
      <div class="form-card">
        <h2>登录</h2>
        <p class="muted small">请输入账号和密码登录系统</p>

        <form autocomplete="off" @submit.prevent="submit">
          <div class="field">
            <label for="login-username">账号</label>
            <input id="login-username" v-model="username" class="input" autocomplete="off" placeholder="请输入账号" />
          </div>
          <div class="field">
            <label for="login-password">密码</label>
            <input id="login-password" v-model="password" class="input" type="password" autocomplete="off" placeholder="请输入密码" />
          </div>

          <div v-if="error" class="alert">{{ error }}</div>

          <button class="btn primary block" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : '登录' }}
          </button>
        </form>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login { min-height: 100vh; display: grid; grid-template-columns: 1.05fr 1fr; }

.brand-side {
  background:
    radial-gradient(900px 500px at 10% 10%, rgba(99, 102, 241, 0.45), transparent 60%),
    radial-gradient(700px 500px at 90% 90%, rgba(34, 211, 238, 0.25), transparent 60%),
    #0d1220;
  color: #e7ebf5;
  display: flex;
  align-items: center;
  padding: 8vw 6vw;
}
.brand-inner { max-width: 460px; }
.mark {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, #6366f1, #22d3ee);
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 22px;
  margin-bottom: 22px;
}
.brand-side h1 { font-size: 32px; letter-spacing: -0.02em; }
.mark-img { object-fit: cover; }
.login-image {
  width: 100%;
  max-width: 460px;
  border-radius: 14px;
  margin-top: 22px;
  object-fit: cover;
}
.lead { color: #a9b2c8; margin: 14px 0 26px; line-height: 1.8; }
.points { list-style: none; padding: 0; margin: 0; display: grid; gap: 10px; color: #c6cddd; }
.points li { padding-left: 18px; position: relative; }
.points li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22d3ee;
}
.points b { color: #fff; font-weight: 600; }

.form-side { display: flex; align-items: center; justify-content: center; padding: 8vw 5vw; background: var(--bg); }
.form-card { width: 100%; max-width: 360px; }
.form-card h2 { font-size: 24px; margin-bottom: 4px; }
form { margin-top: 22px; }
.block { width: 100%; height: 40px; margin-top: 6px; }
.alert {
  margin: 4px 0 10px;
  padding: 9px 12px;
  border-radius: 9px;
  background: var(--danger-weak);
  color: var(--danger);
  font-size: 13px;
}
@media (max-width: 860px) {
  .login { grid-template-columns: 1fr; }
  .brand-side { display: none; }
  .form-side { min-height: 100vh; }
}
</style>
