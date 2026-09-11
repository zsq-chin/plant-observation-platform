<template>
  <view class="pcard" hover-class="pcard--press" :hover-stay-time="80" @tap="open">
    <view class="pcard__cover-wrap">
      <image class="pcard__cover" :src="resolveMediaUrl(item.coverUrl)" mode="aspectFill" @error="onImgError" />
      <text v-if="item.featured" class="pcard__star">★</text>
    </view>
    <view class="pcard__body">
      <text class="pcard__name ellipsis">{{ item.commonName || item.reportedCommonName || '未知植物' }}</text>
      <text v-if="item.scientificName" class="pcard__sci ellipsis">{{ item.scientificName }}</text>
      <view class="pcard__place">
        <image class="pcard__icon" src="/static/icons/location.svg" mode="aspectFit" />
        <text class="pcard__place-text ellipsis">{{ place }}</text>
      </view>
      <view class="pcard__foot">
        <image class="pcard__icon" src="/static/icons/chat.svg" mode="aspectFit" />
        <text class="pcard__stat">{{ numberValue(item.commentCount) }}</text>
        <image class="pcard__icon" src="/static/icons/star.svg" mode="aspectFit" />
        <text class="pcard__stat">{{ item.averageRating ?? '-' }}</text>
        <image class="pcard__icon" src="/static/icons/eye.svg" mode="aspectFit" />
        <text class="pcard__stat">{{ numberValue(item.viewCount) }}</text>
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
  transition: transform 0.15s ease;
}
.pcard--press { transform: scale(0.985); background: #fbfdfa; }
.pcard__cover-wrap { position: relative; width: 220rpx; height: 208rpx; flex-shrink: 0; }
.pcard__cover { width: 100%; height: 100%; background: #eef4ea; }
.pcard__star {
  position: absolute; left: 10rpx; top: 10rpx; width: 40rpx; height: 40rpx; border-radius: 50%;
  background: rgba(245, 166, 35, 0.94); color: #fff; font-size: 24rpx; text-align: center; line-height: 40rpx;
}
.pcard__body { flex: 1; min-width: 0; padding: 20rpx 22rpx; display: flex; flex-direction: column; gap: 8rpx; }
.pcard__name { font-size: 31rpx; font-weight: 600; }
.pcard__sci { font-size: 23rpx; color: #8a968c; font-style: italic; }
.pcard__place { display: flex; align-items: center; gap: 8rpx; }
.pcard__place-text { font-size: 23rpx; color: #55645a; }
.pcard__foot { display: flex; align-items: center; gap: 8rpx; margin-top: auto; }
.pcard__stat { font-size: 22rpx; color: #7b8a80; margin-right: 8rpx; }
.pcard__icon { width: 26rpx; height: 26rpx; opacity: 0.62; }
.pcard__author { font-size: 22rpx; color: #a8b3aa; margin-left: auto; max-width: 150rpx; }
.ellipsis { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
</style>
