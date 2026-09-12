<template>
  <view class="hero">
    <view class="hero__head">
      <view class="grow">
        <view class="hero__title">{{ title }}</view>
        <view v-if="subtitle" class="hero__sub">{{ subtitle }}</view>
      </view>
      <slot name="right" />
    </view>
    <slot />
  </view>
</template>

<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>

<style scoped>
/* 头图刻意保持紧凑：底部留白只够放圆角，避免像"一整块盖住内容" */
.hero {
  padding: calc(var(--status-bar-height, 0px) + 22rpx) 28rpx 34rpx;
  background: linear-gradient(135deg, #4aa64a 0%, #2f7a34 62%, #276b2e 100%);
  border-bottom-left-radius: 28rpx;
  border-bottom-right-radius: 28rpx;
  color: #ffffff;
  position: relative;
  overflow: hidden;
}
.hero::after {
  content: "";
  position: absolute;
  right: -60rpx;
  top: -70rpx;
  width: 220rpx;
  height: 220rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.09);
}
.hero__head {
  display: flex;
  align-items: center;
  gap: 18rpx;
  position: relative;
}
.hero__title {
  font-size: 40rpx;
  font-weight: 700;
  line-height: 1.2;
}
.hero__sub {
  font-size: 23rpx;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 4rpx;
}
.grow { flex: 1; min-width: 0; }
</style>
