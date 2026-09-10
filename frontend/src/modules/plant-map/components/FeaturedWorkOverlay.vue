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
      <p class="featured-overlay__province">{{ card.work.provinceName || card.provinceCode }}</p>
      <PlantWorkCard :work="card.work" compact />
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
  cardWidth?: number
}>()

const emit = defineEmits<{ (event: "hover", key: string | null): void }>()

const cardWidth = computed(() => props.cardWidth ?? 168)
const cardHeight = 132

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
    gap: 12,
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
.featured-overlay__line { fill: none; stroke: rgba(63, 107, 46, 0.55); stroke-width: 1.4; stroke-dasharray: 6 4; transition: stroke 0.2s ease, stroke-width 0.2s ease; }
.featured-overlay__line.is-active { stroke: #e6a23c; stroke-width: 2.4; stroke-dasharray: none; }
.featured-overlay__line.is-dim { stroke: rgba(63, 107, 46, 0.18); }
.featured-overlay__card { position: absolute; pointer-events: auto; display: flex; flex-direction: column; gap: 4px; transition: transform 0.18s ease; }
.featured-overlay__card:hover, .featured-overlay__card.is-active { transform: scale(1.04); }
.featured-overlay__province { margin: 0; font-size: 12px; font-weight: 700; color: #2f4a20; background: rgba(255, 255, 255, 0.9); border-radius: 999px; padding: 1px 8px; width: fit-content; }
.featured-overlay__empty { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); color: var(--text-muted); font-size: 13px; }
</style>
