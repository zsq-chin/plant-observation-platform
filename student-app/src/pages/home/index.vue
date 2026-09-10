<template>
  <view class="page">
    <view class="hello">
      <view class="hi">你好，{{ auth.displayName }}</view>
      <view v-if="auth.className" class="muted">{{ auth.className }}</view>
    </view>
    <view v-if="pendingLocal > 0" class="pending" @tap="goLocalDrafts">
      本地待同步草稿 {{ pendingLocal }} 条，点击联网同步
    </view>
    <view class="stats" v-if="dash">
      <view class="stat" @tap="goTab('DRAFT')"><text>{{ dash.draftCount }}</text><text>待完善</text></view>
      <view class="stat" @tap="goTab('SUBMITTED')"><text>{{ dash.submittedCount }}</text><text>待审核</text></view>
      <view class="stat" @tap="goTab('APPROVED')"><text>{{ dash.approvedCount }}</text><text>已通过</text></view>
      <view class="stat" @tap="goTab('REJECTED')"><text>{{ dash.rejectedCount }}</text><text>被驳回</text></view>
    </view>
    <button type="primary" class="big" @tap="capture">＋ 采集植物</button>
    <view class="sec-head">最新优秀观察</view>
    <view v-for="item in featured" :key="String(item.observationId)">
      <ObservationCard :item="item" @open="openDetail" />
    </view>
    <view class="sec-head">快捷入口</view>
    <view class="links">
      <button size="mini" @tap="switchTab('gallery')">植物展廊</button>
      <button size="mini" @tap="switchTab('map')">全国植物</button>
      <button size="mini" @tap="goNotifications">消息</button>
      <button size="mini" @tap="goSuggestion">新物种建议</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from "@dcloudio/uni-app"
import { ref } from "vue"
import { useAuthStore } from "@/stores/auth"
import { fetchHome, fetchStudentDashboard } from "@/api/plant"
import ObservationCard from "@/components/ObservationCard.vue"
import { readLocalDrafts } from "@/utils/storage"
import type { GalleryItem, StudentDashboard } from "@/types/models"

const auth = useAuthStore()
const dash = ref<StudentDashboard | null>(null)
const featured = ref<GalleryItem[]>([])
const pendingLocal = ref(0)

onShow(() => {
  if (!auth.isLoggedIn) {
    uni.reLaunch({ url: "/pages/login/index" })
    return
  }
  pendingLocal.value = readLocalDrafts().length
  fetchStudentDashboard().then((d) => (dash.value = d)).catch(() => undefined)
  fetchHome()
    .then((h) => (featured.value = h.featuredObservations || []))
    .catch(() => undefined)
})

function capture() {
  uni.navigateTo({ url: "/pages/capture/index" })
}
function openDetail(id: string) {
  uni.navigateTo({ url: "/pages/observation-detail/index?id=" + id })
}
function goTab(status: string) {
  uni.switchTab({ url: "/pages/my-observations/index" })
  setTimeout(() => {
    uni.$emit("plant-status", status)
  }, 300)
}
function goLocalDrafts() {
  uni.switchTab({ url: "/pages/my-observations/index" })
  setTimeout(() => uni.$emit("plant-status", "LOCAL"), 300)
}
function switchTab(name: string) {
  uni.switchTab({ url: name === "gallery" ? "/pages/gallery/index" : "/pages/map/index" })
}
function goNotifications() {
  uni.navigateTo({ url: "/pages/notification/index" })
}
function goSuggestion() {
  uni.navigateTo({ url: "/pages/suggestion/index" })
}
</script>

<style scoped>
.page { padding: 24rpx; display: flex; flex-direction: column; gap: 16rpx; }
.hi { font-size: 40rpx; font-weight: 700; }
.muted { color: #888; }
.pending { background: #fff7e6; color: #b7791f; padding: 16rpx; border-radius: 12rpx; }
.stats { display: flex; gap: 12rpx; }
.stat { flex: 1; background: #fff; border-radius: 14rpx; padding: 18rpx 0; display: flex; flex-direction: column; align-items: center; }
.stat text:first-child { font-size: 36rpx; font-weight: 700; color: #3f9b3f; }
.stat text:last-child { font-size: 22rpx; color: #888; }
.big { margin: 8rpx 0; }
.sec-head { font-size: 30rpx; font-weight: 600; margin-top: 12rpx; }
.links { display: flex; flex-wrap: wrap; gap: 12rpx; }
</style>
