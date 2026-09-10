<template>
  <view class="page">
    <view class="note">地图按省/市/区县主动选择聚合展示，不读取设备定位；节点不代表精确位置。</view>
    <view v-for="p in rows" :key="p.provinceCode" class="prov">
      <view class="prov-head" @tap="toggle(p.provinceCode)">
        <text class="name">{{ p.provinceName }}</text>
        <text class="muted">{{ numberValue(p.observationCount) }} 条观察 · {{ numberValue(p.speciesCount) }} 种植物</text>
      </view>
      <view v-if="openCode === p.provinceCode" class="species">
        <view v-if="loadingSpecies">加载中…</view>
        <view v-for="s in provinceSpecies" :key="String(s.speciesId)" class="sp-item" @tap="openObservations(s)">
          <text>{{ s.commonName }}</text>
          <text class="muted">{{ numberValue(s.observationCount) }} 条</text>
        </view>
        <view v-if="!loadingSpecies && !provinceSpecies.length" class="muted">该省暂无公开植物</view>
      </view>
    </view>
    <view v-if="!rows.length" class="empty">加载中…</view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from "@dcloudio/uni-app"
import { ref } from "vue"
import { fetchMapChina, fetchProvinceSpecies } from "@/api/plant"
import { numberValue } from "@/utils/media"

const rows = ref<{ provinceCode: string; provinceName: string; speciesCount: number | string; observationCount: number | string }[]>([])
const openCode = ref<string | null>(null)
const provinceSpecies = ref<{ speciesId: string; commonName: string; observationCount: number | string }[]>([])
const loadingSpecies = ref(false)

onShow(async () => {
  rows.value = await fetchMapChina().catch(() => [])
})

async function toggle(code: string) {
  if (openCode.value === code) {
    openCode.value = null
    return
  }
  openCode.value = code
  loadingSpecies.value = true
  provinceSpecies.value = []
  try {
    provinceSpecies.value = await fetchProvinceSpecies(code).catch(() => [])
  } finally {
    loadingSpecies.value = false
  }
}

function openObservations(species: { speciesId: string; commonName: string }) {
  uni.showModal({
    title: species.commonName,
    content: "打开该物种的全国观察记录？",
    confirmText: "打开",
    success: (res) => {
      if (res.confirm) {
        uni.navigateTo({ url: "/pages/gallery/index?keyword=" + encodeURIComponent(species.commonName) })
      }
    },
  })
}
</script>

<style scoped>
.page { padding: 20rpx; display: flex; flex-direction: column; gap: 14rpx; }
.note { color: #999; font-size: 22rpx; }
.prov { background: #fff; border-radius: 14rpx; overflow: hidden; }
.prov-head { padding: 20rpx; display: flex; justify-content: space-between; align-items: center; }
.name { font-weight: 600; font-size: 30rpx; }
.muted { color: #999; font-size: 24rpx; }
.species { border-top: 1rpx solid #f0f0f0; padding: 0 20rpx 12rpx; }
.sp-item { display: flex; justify-content: space-between; padding: 14rpx 0; border-bottom: 1rpx solid #f5f5f5; }
.empty { color: #999; text-align: center; padding: 80rpx 0; }
</style>