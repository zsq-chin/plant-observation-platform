<template>
  <view class="page">
    <input class="ipt" v-model="keyword" placeholder="搜索植物 / 学名" @confirm="reload" confirm-type="search" />
    <view v-for="item in rows" :key="String(item.observationId)">
      <ObservationCard :item="item" @open="openDetail" />
    </view>
    <view v-if="!rows.length" class="empty">{{ loading ? '加载中…' : '暂无公开观察' }}</view>
    <button size="mini" v-if="hasMore" @tap="loadMore">加载更多</button>
  </view>
</template>

<script setup lang="ts">
import { onShow } from "@dcloudio/uni-app"
import { ref } from "vue"
import { fetchGallery } from "@/api/plant"
import ObservationCard from "@/components/ObservationCard.vue"
import type { GalleryItem } from "@/types/models"

const rows = ref<GalleryItem[]>([])
const keyword = ref("")
const page = ref(1)
const hasMore = ref(true)
const loading = ref(false)

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
    hasMore.value = result ? rows.value.length < Number(result.total) : false
    page.value = 1
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  page.value += 1
  const result = await fetchGallery(page.value, 12, keyword.value.trim() || undefined).catch(() => null)
  if (result) {
    rows.value.push(...result.records)
    hasMore.value = rows.value.length < Number(result.total)
  }
}

function openDetail(id: string) {
  uni.navigateTo({ url: "/pages/observation-detail/index?id=" + id })
}
</script>

<style scoped>
.page { padding: 20rpx; }
.ipt { border: 1rpx solid #ddd; border-radius: 10rpx; padding: 14rpx 16rpx; margin-bottom: 16rpx; background: #fff; }
.empty { color: #999; text-align: center; padding: 60rpx 0; }
</style>
