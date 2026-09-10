<template>
  <view class="region">
    <picker mode="multiSelector" :range="range" :value="indexes" @columnchange="onColumn" @change="onChange">
      <view class="region__field">{{ label || '请选择省 / 市 / 区县' }} ›</view>
    </picker>
    <input v-model="location" class="region__input" placeholder="详细地点（可选）" />
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue"
import { fetchChildren, fetchProvinces } from "@/api/plant"
import type { RegionItem } from "@/types/models"

const props = defineProps<{ locationText?: string }>()
const emit = defineEmits<{
  (event: "update:location", value: string): void
  (event: "region", value: { province?: string; city?: string; district?: string }): void
}>()

const provinces = ref<RegionItem[]>([])
const cities = ref<RegionItem[]>([])
const districts = ref<RegionItem[]>([])
const indexes = ref([0, 0, 0])
const location = ref(props.locationText || "")

const range = computed(() => [
  provinces.value.map((p) => p.regionName),
  cities.value.map((c) => c.regionName),
  districts.value.map((d) => d.regionName),
])

const label = computed(() => {
  if (!cities.value.length) return ""
  return (
    provinces.value[indexes.value[0]]?.regionName +
    " " + cities.value[indexes.value[1]]?.regionName +
    " " + districts.value[indexes.value[2]]?.regionName
  )
})

watch(location, (v) => emit("update:location", v))
watch(() => props.locationText, (v) => { if (v !== undefined) location.value = v })

async function onColumn(event: { detail: { column: number; value: number } }) {
  const { column, value } = event.detail
  indexes.value[column] = value
  if (column === 0) {
    cities.value = await loadCities(provinces.value[value])
    districts.value = []
    indexes.value[1] = 0
    indexes.value[2] = 0
  } else if (column === 1) {
    districts.value = await loadDistricts(cities.value[value])
    indexes.value[2] = 0
  }
  sync()
}

function onChange() {
  sync()
}

function sync() {
  const province = provinces.value[indexes.value[0]]
  const city = cities.value[indexes.value[1]]
  const district = districts.value[indexes.value[2]]
  emit("region", {
    province: province?.regionCode,
    city: city?.regionCode,
    district: district?.regionCode,
  })
}

async function loadCities(province?: RegionItem) {
  if (!province) return []
  return fetchChildren(province.regionCode).catch(() => [])
}
async function loadDistricts(city?: RegionItem) {
  if (!city) return []
  return fetchChildren(city.regionCode).catch(() => [])
}

onMounted(async () => {
  provinces.value = await fetchProvinces().catch(() => [])
  if (provinces.value.length) {
    cities.value = await loadCities(provinces.value[0])
    if (cities.value.length) {
      districts.value = await loadDistricts(cities.value[0])
    }
  }
})
</script>

<style scoped>
.region { display: flex; flex-direction: column; gap: 8rpx; }
.region__field { border: 1rpx solid #ddd; border-radius: 10rpx; padding: 16rpx; background: #fff; }
.region__input { border: 1rpx solid #ddd; border-radius: 10rpx; padding: 14rpx; background: #fff; }
</style>
