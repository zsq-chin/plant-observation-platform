<template>
  <view class="page">
    <view class="head">
      <text class="title">全国植物观察地图</text>
      <text class="note">按学生主动选择的省/市/区县聚合展示，不读取设备定位。</text>
    </view>

    <ChinaMap2D :stats="rows" :height="330" @select="onSelect" />

    <view class="panel">
      <template v-if="selectedCode">
        <view class="panel__head">
          <text class="name">{{ selectedName }}</text>
          <text class="close" @tap="clearSelection">收起</text>
        </view>
        <text class="muted">
          {{ numberValue(selectedStat.observationCount) }} 条观察
          <text v-if="selectedStat.speciesCount"> · {{ numberValue(selectedStat.speciesCount) }} 种植物</text>
          <text v-if="selectedStat.studentCount"> · {{ numberValue(selectedStat.studentCount) }} 名学生</text>
        </text>

        <view v-if="worksLoading" class="muted loading">作品加载中…</view>
        <view v-else-if="works.length" class="works">
          <view v-for="w in works" :key="String(w.observationId)" class="work" @tap="openWork(w)">
            <image :src="resolveMediaUrl(w.coverUrl)" mode="aspectFill" class="work__img" />
            <text class="work__name">{{ w.commonName || w.reportedCommonName || '待鉴定植物' }}</text>
            <text class="work__meta">{{ w.displayName || w.submitterName || '匿名' }}<text v-if="w.cityName"> · {{ w.cityName }}</text></text>
          </view>
        </view>
        <view v-else class="muted loading">该省暂无公开观察</view>

        <view v-if="worksTotal > works.length" class="more" @tap="loadMoreWorks">
          查看更多（已显示 {{ works.length }} / {{ worksTotal }}）
        </view>
      </template>

      <template v-else>
        <text class="muted">点击地图上的省份，查看该省学生上传的植物观察</text>
        <view class="rank">
          <view v-for="p in topProvinces" :key="p.provinceCode" class="rank__row" @tap="onSelect(p.provinceCode, String(p.provinceName || p.provinceCode))">
            <text class="rank__name">{{ p.provinceName }}</text>
            <text class="muted">{{ numberValue(p.observationCount) }} 条 · {{ numberValue(p.speciesCount) }} 种</text>
          </view>
          <view v-if="!rows.length" class="muted loading">加载中…</view>
        </view>
      </template>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from "@dcloudio/uni-app"
import { computed, ref } from "vue"
import ChinaMap2D from "@/components/ChinaMap2D.vue"
import { fetchMapChina, fetchProvinceWorks, type ProvinceWorkItem } from "@/api/plant"
import { numberValue, resolveMediaUrl } from "@/utils/media"

interface StatRow {
  provinceCode: string
  provinceName?: string | null
  speciesCount: number | string
  observationCount: number | string
  studentCount?: number | string
}

const rows = ref<StatRow[]>([])
const selectedCode = ref("")
const selectedName = ref("")
const works = ref<ProvinceWorkItem[]>([])
const worksTotal = ref(0)
const worksLoading = ref(false)
const page = ref(1)

const topProvinces = computed(() =>
  [...rows.value].sort((a, b) => numberValue(b.observationCount) - numberValue(a.observationCount)).slice(0, 8),
)
const selectedStat = computed(
  () => rows.value.find((r) => r.provinceCode === selectedCode.value) || { observationCount: 0, speciesCount: 0, studentCount: 0 },
)

async function loadStats() {
  try {
    rows.value = await fetchMapChina()
  } catch (error) {
    console.error("加载全国统计失败", error)
    uni.showToast({ title: "地图数据加载失败，请下拉重试", icon: "none" })
  }
}

async function onSelect(code: string, name: string) {
  selectedCode.value = code
  selectedName.value = name || code
  page.value = 1
  works.value = []
  await loadWorks(true)
}

async function loadWorks(reset = false) {
  if (!selectedCode.value) return
  worksLoading.value = true
  try {
    const result = await fetchProvinceWorks(selectedCode.value, page.value, 12)
    works.value = reset ? result.records : works.value.concat(result.records)
    worksTotal.value = Number(result.total || 0)
  } catch (error) {
    console.error("加载省份作品失败", error)
    uni.showToast({ title: "该省作品加载失败，请重试", icon: "none" })
  } finally {
    worksLoading.value = false
  }
}

function loadMoreWorks() {
  page.value += 1
  loadWorks(false)
}

function clearSelection() {
  selectedCode.value = ""
  selectedName.value = ""
  works.value = []
  worksTotal.value = 0
}

function openWork(work: ProvinceWorkItem) {
  uni.navigateTo({ url: "/pages/observation-detail/index?public=1&id=" + String(work.observationId) })
}

onShow(() => {
  loadStats()
})
</script>

<style scoped>
.page { padding: 20rpx; display: flex; flex-direction: column; gap: 14rpx; }
.head { display: flex; flex-direction: column; gap: 4rpx; }
.title { font-size: 34rpx; font-weight: 700; }
.note { color: #999; font-size: 22rpx; }
.panel { background: #fff; border-radius: 16rpx; padding: 18rpx; display: flex; flex-direction: column; gap: 10rpx; }
.panel__head { display: flex; justify-content: space-between; align-items: center; }
.name { font-size: 30rpx; font-weight: 700; }
.close { color: #3f9b3f; font-size: 26rpx; }
.muted { color: #999; font-size: 24rpx; }
.loading { padding: 20rpx 0; }
.works { display: flex; flex-wrap: wrap; gap: 12rpx; }
.work { width: 30%; display: flex; flex-direction: column; gap: 4rpx; }
.work__img { width: 100%; height: 150rpx; border-radius: 12rpx; background: #eef4e8; }
.work__name { font-size: 24rpx; font-weight: 600; }
.work__meta { color: #999; font-size: 20rpx; }
.more { text-align: center; color: #3f9b3f; font-size: 26rpx; padding: 10rpx 0 4rpx; }
.rank { display: flex; flex-direction: column; margin-top: 8rpx; }
.rank__row { display: flex; justify-content: space-between; padding: 14rpx 0; border-bottom: 1rpx solid #f5f5f5; }
.rank__name { font-size: 28rpx; }
</style>
