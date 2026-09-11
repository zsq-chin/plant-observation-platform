<template>
  <view class="empty-state">
    <view class="empty-state__badge">
      <image class="empty-state__icon" :src="'/static/icons/' + icon + '.svg'" mode="aspectFit" />
    </view>
    <text class="empty-state__title">{{ title }}</text>
    <text v-if="hint" class="empty-state__hint">{{ hint }}</text>
    <slot />
  </view>
</template>

<script setup lang="ts">
/** icon 传 /static/icons 下的图标名（如 plant / map / gallery / bell） */
withDefaults(defineProps<{ icon?: string; title: string; hint?: string }>(), { icon: "plant", hint: "" })
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  padding: 80rpx 40rpx;
  text-align: center;
}
.empty-state__badge {
  width: 132rpx;
  height: 132rpx;
  border-radius: 50%;
  background: linear-gradient(180deg, #f1f7ec, #e6f0df);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8rpx;
}
.empty-state__icon { width: 62rpx; height: 62rpx; opacity: 0.85; }
.empty-state__title { font-size: 29rpx; color: #55645a; font-weight: 600; }
.empty-state__hint { font-size: 24rpx; color: #8a968c; }
</style>
