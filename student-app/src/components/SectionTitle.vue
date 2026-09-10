<template>
  <view class="sec">
    <text class="sec__title">{{ title }}</text>
    <text v-if="more" class="sec__more" @tap="$emit('more')">{{ more }} ›</text>
  </view>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ title: string; more?: string }>(), { more: "" })
defineEmits<{ (event: "more"): void }>()
</script>
