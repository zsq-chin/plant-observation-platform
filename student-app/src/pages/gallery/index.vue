<template>
  <view class="page page--hero">
    <AppHero title="植物展廊" :subtitle="subtitle">
      <template #right>
        <view class="hero-tag">🌏 全国</view>
      </template>
    </AppHero>

    <view class="lift">
      <view class="search">
        <text class="search__icon">🔍</text>
        <input
          class="search__ipt"
          v-model="keyword"
          placeholder="搜索植物名 / 学名"
          placeholder-class="search__ph"
          confirm-type="search"
          @confirm="reload"
        />
        <text v-if="keyword" class="search__clear" @tap="clearKeyword">✕</text>
      </view>

      <view class="sec">
        <text class="sec__title">公开观察</text>
        <text class="sec__more">{{ totalText }}</text>
      </view>

      <view v-for="item in rows" :key="String(item.observationId)">
        <ObservationCard :item="item" @open="openDetail" />
      </view>

      <EmptyState
        v-if="!rows.length"
        :icon="loading ? '⏳' : '🖼️'"
        :title="loading ? '正在加载…' : '暂无公开观察'"
        :hint="loading ? '' : '换一个关键词，或先去采集一株植物'"
      />

      <button v-if="hasMore" class="btn-ghost btn-block" :loading="loadingMore" @tap="loadMore">加载更多</button>
      <view v-else-if="rows.length" class="list-end">— 已经到底了 —</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from "@dcloudio/uni-app"
import { computed, ref } from "vue"
import { fetchGallery } from "@/api/plant"
import AppHero from "@/components/AppHero.vue"
import EmptyState from "@/components/EmptyState.vue"
import ObservationCard from "@/components/ObservationCard.vue"
import type { GalleryItem } from "@/types/models"

const rows = ref<GalleryItem[]>([])
const keyword = ref("")
const page = ref(1)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)
const total = ref(0)

const subtitle = computed(() => (total.value ? "已收录 " + total.value + " 条学生观察" : "看看同学们都发现了什么"))
const totalText = computed(() => (loading.value ? "加载中" : "共 " + total.value + " 条"))

onShow(() => {
  page.value = 1
  hasMore.value = true
  rows.value = []
  reload()
})

async function reload() {
  loading.value = true
  try {
    const result = await fetchGallery(1, 12, keyword.value.trim() || undefined).catch(() => null)
    rows.value = result ? result.records : []
    total.value = result ? Number(result.total) : 0
    hasMore.value = result ? rows.value.length < total.value : false
    page.value = 1
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  loadingMore.value = true
  page.value += 1
  try {
    const result = await fetchGallery(page.value, 12, keyword.value.trim() || undefined).catch(() => null)
    if (result) {
      rows.value.push(...result.records)
      hasMore.value = rows.value.length < Number(result.total)
    }
  } finally {
    loadingMore.value = false
  }
}

function clearKeyword() {
  keyword.value = ""
  reload()
}

function openDetail(id: string) {
  uni.navigateTo({ url: "/pages/observation-detail/index?id=" + id })
}
</script>

<style scoped>
.hero-tag {
  font-size: 24rpx;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.2);
  color: #ffffff;
  flex-shrink: 0;
}
.search {
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: #ffffff;
  border-radius: 999rpx;
  padding: 20rpx 26rpx;
  box-shadow: 0 8rpx 24rpx rgba(31, 45, 36, 0.07);
}
.search__icon { font-size: 28rpx; }
.search__ipt { flex: 1; font-size: 28rpx; }
.search__clear { color: #b6c0b8; font-size: 26rpx; padding: 0 6rpx; }
.search__ph { color: #a8b3aa; }
.list-end { text-align: center; color: #a8b3aa; font-size: 23rpx; padding: 16rpx 0; }
</style>
