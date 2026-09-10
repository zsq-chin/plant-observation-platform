<template>
  <div class="plant-map-page">
    <header class="plant-map-page__header">
      <div>
        <p class="page-kicker">China Plant Map · 3D</p>
        <h1>中国植物地图</h1>
      </div>
      <router-link class="el-button" to="/plant">← 返回首页</router-link>
    </header>

    <MapFilterBar ref="filterBarRef" :model-value="filter" :categories="categories" :classes="classes" @apply="onApplyFilter" />

    <nav class="map-breadcrumb">
      <button type="button" class="crumb-link" :disabled="stage === 'CHINA_OVERVIEW'" @click="backToChina">全国</button>
      <template v-if="selectedProvince">
        <span class="crumb-sep">›</span>
        <button v-if="selectedSpecies" type="button" class="crumb-link" @click="backToProvince">{{ selectedProvince.name }}</button>
        <span v-else class="crumb-current">{{ selectedProvince.name }}</span>
      </template>
      <template v-if="selectedSpecies">
        <span class="crumb-sep">›</span>
        <span class="crumb-current">{{ selectedSpecies.name }}</span>
      </template>
    </nav>

    <div v-if="nationSpecies" class="nation-species">
      全国物种筛选：<b>{{ nationSpeciesName || "物种 #" + nationSpecies }}</b>
      <el-button size="small" link type="primary" @click="clearNationSpecies">清除</el-button>
    </div>

    <div class="map-stage">
      <template v-if="mapSupported">
        <div v-show="!mapError" ref="mapContainerRef" class="map-canvas"></div>
        <div v-if="loadingStats" class="map-overlay map-overlay--center">地图数据加载中…</div>
        <div v-if="mapError" class="map-overlay map-overlay--center map-overlay--error">
          <p>{{ mapError }}</p>
          <el-button type="primary" @click="retryMount">重新加载</el-button>
        </div>
        <div v-if="hovered && !selectedProvince" class="map-hover-tip">
          <b>{{ hovered.name }}</b>
          <template v-if="statsByCode[hovered.code]">
            <p>{{ countOf(statsByCode[hovered.code].observationCount) }} 条观察 · {{ countOf(statsByCode[hovered.code].speciesCount) }} 种植物</p>
          </template>
        </div>
      </template>
      <div v-else class="map-fallback">
        <p class="map-fallback__note">当前浏览器不支持 WebGL，已降级为二维省份列表（点击进入该省展廊）。</p>
        <div class="map-fallback__grid">
          <router-link v-for="p in provinces" :key="p.regionCode" class="province-mini"
            :to="'/plant/gallery?provinceCode=' + p.regionCode">
            <b>{{ p.regionName }}</b>
            <span v-if="statsByCode[p.regionCode]">{{ countOf(statsByCode[p.regionCode].observationCount) }} 条</span>
            <span v-else>暂无数据</span>
          </router-link>
        </div>
      </div>

      <Transition name="panel">
        <div v-if="stage !== 'CHINA_OVERVIEW' && (visual || visualLoading || visualError)" class="map-side">
          <el-alert v-if="visualError" type="error" :closable="false" class="map-side__alert">
            <template #title>{{ visualError }} <el-button size="small" link type="primary" @click="loadVisual()">重试</el-button></template>
          </el-alert>
          <ProvincePlantPanel v-if="visual" :visual="visual" :active-city="activeCity"
            @close="backToChina" @open-species="openSpecies" @select-city="onSelectCity" />
        </div>
      </Transition>

      <Transition name="panel">
        <div v-if="selectedSpecies" class="map-drawer">
          <SpeciesObservationDrawer :species-name="selectedSpecies.name" :province-name="selectedProvinceName"
            :rows="speciesRows" :loading="speciesLoading" @back="backToProvince" @close="backToProvince" />
        </div>
      </Transition>
    </div>
    <p class="map-compliance">地图按学生主动选择的省、市、区县聚合展示，不读取学生设备定位；节点不代表精确采集位置；省份高度表示公开观察数量，不代表地形高度。</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue"
import { useRoute, useRouter } from "vue-router"
import "maplibre-gl/dist/maplibre-gl.css"
import { fetchCategories, fetchProvinces, fetchPublicClasses, type CategoryItem, type ClassItem, type RegionItem } from "@/api/plant"
import MapFilterBar from "@/modules/plant-map/components/MapFilterBar.vue"
import ProvincePlantPanel from "@/modules/plant-map/components/ProvincePlantPanel.vue"
import SpeciesObservationDrawer from "@/modules/plant-map/components/SpeciesObservationDrawer.vue"
import { countOf, fetchChinaStats, fetchProvinceVisual, fetchSpeciesObservations } from "@/modules/plant-map/api"
import { isWebGlSupported, prefersReducedMotion, usePlantMap, type PlantMapCallbacks } from "@/modules/plant-map/composables/usePlantMap"
import { parseMapQuery, type MapStage } from "@/modules/plant-map/utils/mapState"
import { resolveMediaUrl } from "@/utils/media"
import type { ChinaStatRow, MapFilter, ProvinceVisual, RegionNode, SpeciesObsRow, TopSpeciesItem } from "@/modules/plant-map/types"

const route = useRoute()
const router = useRouter()

const mapContainerRef = ref<HTMLElement | null>(null)
const filterBarRef = ref<InstanceType<typeof MapFilterBar> | null>(null)

const mapSupported = isWebGlSupported()
const plantMap = usePlantMap()
const categories = ref<CategoryItem[]>([])
const classes = ref<ClassItem[]>([])
const provinces = ref<RegionItem[]>([])
const statsRows = ref<ChinaStatRow[]>([])
const loadingStats = ref(false)
const mapError = ref("")
const hovered = ref<{ code: string; name: string } | null>(null)
const selectedProvince = ref<{ code: string; name: string } | null>(null)
const selectedSpecies = ref<{ id: number | string; name: string } | null>(null)
const visual = ref<ProvinceVisual | null>(null)
const visualLoading = ref(false)
const visualError = ref("")
const speciesRows = ref<SpeciesObsRow[]>([])
const speciesLoading = ref(false)
const activeCity = ref<string | null>(null)
const urlState = parseMapQuery(route.query)
const stage = ref<MapStage>("CHINA_OVERVIEW")
const nationSpecies = ref<string | null>(urlState.speciesFilter || null)
const nationSpeciesName = ref<string | null>(null)

const filter = reactive<MapFilter>({})
let requestSeq = 0
let visualController: AbortController | null = null
let mounted = false

const selectedProvinceName = computed(() => selectedProvince.value?.name || "")

const statsByCode = computed(() => {
  const map: Record<string, ChinaStatRow> = {}
  for (const row of statsRows.value) map[row.provinceCode] = row
  return map
})

const reducedMotion = prefersReducedMotion()
const cameraDuration = () => (reducedMotion ? 200 : 1100)

async function loadOptions() {
  try {
    const [p, c, cl] = await Promise.all([fetchProvinces(), fetchCategories(), fetchPublicClasses()])
    provinces.value = p
    categories.value = c
    classes.value = cl
  } catch {
    /* options 失败不阻塞 */
  }
}

async function loadStats() {
  loadingStats.value = true
  try {
    const params: MapFilter = { ...filter }
if (nationSpecies.value) params.speciesId = nationSpecies.value
statsRows.value = await fetchChinaStats(params)
    if (mounted) plantMap.setStats(statsRows.value)
  } catch {
    statsRows.value = []
  } finally {
    loadingStats.value = false
  }
}

async function loadVisual() {
  if (!selectedProvince.value) return
  visualController?.abort()
  visualController = new AbortController()
  const seq = ++requestSeq
  visualLoading.value = true
  visualError.value = ""
  visual.value = null
  try {
    const data = await fetchProvinceVisual(selectedProvince.value.code, { ...filter })
    if (seq !== requestSeq) return
    visual.value = data
    renderNodes(data.regions)
  } catch {
    if (seq === requestSeq) visualError.value = selectedProvince.value.name + " 数据加载失败"
  } finally {
    if (seq === requestSeq) visualLoading.value = false
  }
}

function renderNodes(regions: RegionNode[]) {
  const visible = regions.filter((region) => countOf(region.observationCount) > 0)
  plantMap.renderRegionMarkers(visible, resolveMediaUrl)
  plantMap.setMarkerClick(({ region }) => {
    activeCity.value = region.regionCode
  })
}

function onSelectCity(code: string | null) {
  activeCity.value = code
}

async function clearNationSpecies() {
  nationSpecies.value = null
  nationSpeciesName.value = null
  syncUrl()
  loadStats()
}

async function handleProvinceClick(code: string, name: string) {
  if (stage.value === "PROVINCE_FLYING" || stage.value === "RETURNING") return
  if (selectedProvince.value?.code === code && stage.value === "PROVINCE_OVERVIEW") return
  requestSeq++
  clearNationSpecies()
  selectedProvince.value = { code, name }
  selectedSpecies.value = null
  speciesRows.value = []
  visual.value = null
  visualError.value = ""
  activeCity.value = null
  plantMap.setSelected(code)
  stage.value = "PROVINCE_FLYING"
  syncUrl()
  const bounds = plantMap.boundsOf(code)
  if (bounds) {
    await plantMap.flyToBounds(bounds, cameraDuration())
  } else {
    await plantMap.flyChina(cameraDuration())
  }
  if (selectedProvince.value?.code === code) {
    stage.value = "PROVINCE_OVERVIEW"
    loadVisual()
  }
}

async function openSpecies(item: TopSpeciesItem) {
  if (!selectedProvince.value) return
  const seq = ++requestSeq
  selectedSpecies.value = { id: item.speciesId, name: item.commonName || "物种" }
  stage.value = "SPECIES_FOCUS"
  speciesRows.value = []
  syncUrl()
  speciesLoading.value = true
  try {
    const rows = await fetchSpeciesObservations(item.speciesId, selectedProvince.value.code)
    if (seq === requestSeq) speciesRows.value = rows
  } finally {
    if (seq === requestSeq) speciesLoading.value = false
  }
}

function backToProvince() {
  requestSeq++
  selectedSpecies.value = null
  speciesRows.value = []
  stage.value = "PROVINCE_OVERVIEW"
  syncUrl()
}

async function backToChina() {
  if (stage.value === "RETURNING" || stage.value === "PROVINCE_FLYING") return
  requestSeq++
  visualController?.abort()
  selectedProvince.value = null
  selectedSpecies.value = null
  speciesRows.value = []
  visual.value = null
  visualError.value = ""
  activeCity.value = null
  plantMap.setSelected(null)
  plantMap.clearRegionMarkers()
  stage.value = "RETURNING"
  syncUrl()
  await plantMap.flyChina(cameraDuration())
  if (!selectedProvince.value) stage.value = "CHINA_OVERVIEW"
}

function syncUrl() {
  const query: Record<string, string | null> = {}
  if (filter.categoryId) query.categoryId = filter.categoryId
  if (filter.classId) query.classId = filter.classId
  if (filter.year) query.year = String(filter.year)
  if (filter.keyword) query.keyword = filter.keyword
  if (nationSpecies.value) query.sp = nationSpecies.value
  if (selectedProvince.value) query.province = selectedProvince.value.code
  if (selectedSpecies.value) query.species = String(selectedSpecies.value.id)
  router.replace({ query })
}

function onApplyFilter() {
  const local = filterBarRef.value?.getLocal() ?? {}
  Object.assign(filter, local)
  syncUrl()
  loadStats()
  if (selectedProvince.value) {
    loadVisual()
  }
}

async function mountMap() {
  if (!mapSupported || !mapContainerRef.value) return
  mapError.value = ""
  const callbacks: PlantMapCallbacks = {
    onProvinceClick: (code, name) => handleProvinceClick(code, name),
    onHover: (code, name) => {
      hovered.value = code ? { code, name: name || code } : null
    },
  }
  try {
    await plantMap.mount(mapContainerRef.value, callbacks)
    mounted = true
    plantMap.setStats(statsRows.value)
    if (urlState.province) {
      const name = provinces.value.find((p) => p.regionCode === urlState.province)?.regionName || urlState.province
      handleProvinceClick(urlState.province, name)
    }
  } catch (error) {
    mapError.value = error instanceof Error ? error.message : "地图初始化失败"
  }
}

function retryMount() {
  mountMap()
}

onMounted(async () => {
  await loadOptions()
  await loadStats()
  await mountMap()
})

onBeforeUnmount(() => {
  visualController?.abort()
  plantMap.destroy()
})
</script>

<style scoped>
.plant-map-page { display: flex; flex-direction: column; gap: 12px; max-width: 1280px; margin: 0 auto; padding: 18px 20px 36px; }
.plant-map-page__header { display: flex; justify-content: space-between; align-items: center; }
.plant-map-page__header h1 { margin: 0; }
.map-stage { position: relative; height: min(72vh, 760px); min-height: 480px; border-radius: 20px; overflow: hidden; border: 1px solid var(--border-subtle); }
.map-canvas { position: absolute; inset: 0; }
.map-overlay { position: absolute; z-index: 5; padding: 14px 18px; border-radius: 12px; background: color-mix(in srgb, var(--card-bg) 92%, transparent); box-shadow: var(--shadow-md); }
.map-overlay--center { top: 50%; left: 50%; transform: translate(-50%, -50%); }
.map-overlay--error { display: flex; flex-direction: column; gap: 10px; align-items: center; }
.map-hover-tip { position: absolute; z-index: 5; top: 12px; left: 12px; padding: 8px 12px; border-radius: 10px; background: rgba(255, 255, 255, 0.92); color: #333; font-size: 13px; pointer-events: none; }
.map-hover-tip p { margin: 2px 0 0; color: #666; }
.map-side { position: absolute; top: 12px; right: 12px; bottom: 12px; z-index: 6; padding: 16px; border-radius: 16px; background: color-mix(in srgb, var(--card-bg) 94%, transparent); backdrop-filter: blur(8px); overflow: auto; border: 1px solid var(--border-subtle); }
.map-drawer { position: absolute; top: 12px; right: 12px; bottom: 12px; z-index: 7; padding: 16px; border-radius: 16px; background: color-mix(in srgb, var(--card-bg) 96%, transparent); backdrop-filter: blur(8px); overflow: auto; border: 1px solid var(--border-subtle); }
.map-side__alert { margin-bottom: 10px; }
.map-fallback { padding: 20px; overflow: auto; height: 100%; }
.map-fallback__note { color: var(--text-muted); }
.map-fallback__grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 10px; }
.province-mini { display: flex; flex-direction: column; gap: 4px; padding: 12px 14px; border: 1px solid var(--border-subtle); border-radius: 12px; text-decoration: none; color: inherit; background: var(--surface-elevated); }
.province-mini span { color: var(--text-muted); font-size: 12px; }
.map-breadcrumb { display: flex; align-items: center; gap: 6px; font-size: 14px; }
.crumb-link { border: none; background: none; color: var(--el-color-primary); cursor: pointer; padding: 0; }
.crumb-sep { color: var(--text-muted); }
.crumb-current { color: var(--text-secondary); }
.nation-species { display: flex; align-items: center; gap: 8px; padding: 6px 12px; border-radius: 999px; background: color-mix(in srgb, var(--brand-soft) 60%, transparent); width: fit-content; font-size: 13px; }
.map-compliance { color: var(--text-muted); font-size: 12px; text-align: center; }
.panel-enter-active, .panel-leave-active { transition: opacity 0.3s ease, transform 0.3s ease; }
.panel-enter-from, .panel-leave-to { opacity: 0; transform: translateX(40px); }
@media (max-width: 720px) {
  .map-stage { height: 70vh; }
  .map-side, .map-drawer { top: auto; bottom: 0; left: 0; right: 0; max-height: 46vh; border-radius: 18px 18px 0 0; }
}
</style>

<style>
/* 地图 Marker（动态 DOM，需全局样式） */
.plant-region-node {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  border: none; background: transparent; cursor: pointer; padding: 0;
  width: 74px;
  animation: plantNodeIn 0.35s ease both;
}
@keyframes plantNodeIn { from { opacity: 0; transform: scale(0.6); } to { opacity: 1; transform: scale(var(--node-scale, 1)); } }
.plant-region-node__img { width: 48px; height: 48px; border-radius: 50%; overflow: hidden; border: 2px solid #ffffff; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25); background: #eef6e4; display: flex; align-items: center; justify-content: center; }
.plant-region-node__img img { width: 100%; height: 100%; object-fit: cover; }
.plant-region-node__name { font-size: 12px; font-weight: 700; background: rgba(255, 255, 255, 0.9); border-radius: 999px; padding: 1px 8px; color: #333; white-space: nowrap; }
.plant-region-node__count { font-size: 11px; color: #3d6b2e; background: rgba(255, 255, 255, 0.85); border-radius: 999px; padding: 0 6px; }
.plant-region-node:hover .plant-region-node__img { transform: scale(1.08); }
</style>