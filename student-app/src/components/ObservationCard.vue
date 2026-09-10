<template>
  <view class="card" @tap="open">
    <image class="card__cover" :src="resolveMediaUrl(item.coverUrl)" mode="aspectFill" @error="onImgError" />
    <view class="card__body">
      <view class="card__name">{{ item.commonName || item.reportedCommonName || '未知植物' }}
        <text v-if="item.featured" class="badge">★ 优秀</text>
      </view>
      <text v-if="item.scientificName" class="muted">{{ item.scientificName }}</text>
      <view class="muted">{{ item.provinceName || '' }} {{ item.cityName || '' }} · {{ item.submitterName || '' }}</view>
      <view class="muted">💬 {{ numberValue(item.commentCount) }} ⭐ {{ item.averageRating ?? '-' }} 👁 {{ numberValue(item.viewCount) }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { GalleryItem } from "@/types/models"
import { PLANT_PLACEHOLDER } from "@/config"
import { fmtDate, numberValue, resolveMediaUrl } from "@/utils/media"

const props = defineProps<{ item: GalleryItem }>()
const emit = defineEmits<{ (event: "open", id: string): void }>()

function open() {
  emit("open", String(props.item.observationId))
}
function onImgError(event: unknown) {
  const img = event as { target?: { src?: string } }
  const anyTarget = img.target as { src?: string } | undefined
  if (anyTarget) anyTarget.src = PLANT_PLACEHOLDER
}
</script>

<style scoped>
.card { display: flex; background: #ffffff; border-radius: 16rpx; overflow: hidden; margin-bottom: 16rpx; }
.card__cover { width: 180rpx; height: 150rpx; flex-shrink: 0; }
.card__body { flex: 1; padding: 12rpx 16rpx; display: flex; flex-direction: column; gap: 4rpx; }
.card__name { font-size: 30rpx; font-weight: 600; }
.badge { font-size: 20rpx; color: #b7791f; margin-left: 8rpx; }
.muted { color: #888; font-size: 24rpx; }
</style>
