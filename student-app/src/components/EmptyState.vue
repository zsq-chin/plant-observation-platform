<template>
  <view class="empty-state">
    <text class="empty-state__icon">{{ icon }}</text>
    <text class="empty-state__title">{{ title }}</text>
    <text v-if="hint" class="empty-state__hint">{{ hint }}</text>
    <slot />
  </view>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ icon?: string; title: string; hint?: string }>(), { icon: "🌱", hint: "" })
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 80rpx 40rpx;
  text-align: center;
}
.empty-state__icon { font-size: 72rpx; line-height: 1; opacity: 0.85; }
.empty-state__title { font-size: 29rpx; color: #55645a; font-weight: 600; }
.empty-state__hint { font-size: 24rpx; color: #8a968c; }
</style>
