<template>
  <div class="featured-overlay" :style="{ width: size.width + 'px', height: size.height + 'px' }" aria-hidden="false">
    <svg class="featured-overlay__lines" :width="size.width" :height="size.height" aria-hidden="true">
      <path
        v-for="line in lines"
        :key="line.key"
        :d="line.d"
        class="featured-overlay__line"
        :class="{ 'is-active': effectiveActive === line.key, 'is-dim': effectiveActive && effectiveActive !== line.key }"
      />
    </svg>
    <div
      v-for="card in cards"
      :key="card.key"
      class="featured-overlay__card"
      :class="{ 'is-active': effectiveActive === card.key }"
      :style="{ left: card.x + 'px', top: card.y + 'px', width: cardWidth + 'px' }"
      @mouseenter="emit('hover', card.key)"
      @mouseleave="emit('hover', null)"
      @focusin="emit('hover', card.key)"
      @focusout="emit('hover', null)"
    >
      <PlantWorkCard :work="card.work" compact variant="map" />
    </div>
    <p v-if="!cards.length" class="featured-overlay__empty">暂无精选作品</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue"
import PlantWorkCard from "./PlantWorkCard.vue"
import { connectorPath, layoutFeaturedCards, type LayoutInput } from "@/modules/plant-map/utils/mapLayout"
import type { MapFeaturedWork } from "@/modules/plant-map/types"

const props = defineProps<{
  items: Array<{ key: string; provinceCode: string; anchor: { x: number; y: number }; work: MapFeaturedWork }>
  size: { width: number; height: number }
  /** 当前 hover 的卡片 key（卡片自身 hover） */
  activeKey?: string | null
  /** 当前 hover 的省份编码（地图省份 hover 联动卡片） */
  highlightCode?: string | null
  /** 地图精选卡宽度（下一步计划 §4.2 推荐 120~136px） */
  cardWidth?: number
}>()

const emit = defineEmits<{ (event: "hover", key: string | null): void }>()

const cardWidth = computed(() => props.cardWidth ?? 128)
const cardHeight = 102

const cards = computed(() => {
  const inputs: LayoutInput[] = props.items.map((item) => ({
    key: item.key,
    provinceCode: item.provinceCode,
    anchor: item.anchor,
  }))
  const placed = layoutFeaturedCards(inputs, {
    width: props.size.width,
    height: props.size.height,
    cardWidth: cardWidth.value,
    cardHeight,
    gap: 10,
    padding: 14,
  })
  return placed.map((card) => ({
    ...card,
    work: (props.items.find((item) => item.key === card.key)?.work ?? {}) as MapFeaturedWork,
  }))
})

const lines = computed(() => cards.value.map((card) => ({ key: card.key, d: connectorPath(card, cardWidth.value, cardHeight) })))

const effectiveActive = computed(() => {
  if (props.activeKey) return props.activeKey
  if (!props.highlightCode) return null
  const hit = cards.value.find((card) => card.provinceCode === props.highlightCode)
  return hit ? hit.key : null
})
</script>

<style scoped>
.featured-overlay { position: absolute; inset: 0; pointer-events: none; z-index: 4; }
.featured-overlay__lines { position: absolute; inset: 0; }
.featured-overlay__line { fill: none; stroke: rgba(63, 107, 46, 0.28); stroke-width: 1; stroke-dasharray: 5 5; transition: stroke 0.2s ease, stroke-width 0.2s ease; }
.featured-overlay__line.is-active { stroke: #e6a23c; stroke-width: 2; stroke-dasharray: none; }
.featured-overlay__line.is-dim { stroke: rgba(63, 107, 46, 0.1); }
.featured-overlay__card { position: absolute; pointer-events: auto; display: flex; flex-direction: column; gap: 3px; transition: transform 0.18s ease; }
.featured-overlay__card:hover, .featured-overlay__card.is-active { transform: scale(1.06); z-index: 3; }
.featured-overlay__empty { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); color: var(--text-muted); font-size: 13px; }
</style>
