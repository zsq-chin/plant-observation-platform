<template>
  <router-link class="plant-work-card" :class="{ 'plant-work-card--featured': work.featured, 'plant-work-card--compact': compact }" :to="detailPath">
    <div class="plant-work-card__cover">
      <img v-if="work.coverUrl" :src="resolveMediaUrl(work.coverUrl)" :alt="coverAlt" loading="lazy" @error="onMediaError" />
      <span v-else class="plant-work-card__placeholder" aria-hidden="true">🌿</span>
      <span v-if="work.featured" class="plant-work-card__badge">★ 精选</span>
    </div>
    <div class="plant-work-card__body">
      <b class="plant-work-card__name">{{ plantName }}</b>
      <span class="plant-work-card__meta">{{ metaLine }}</span>
      <p v-if="work.description" class="plant-work-card__desc">{{ work.description }}</p>
    </div>
  </router-link>
</template>

<script setup lang="ts">
import { computed } from "vue"
import type { MapFeaturedWork, ProvinceWorkRow } from "@/modules/plant-map/types"
import { onMediaError, resolveMediaUrl } from "@/utils/media"

const props = defineProps<{
  work: MapFeaturedWork | ProvinceWorkRow
  compact?: boolean
}>()

const plantName = computed(() => props.work.commonName || props.work.reportedCommonName || "未知植物")
const detailPath = computed(() => "/plant/observations/" + String(props.work.observationId))
const coverAlt = computed(() => {
  const place = props.work.provinceName || props.work.cityName || ""
  return place ? plantName.value + "在" + place + "的观察照片" : plantName.value + "观察照片"
})
const metaLine = computed(() => {
  const who = props.work.displayName || props.work.submitterName || ""
  const where = [props.work.provinceName, props.work.cityName].filter(Boolean).join(" · ")
  return [who, where].filter(Boolean).join(" · ") || "匿名观察"
})
</script>

<style scoped>
.plant-work-card { display: flex; flex-direction: column; overflow: hidden; border: 1px solid var(--border-subtle); border-radius: 14px; background: var(--surface-elevated); color: inherit; text-decoration: none; transition: transform 0.18s ease, box-shadow 0.18s ease; }
.plant-work-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); }
.plant-work-card--featured { border-color: #e6a23c; }
.plant-work-card__cover { position: relative; aspect-ratio: 4 / 3; background: linear-gradient(135deg, var(--brand-soft), #dfe8c8); display: flex; align-items: center; justify-content: center; }
.plant-work-card__cover img { width: 100%; height: 100%; object-fit: cover; }
.plant-work-card__placeholder { font-size: 42px; opacity: 0.5; }
.plant-work-card__badge { position: absolute; top: 6px; left: 6px; padding: 1px 8px; border-radius: 999px; background: #e6a23c; color: #fff; font-size: 11px; }
.plant-work-card__body { display: flex; flex-direction: column; gap: 2px; padding: 8px 10px 10px; }
.plant-work-card__name { font-size: 14px; }
.plant-work-card__meta { color: var(--text-muted); font-size: 12px; }
.plant-work-card__desc { margin: 4px 0 0; color: var(--text-secondary); font-size: 12px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.plant-work-card--compact .plant-work-card__desc { display: none; }
</style>
