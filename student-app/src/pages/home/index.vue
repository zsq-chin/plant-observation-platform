<template>
  <view class="page page--hero">
    <AppHero title="你好，{{ auth.displayName }}" :subtitle="auth.className || '记录身边的植物，让观察被看见'">
      <template #right>
        <view class="avatar" @tap="goProfile">{{ avatarText }}</view>
      </template>
    </AppHero>

    <view class="lift">
      <!-- 快捷入口放在最上面，一进首页就能看到 -->
      <view class="card quick">
        <view class="quick__grid">
          <view v-for="t in tiles" :key="t.key" class="quick__item" hover-class="quick__item--press" @tap="t.action()">
            <view class="quick__badge">
              <image class="quick__icon" :src="'/static/icons/' + t.icon + '.svg'" mode="aspectFit" />
            </view>
            <text class="quick__label">{{ t.label }}</text>
          </view>
        </view>
      </view>

      <view v-if="pendingLocal > 0" class="notice" @tap="goLocalDrafts">
        <image class="notice__icon" src="/static/icons/cloud.svg" mode="aspectFit" />
        <view class="grow">
          <text class="notice__title">有 {{ pendingLocal }} 条本地草稿待同步</text>
          <text class="notice__hint">点击联网同步，避免照片丢失</text>
        </view>
        <text class="item__arrow">›</text>
      </view>

      <view class="stats" v-if="dash">
        <view class="stat stat--draft" hover-class="stat--press" @tap="goTab('DRAFT')">
          <text class="stat__num">{{ dash.draftCount }}</text><text class="stat__label">待完善</text>
        </view>
        <view class="stat stat--submitted" hover-class="stat--press" @tap="goTab('SUBMITTED')">
          <text class="stat__num">{{ dash.submittedCount }}</text><text class="stat__label">待审核</text>
        </view>
        <view class="stat stat--approved" hover-class="stat--press" @tap="goTab('APPROVED')">
          <text class="stat__num">{{ dash.approvedCount }}</text><text class="stat__label">已通过</text>
        </view>
        <view class="stat stat--rejected" hover-class="stat--press" @tap="goTab('REJECTED')">
          <text class="stat__num">{{ dash.rejectedCount }}</text><text class="stat__label">被驳回</text>
        </view>
      </view>

      <view class="sec">
        <text class="sec__title">最新优秀观察</text>
        <text class="sec__more" @tap="goGallery">全部 ›</text>
      </view>
      <view v-for="item in featured" :key="String(item.observationId)">
        <ObservationCard :item="item" @open="openDetail" />
      </view>
      <EmptyState v-if="!featured.length" icon="plant" title="还没有优秀观察" hint="先去采集，通过审核后就有机会登上首页" />
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
function goProfile() {
  uni.switchTab({ url: "/pages/profile/index" })
}

const tiles = [
  { key: "capture", icon: "camera", label: "采集植物", action: capture },
  { key: "map", icon: "map", label: "全国地图", action: goMap },
  { key: "gallery", icon: "gallery", label: "植物展廊", action: goGallery },
  { key: "mine", icon: "plant", label: "我的植物", action: goMine },
  { key: "notify", icon: "bell", label: "消息通知", action: goNotifications },
  { key: "profile", icon: "user", label: "我的", action: goProfile },
]
</script>

<style scoped>
.quick { padding: 18rpx 12rpx 8rpx; }
.quick__grid { display: flex; flex-wrap: wrap; }
.quick__item {
  width: 33.33%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  padding: 14rpx 0 18rpx;
  border-radius: 18rpx;
}
.quick__item--press { background: #f4f9f1; }
.quick__badge {
  width: 78rpx;
  height: 78rpx;
  border-radius: 24rpx;
  background: linear-gradient(180deg, #f1f7ec, #e5f0de);
  display: flex;
  align-items: center;
  justify-content: center;
}
.quick__icon { width: 42rpx; height: 42rpx; }
.quick__label { font-size: 24rpx; color: #55645a; }
.notice {
  display: flex;
  align-items: center;
  gap: 18rpx;
  background: #fff8ea;
  border: 1rpx solid #f6e3bd;
  border-radius: 22rpx;
  padding: 22rpx 24rpx;
}
.notice__icon { width: 40rpx; height: 40rpx; }
.notice__title { display: block; font-size: 28rpx; color: #a86a15; font-weight: 600; }
.notice__hint { display: block; font-size: 22rpx; color: #bd9146; }
.stat--press { background: #fbfdfa; }
</style>
