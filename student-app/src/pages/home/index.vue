<template>
  <view class="page page--hero">
    <AppHero title="你好，{{ auth.displayName }}" :subtitle="auth.className || '记录身边的植物，让观察被看见'">
      <template #right>
        <view class="avatar" @tap="goProfile">{{ avatarText }}</view>
      </template>
    </AppHero>

    <view class="lift">
      <view v-if="pendingLocal > 0" class="notice" @tap="goLocalDrafts">
        <text class="notice__icon">☁️</text>
        <view class="grow">
          <text class="notice__title">有 {{ pendingLocal }} 条本地草稿待同步</text>
          <text class="notice__hint">点击联网同步，避免照片丢失</text>
        </view>
        <text class="item__arrow">›</text>
      </view>

      <view class="stats" v-if="dash">
        <view class="stat stat--draft" @tap="goTab('DRAFT')">
          <text class="stat__num">{{ dash.draftCount }}</text><text class="stat__label">待完善</text>
        </view>
        <view class="stat stat--submitted" @tap="goTab('SUBMITTED')">
          <text class="stat__num">{{ dash.submittedCount }}</text><text class="stat__label">待审核</text>
        </view>
        <view class="stat stat--approved" @tap="goTab('APPROVED')">
          <text class="stat__num">{{ dash.approvedCount }}</text><text class="stat__label">已通过</text>
        </view>
        <view class="stat stat--rejected" @tap="goTab('REJECTED')">
          <text class="stat__num">{{ dash.rejectedCount }}</text><text class="stat__label">被驳回</text>
        </view>
      </view>

      <button class="btn-primary btn-block cta" @tap="capture">📷 采集一株植物</button>

      <view class="sec"><text class="sec__title">快捷入口</text></view>
      <view class="tiles">
        <view class="tile" @tap="goMap">
          <text class="tile__icon">🗺️</text><text class="tile__text">全国地图</text>
        </view>
        <view class="tile" @tap="goGallery">
          <text class="tile__icon">🖼️</text><text class="tile__text">植物展廊</text>
        </view>
        <view class="tile" @tap="goMine">
          <text class="tile__icon">🌿</text><text class="tile__text">我的植物</text>
        </view>
        <view class="tile" @tap="goNotifications">
          <text class="tile__icon">🔔</text><text class="tile__text">消息通知</text>
        </view>
        <view class="tile" @tap="goSuggestion">
          <text class="tile__icon">💡</text><text class="tile__text">新物种建议</text>
        </view>
        <view class="tile" @tap="goProfile">
          <text class="tile__icon">👤</text><text class="tile__text">我的</text>
        </view>
      </view>

      <view class="sec">
        <text class="sec__title">最新优秀观察</text>
        <text class="sec__more" @tap="goGallery">全部 ›</text>
      </view>
      <view v-for="item in featured" :key="String(item.observationId)">
        <ObservationCard :item="item" @open="openDetail" />
      </view>
      <EmptyState v-if="!featured.length" icon="🌼" title="还没有优秀观察" hint="先去采集，通过审核后就有机会登上首页" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from "@dcloudio/uni-app"
import { computed, ref } from "vue"
import { useAuthStore } from "@/stores/auth"
import { fetchHome, fetchStudentDashboard } from "@/api/plant"
import AppHero from "@/components/AppHero.vue"
import EmptyState from "@/components/EmptyState.vue"
import ObservationCard from "@/components/ObservationCard.vue"
import { readLocalDrafts } from "@/utils/storage"
import type { GalleryItem, StudentDashboard } from "@/types/models"

const auth = useAuthStore()
const dash = ref<StudentDashboard | null>(null)
const featured = ref<GalleryItem[]>([])
const pendingLocal = ref(0)

const avatarText = computed(() => (auth.displayName || "同").slice(0, 1))

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
/** 全国植物地图不是 tabBar 页面，必须用 navigateTo；switchTab 会静默失败 */
function goMap() {
  uni.navigateTo({ url: "/pages/map/index" })
}
function goGallery() {
  uni.switchTab({ url: "/pages/gallery/index" })
}
function goMine() {
  uni.switchTab({ url: "/pages/my-observations/index" })
}
function goNotifications() {
  uni.navigateTo({ url: "/pages/notification/index" })
}
function goSuggestion() {
  uni.navigateTo({ url: "/pages/suggestion/index" })
}
function goProfile() {
  uni.switchTab({ url: "/pages/profile/index" })
}
</script>

<style scoped>
.notice {
  display: flex;
  align-items: center;
  gap: 18rpx;
  background: #fff8ea;
  border: 1rpx solid #f6e3bd;
  border-radius: 22rpx;
  padding: 22rpx 24rpx;
}
.notice__icon { font-size: 36rpx; }
.notice__title { display: block; font-size: 28rpx; color: #a86a15; font-weight: 600; }
.notice__hint { display: block; font-size: 22rpx; color: #bd9146; }
.cta { margin-top: 6rpx; }
</style>
