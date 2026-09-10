<template>
  <aside class="species-drawer">
    <header class="species-drawer__head">
      <div>
        <p class="species-drawer__crumb">{{ provinceName }} · {{ speciesName }}</p>
        <h3>公开观察记录（{{ rows.length }}）</h3>
      </div>
      <div class="species-drawer__actions">
        <el-button size="small" @click="emit('back')">返回省份</el-button>
        <el-button circle size="small" @click="emit('close')"><el-icon><Close /></el-icon></el-button>
      </div>
    </header>
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-empty v-else-if="!rows.length" description="该物种暂无公开观察记录" :image-size="60" />
    <ul v-else class="species-drawer__list">
      <li v-for="row in rows" :key="String(row.observationId)">
        <button type="button" class="obs-mini" @click="openDetail(row)">
          <img :src="resolveUrl(row.coverUrl)" :alt="(row.commonName || '植物') + '观察照片'" loading="lazy" @error="onError" />
          <span class="obs-mini__body">
            <b>{{ row.submitterName || "匿名" }}<i v-if="row.className"> · {{ row.className }}</i></b>
            <span class="obs-mini__meta">{{ row.provinceName || "" }} {{ row.cityName || "" }} · {{ fmtDate(row.observedAt) }}</span>
            <span class="obs-mini__meta">⭐ {{ row.averageRating ?? "-" }} 💬 {{ countOf(row.commentCount) }}</span>
          </span>
          <span class="obs-mini__go">查看 →</span>
        </button>
      </li>
    </ul>
    <p class="species-drawer__note">点击记录进入完整详情（多图/点评/评论/评分）。地图节点不代表精确采集位置。</p>
  </aside>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router"
import { Close } from "@element-plus/icons-vue"
import { fmtDate } from "@/api/plant"
import { PLANT_PLACEHOLDER, resolveMediaUrl } from "@/utils/media"
import { countOf } from "../api"
import type { SpeciesObsRow } from "../types"

defineProps<{
  speciesName?: string | null
  provinceName?: string | null
  rows: SpeciesObsRow[]
  loading: boolean
}>()

const emit = defineEmits<{ (event: "back"): void; (event: "close"): void }>()
const router = useRouter()

function resolveUrl(value?: string | null) {
  return resolveMediaUrl(value)
}

function onError(event: Event) {
  const img = event.target as HTMLImageElement
  if (img && img.src !== PLANT_PLACEHOLDER) img.src = PLANT_PLACEHOLDER
}

function openDetail(row: SpeciesObsRow) {
  router.push("/plant/observations/" + String(row.observationId))
}
</script>

<style scoped>
.species-drawer { width: 400px; max-width: 94vw; display: flex; flex-direction: column; gap: 10px; }
.species-drawer__head { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
.species-drawer__crumb { margin: 0 0 2px; color: var(--text-muted); font-size: 12px; }
.species-drawer__head h3 { margin: 0; }
.species-drawer__actions { display: flex; gap: 6px; align-items: center; }
.species-drawer__list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; max-height: 52vh; overflow: auto; }
.obs-mini { display: flex; gap: 10px; align-items: center; width: 100%; padding: 8px; border: 1px solid var(--border-subtle); border-radius: 12px; background: var(--surface-elevated); cursor: pointer; text-align: left; }
.obs-mini img { width: 58px; height: 58px; border-radius: 10px; object-fit: cover; flex-shrink: 0; }
.obs-mini__body { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.obs-mini__body i { color: var(--text-muted); font-style: normal; font-size: 12px; }
.obs-mini__meta { color: var(--text-muted); font-size: 12px; }
.obs-mini__go { margin-left: auto; color: var(--el-color-primary); font-size: 13px; flex-shrink: 0; }
.species-drawer__note { color: var(--text-muted); font-size: 12px; }
</style>
