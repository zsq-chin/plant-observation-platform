<template>
  <aside class="province-panel">
    <template v-if="visual">
      <header class="province-panel__head">
        <div>
          <h2>{{ visual.province.name }}</h2>
          <p class="province-panel__stats">
            {{ countOf(visual.province.speciesCount) }} 种植物 · {{ countOf(visual.province.observationCount) }} 条观察 · {{ countOf(visual.province.studentCount) }} 名学生
          </p>
        </div>
        <el-button circle size="small" @click="emit('close')"><el-icon><Close /></el-icon></el-button>
      </header>

      <div v-if="visual.regions.length" class="province-panel__cities">
        <button
          v-for="region in visual.regions"
          :key="region.regionCode"
          type="button"
          class="city-chip"
          :class="{ 'city-chip--active': activeCity === region.regionCode }"
          @click="toggleCity(region.regionCode)"
        >
          {{ region.regionName }}
          <b>{{ countOf(region.observationCount) }}</b>
        </button>
      </div>

      <p v-if="!listSpecies.length" class="province-panel__empty">该区域暂无审核通过的植物观察</p>
      <ul v-else class="province-panel__species">
        <li v-for="item in listSpecies" :key="String(item.speciesId)">
          <button type="button" class="species-row" @click="emit('openSpecies', item)">
            <img :src="resolveUrl(item.coverUrl)" :alt="(item.commonName || '植物') + '观察照片'" loading="lazy" @error="onError" />
            <span class="species-row__name">{{ item.commonName || "-" }}</span>
            <span class="species-row__count">{{ countOf(item.observationCount) }}</span>
          </button>
        </li>
      </ul>
    </template>
    <el-empty v-else description="省份数据加载中…" :image-size="60" />
  </aside>
</template>

<script setup lang="ts">
import { computed } from "vue"
import { Close } from "@element-plus/icons-vue"
import { PLANT_PLACEHOLDER, resolveMediaUrl } from "@/utils/media"
import { countOf } from "../api"
import type { ProvinceVisual, TopSpeciesItem } from "../types"

const props = defineProps<{ visual: ProvinceVisual | null; activeCity: string | null }>()
const emit = defineEmits<{
  (event: "close"): void
  (event: "openSpecies", item: TopSpeciesItem): void
  (event: "selectCity", code: string | null): void
}>()


const listSpecies = computed(() => {
  if (!props.visual) return []
  if (!props.activeCity) return props.visual.topSpecies
  const region = props.visual.regions.find((r) => r.regionCode === props.activeCity)
  return region?.topSpecies?.length ? region.topSpecies : props.visual.topSpecies
})

function toggleCity(code: string) {
  emit('selectCity', props.activeCity === code ? null : code)
}

function resolveUrl(value?: string | null) {
  return resolveMediaUrl(value)
}

function onError(event: Event) {
  const img = event.target as HTMLImageElement
  if (img && img.src !== PLANT_PLACEHOLDER) img.src = PLANT_PLACEHOLDER
}
</script>

<style scoped>
.province-panel { width: 380px; max-width: 92vw; display: flex; flex-direction: column; gap: 12px; }
.province-panel__head { display: flex; justify-content: space-between; align-items: flex-start; }
.province-panel__head h2 { margin: 0; }
.province-panel__stats { margin: 4px 0 0; color: var(--text-secondary); font-size: 12px; }
.province-panel__cities { display: flex; flex-wrap: wrap; gap: 6px; }
.city-chip { border: 1px solid var(--border-color); background: var(--surface-elevated); border-radius: 999px; padding: 3px 10px; cursor: pointer; font-size: 12px; }
.city-chip b { color: var(--el-color-primary); margin-left: 4px; }
.city-chip--active { border-color: var(--el-color-primary); color: var(--el-color-primary); }
.province-panel__species { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; max-height: 46vh; overflow: auto; }
.species-row { display: flex; gap: 10px; align-items: center; width: 100%; padding: 6px 8px; border: 1px solid var(--border-subtle); border-radius: 12px; background: var(--surface-elevated); cursor: pointer; }
.species-row img { width: 42px; height: 42px; border-radius: 8px; object-fit: cover; }
.species-row__name { flex: 1; text-align: left; font-size: 14px; }
.species-row__count { color: var(--text-muted); font-size: 12px; }
.province-panel__empty { color: var(--text-muted); font-size: 13px; padding: 10px 0; }
</style>