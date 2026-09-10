<template>
  <div class="obs-card">
    <router-link class="obs-card__cover-link" :to="detailPath">
      <div class="obs-card__cover">
        <img v-if="item.coverUrl" :src="resolveMediaUrl(item.coverUrl)" :alt="coverAlt" loading="lazy" @error="onMediaError" />
        <div v-else class="obs-card__placeholder">🌿</div>
        <span v-if="item.featured" class="obs-card__badge">★ 优秀观察</span>
      </div>
    </router-link>
    <div class="obs-card__body">
      <router-link class="obs-card__name-link" :to="detailPath">
        <h3 class="obs-card__name">{{ item.commonName || item.reportedCommonName || '未知植物' }}</h3>
      </router-link>
      <p v-if="item.scientificName" class="obs-card__sci">{{ item.scientificName }}</p>
      <p class="obs-card__meta">
        <router-link v-if="item.provinceCode" class="obs-card__province" :to="provincePath" title="在 3D 地图中查看该省">
          📍 {{ item.provinceName }}{{ item.cityName ? ' · ' + item.cityName : '' }}
        </router-link>
      </p>
      <p class="obs-card__meta">
        <span v-if="item.submitterName">👤 {{ item.submitterName }}</span>
        <span v-if="item.className" class="obs-card__class">{{ item.className }}</span>
        <span v-if="fmtDate(item.observedAt)" class="obs-card__date">📅 {{ fmtDate(item.observedAt) }}</span>
      </p>
      <p class="obs-card__stats">
        <span v-if="item.averageRating !== null && item.averageRating !== undefined">⭐ {{ Number(item.averageRating).toFixed(1) }}</span>
        <span>💬 {{ numberValue(item.commentCount) }}</span>
        <span>👁 {{ numberValue(item.viewCount) }}</span>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { GalleryItem } from '@/api/plant'
import { fmtDate, numberValue } from '@/api/plant'
import { onMediaError, resolveMediaUrl } from '@/utils/media'

const props = defineProps<{ item: GalleryItem }>()

const coverAlt = computed(() => {
  const name = props.item.commonName || props.item.reportedCommonName || '植物'
  const at = props.item.provinceName || props.item.cityName || ''
  return at ? `${name}在${at}的观察照片` : `${name}观察照片`
})
const detailPath = computed(() => '/plant/observations/' + String(props.item.observationId))
const provincePath = computed(() => '/plant/map?province=' + String(props.item.provinceCode))
</script>

<style scoped>
.obs-card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-subtle);
  border-radius: 18px;
  overflow: hidden;
  background: var(--surface-elevated);
  transition: transform var(--transition-base), box-shadow var(--transition-base);
}
.obs-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-lg);
}
.obs-card__cover-link {
  display: block;
  color: inherit;
  text-decoration: none;
}
.obs-card__cover {
  position: relative;
  aspect-ratio: 4 / 3;
  background: linear-gradient(135deg, var(--brand-soft), #dfe8c8);
  display: flex;
  align-items: center;
  justify-content: center;
}
.obs-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.obs-card__placeholder {
  font-size: 56px;
  opacity: 0.55;
}
.obs-card__badge {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(250, 173, 20, 0.92);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.obs-card__body {
  padding: 14px 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
}
.obs-card__name-link {
  color: inherit;
  text-decoration: none;
}
.obs-card__name {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}
.obs-card__sci {
  margin: 2px 0 8px;
  color: var(--text-muted);
  font-size: 12px;
  font-style: italic;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.obs-card__meta {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.obs-card__province {
  color: var(--el-color-primary);
  text-decoration: none;
}
.obs-card__province:hover {
  text-decoration: underline;
}
.obs-card__class {
  padding: 0 8px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  font-size: 12px;
}
.obs-card__date {
  color: var(--text-muted);
}
.obs-card__stats {
  margin: 10px 0 0;
  display: flex;
  gap: 12px;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
