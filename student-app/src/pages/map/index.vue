<template>
  <view class="page">
    <view class="card card--ink summary">
      <text class="summary__title">全国植物观察地图</text>
      <text class="summary__note">按学生主动选择的省/市/区县聚合，不读取设备定位</text>
      <view class="summary__stats">
        <view class="summary__stat">
          <text class="summary__num">{{ rows.length }}</text><text class="summary__label">覆盖省份</text>
        </view>
        <view class="summary__stat">
          <text class="summary__num">{{ totalObservations }}</text><text class="summary__label">观察总数</text>
        </view>
        <view class="summary__stat">
          <text class="summary__num">{{ totalSpecies }}</text><text class="summary__label">记录物种</text>
        </view>
      </view>
    </view>

    <view class="map-card">
      <ChinaMap2D :stats="rows" :height="330" @select="onSelect" />
      <text class="map-hint">👆 点击省份查看该省的植物观察</text>
    </view>

    <view class="card">
      <template v-if="selectedCode">
        <view class="row">
          <text class="h2 grow">{{ selectedName }}</text>
          <text class="chip chip--brand" @tap="clearSelection">收起</text>
        </view>
        <view class="row row--tight">
          <text class="chip">{{ numberValue(selectedStat.observationCount) }} 条观察</text>
          <text v-if="selectedStat.speciesCount" class="chip">{{ numberValue(selectedStat.speciesCount) }} 种植物</text>
          <text v-if="selectedStat.studentCount" class="chip">{{ numberValue(selectedStat.studentCount) }} 名学生</text>
        </view>

        <view v-if="worksLoading" class="muted loading">作品加载中…</view>
        <view v-else-if="works.length" class="works">
          <view v-for="w in works" :key="String(w.observationId)" class="work" @tap="openWork(w)">
            <image :src="resolveMediaUrl(w.coverUrl)" mode="aspectFill" class="work__img" />
            <text class="work__name ellipsis">{{ w.commonName || w.reportedCommonName || '待鉴定植物' }}</text>
            <text class="work__meta ellipsis">{{ w.displayName || w.submitterName || '匿名' }}</text>
          </view>
        </view>
        <EmptyState v-else icon="🗺️" title="该省暂无公开观察" hint="换个省份看看，或先去采集一株" />

        <view v-if="worksTotal > works.length" class="more" @tap="loadMoreWorks">
          查看更多（{{ works.length }} / {{ worksTotal }}）
        </view>
      </template>

      <template v-else>
        <view class="sec"><text class="sec__title">观察最多的省份</text></view>
        <view class="rank">
          <view
            v-for="(p, i) in topProvinces"
            :key="p.provinceCode"
            class="rank__row"
            @tap="onSelect(p.provinceCode, String(p.provinceName || p.provinceCode))"
          >
            <text class="rank__no" :class="{ 'rank__no--top': i < 3 }">{{ i + 1 }}</text>
            <text class="rank__name grow ellipsis">{{ p.provinceName }}</text>
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
import EmptyState from "@/components/EmptyState.vue"
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

const totalObservations = computed(() => rows.value.reduce((sum, r) => sum + numberValue(r.observationCount), 0))
const totalSpecies = computed(() => rows.value.reduce((sum, r) => sum + numberValue(r.speciesCount), 0))
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
.summary { gap: 6rpx; }
.summary__title { font-size: 36rpx; font-weight: 700; }
.summary__note { font-size: 23rpx; color: rgba(255, 255, 255, 0.8); }
.summary__stats { display: flex; margin-top: 20rpx; }
.summary__stat { flex: 1; display: flex; flex-direction: column; align-items: center; }
.summary__num { font-size: 40rpx; font-weight: 700; }
.summary__label { font-size: 22rpx; color: rgba(255, 255, 255, 0.78); }
.map-card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 18rpx 12rpx 10rpx;
  box-shadow: 0 8rpx 24rpx rgba(31, 45, 36, 0.06);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}
.map-hint { font-size: 22rpx; color: #8a968c; }
.loading { padding: 20rpx 0; }
.works { display: flex; flex-wrap: wrap; gap: 14rpx; }
.work { width: calc((100% - 28rpx) / 3); display: flex; flex-direction: column; gap: 6rpx; }
.work__img { width: 100%; height: 168rpx; border-radius: 16rpx; background: #eef4ea; }
.work__name { font-size: 24rpx; font-weight: 600; }
.work__meta { color: #8a968c; font-size: 20rpx; }
.more { text-align: center; color: #3f9b3f; font-size: 26rpx; padding: 12rpx 0 4rpx; }
.rank { display: flex; flex-direction: column; }
.rank__row { display: flex; align-items: center; gap: 16rpx; padding: 18rpx 0; border-bottom: 1rpx solid #f1f5ef; }
.rank__row:last-child { border-bottom: none; }
.rank__no {
  width: 44rpx; height: 44rpx; border-radius: 50%; background: #f0f4ee; color: #8a968c;
  font-size: 24rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.rank__no--top { background: linear-gradient(135deg, #6cc46a, #2f7a34); color: #ffffff; font-weight: 700; }
.rank__name { font-size: 28rpx; }
</style>
