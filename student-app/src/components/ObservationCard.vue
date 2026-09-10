<template>
  <view class="pcard" @tap="open">
    <image class="pcard__cover" :src="resolveMediaUrl(item.coverUrl)" mode="aspectFill" @error="onImgError" />
    <view class="pcard__body">
      <view class="pcard__top">
        <text class="pcard__name ellipsis">{{ item.commonName || item.reportedCommonName || '未知植物' }}</text>
        <text v-if="item.featured" class="chip chip--warn">★ 优秀</text>
      </view>
      <text v-if="item.scientificName" class="pcard__sci ellipsis">{{ item.scientificName }}</text>
      <view class="pcard__meta">
        <text class="chip chip--brand">📍 {{ place }}</text>
      </view>
      <view class="pcard__foot">
        <text class="pcard__stat">💬 {{ numberValue(item.commentCount) }}</text>
        <text class="pcard__stat">⭐ {{ item.averageRating ?? '-' }}</text>
        <text class="pcard__stat">👁 {{ numberValue(item.viewCount) }}</text>
        <text class="pcard__author ellipsis">{{ item.submitterName || '' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from "vue"
import type { GalleryItem } from "@/types/models"
import { PLANT_PLACEHOLDER } from "@/config"
import { numberValue, resolveMediaUrl } from "@/utils/media"

const props = defineProps<{ item: GalleryItem }>()
const emit = defineEmits<{ (event: "open", id: string): void }>()

const place = computed(() => {
  const parts = [props.item.provinceName, props.item.cityName].filter(Boolean)
  return parts.length ? parts.join(" ") : "未知地点"
})

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
.pcard {
  display: flex;
  background: #ffffff;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 8rpx 24rpx rgba(31, 45, 36, 0.06);
  margin-bottom: 18rpx;
}
.pcard__cover {
  width: 220rpx;
  height: 200rpx;
  flex-shrink: 0;
  background: #eef4ea;
}
.pcard__body {
  flex: 1;
  min-width: 0;
  padding: 20rpx 22rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.pcard__top { display: flex; align-items: center; gap: 10rpx; }
.pcard__name { font-size: 31rpx; font-weight: 600; flex: 1; min-width: 0; }
.pcard__sci { font-size: 23rpx; color: #8a968c; font-style: italic; }
.pcard__meta { display: flex; flex-wrap: wrap; gap: 8rpx; }
.pcard__foot { display: flex; align-items: center; gap: 16rpx; margin-top: auto; }
.pcard__stat { font-size: 22rpx; color: #7b8a80; }
.pcard__author { font-size: 22rpx; color: #a8b3aa; margin-left: auto; max-width: 150rpx; }
.ellipsis { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
</style>
