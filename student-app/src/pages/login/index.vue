<template>
  <view class="login">
    <view class="login__deco login__deco--a"></view>
    <view class="login__deco login__deco--b"></view>

    <view class="brand">
      <view class="brand__logo">🌿</view>
      <text class="brand__title">全国植物观察与交流平台</text>
      <text class="brand__sub">学生端 · 记录身边的植物</text>
    </view>

    <view class="panel">
      <view class="field">
        <text class="field__label">账号</text>
        <input v-model="username" class="field__ipt" placeholder="学号 / 用户名" placeholder-class="ph" />
      </view>
      <view class="field">
        <text class="field__label">密码</text>
        <input v-model="password" class="field__ipt" password placeholder="请输入密码" placeholder-class="ph" />
      </view>
      <button class="btn-primary btn-block login__btn" :loading="loading" @tap="submit">登 录</button>
      <text class="tip">登录态安全保存在本机，不保存明文密码</text>
    </view>

    <view class="server" @tap="openServerSetting">
      <text class="server__label">服务器：{{ apiBaseLabel }}</text>
      <text class="server__link">设置 ›</text>
    </view>
    <text class="tip tip--bottom">若打不开页面或提示网络异常，请点「设置」填写后端地址（如 http://192.168.1.5），需与后端处于同一网络。</text>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue"
import { useAuthStore } from "@/stores/auth"
import { getApiBase, setApiBase } from "@/config"

const auth = useAuthStore()
const username = ref("")
const password = ref("")
const loading = ref(false)
const apiBase = ref(getApiBase())

const apiBaseLabel = computed(() => apiBase.value || "同源（跟随当前页面地址）")

function openServerSetting() {
  uni.showModal({
    title: "后端服务器地址",
    editable: true,
    placeholderText: "如 http://10.120.46.175 或 http://192.168.1.5:8080",
    content: apiBase.value,
    success: (res) => {
      if (!res.confirm) return
      const saved = setApiBase(res.content || "")
      apiBase.value = saved
      uni.showToast({ title: saved ? "已保存：" + saved : "已恢复默认（同源）", icon: "none" })
    },
  })
}

async function submit() {
  if (!username.value || !password.value) {
    uni.showToast({ title: "请输入账号和密码", icon: "none" })
    return
  }
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    uni.showToast({ title: "登录成功", icon: "success" })
    setTimeout(() => uni.switchTab({ url: "/pages/home/index" }), 400)
  } catch {
    // toast 已由 request 层提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  padding: calc(var(--status-bar-height, 0px) + 120rpx) 48rpx 60rpx;
  box-sizing: border-box;
  background: linear-gradient(160deg, #4aa64a 0%, #2f7a34 52%, #235f29 100%);
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}
.login__deco { position: absolute; border-radius: 50%; background: rgba(255, 255, 255, 0.08); }
.login__deco--a { width: 420rpx; height: 420rpx; top: -140rpx; right: -140rpx; }
.login__deco--b { width: 300rpx; height: 300rpx; bottom: 200rpx; left: -120rpx; background: rgba(255, 255, 255, 0.06); }
.brand { display: flex; flex-direction: column; align-items: center; gap: 12rpx; position: relative; }
.brand__logo {
  width: 128rpx; height: 128rpx; border-radius: 36rpx; background: rgba(255, 255, 255, 0.16);
  display: flex; align-items: center; justify-content: center; font-size: 64rpx; margin-bottom: 8rpx;
}
.brand__title { font-size: 42rpx; font-weight: 700; color: #ffffff; text-align: center; }
.brand__sub { font-size: 25rpx; color: rgba(255, 255, 255, 0.8); }
.panel {
  background: #ffffff; border-radius: 32rpx; padding: 40rpx 34rpx 30rpx; margin-top: 60rpx;
  box-shadow: 0 20rpx 50rpx rgba(16, 46, 20, 0.22); display: flex; flex-direction: column; gap: 24rpx;
  position: relative;
}
.field { display: flex; flex-direction: column; gap: 10rpx; }
.field__label { font-size: 25rpx; color: #55645a; font-weight: 600; }
.field__ipt {
  background: #f6f9f4; border: 1rpx solid #e6efe3; border-radius: 18rpx;
  padding: 24rpx; font-size: 30rpx; color: #1f2d24;
}
.ph { color: #a8b3aa; }
.login__btn { margin-top: 12rpx; }
.tip { font-size: 22rpx; color: #8a968c; text-align: center; }
.tip--bottom { color: rgba(255, 255, 255, 0.7); margin-top: 18rpx; line-height: 1.6; }
.server {
  margin-top: 40rpx; display: flex; align-items: center; justify-content: center; gap: 12rpx;
  font-size: 24rpx; color: rgba(255, 255, 255, 0.85); position: relative;
}
.server__link { color: #ffffff; text-decoration: underline; }
</style>
