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
.hero {
  padding: calc(var(--status-bar-height, 0px) + 34rpx) 30rpx 76rpx;
  background: linear-gradient(135deg, #4aa64a 0%, #2f7a34 62%, #276b2e 100%);
  border-bottom-left-radius: 40rpx;
  border-bottom-right-radius: 40rpx;
  color: #ffffff;
  position: relative;
  overflow: hidden;
}
.hero::after {
  content: "";
  position: absolute;
  right: -70rpx;
  top: -80rpx;
  width: 300rpx;
  height: 300rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
}
.hero::before {
  content: "";
  position: absolute;
  left: -60rpx;
  bottom: -120rpx;
  width: 220rpx;
  height: 220rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.07);
}
.hero__head {
  display: flex;
  align-items: center;
  gap: 20rpx;
  position: relative;
}
.hero__title {
  font-size: 46rpx;
  font-weight: 700;
  line-height: 1.25;
}
.hero__sub {
  font-size: 25rpx;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 6rpx;
}
.grow { flex: 1; min-width: 0; }
</style>
