<template>
  <view class="china-map">
    <!-- #ifdef H5 -->
    <view v-if="supported" id="china-map-canvas" class="china-map__canvas" :style="{ height: height + 'px' }"></view>
    <view v-else class="china-map__fallback">当前环境不支持地图渲染，已切换为省份列表</view>
    <!-- #endif -->
    <!-- #ifndef H5 -->
    <view class="china-map__fallback">当前平台暂使用省份列表模式（H5 / App 壳内置 2D 地图）</view>
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from "vue"

export interface ProvinceStat {
  provinceCode: string
  provinceName?: string | null
  observationCount: number | string
}

const props = withDefaults(defineProps<{ stats: ProvinceStat[]; height?: number; selected?: string | null }>(), {
  height: 320,
  selected: null,
})

const emit = defineEmits<{ (event: "select", provinceCode: string, provinceName: string): void }>()

// H5 之外（小程序/原生）不加载 echarts；H5 中由 WebView 渲染 DOM
const isH5 = ref(false)
const supported = ref(true)
interface MapChartInstance {
  setOption: (option: unknown) => void
  on: (event: string, handler: (params: { name?: string }) => void) => void
  resize: () => void
  dispose: () => void
}
let chart: MapChartInstance | null = null
let nameToCode: Record<string, string> = {}

function numberValue(v: number | string | null | undefined): number {
  const n = typeof v === "number" ? v : Number(v || 0)
  return Number.isFinite(n) ? n : 0
}

async function loadGeo(): Promise<unknown> {
  const response = await fetch("/static/geo/china-provinces.json")
  if (!response.ok) throw new Error("地图数据加载失败")
  return response.json()
}

async function render() {
  if (!isH5.value) return
  const container = document.getElementById("china-map-canvas")
  if (!container) return
  try {
    const [echartsCore, charts, components, renderers] = await Promise.all([
      import("echarts/core"),
      import("echarts/charts"),
      import("echarts/components"),
      import("echarts/renderers"),
    ])
    echartsCore.use([charts.MapChart, components.TooltipComponent, components.VisualMapComponent, renderers.CanvasRenderer])
    const geo = (await loadGeo()) as { features?: Array<{ properties?: { name?: string; adcode?: string | number } }> }
    nameToCode = {}
    for (const feature of geo.features || []) {
      const name = String(feature.properties?.name || "")
      const code = String(feature.properties?.adcode || "")
      if (name && code) nameToCode[name] = code
    }
    echartsCore.registerMap("china", geo as never)
    const data = props.stats.map((item) => ({
      name: item.provinceName || "",
      value: numberValue(item.observationCount),
    }))
    const maxValue = Math.max(1, ...data.map((d) => d.value))
    if (!chart) {
      chart = echartsCore.init(container) as unknown as MapChartInstance
      chart.on("click", (params: { name?: string }) => {
        const name = String(params?.name || "")
        const code = nameToCode[name]
        if (code) emit("select", code, name)
      })
    }
    chart?.setOption({
      tooltip: {
        trigger: "item",
        formatter: (params: { name?: string; value?: number }) =>
          (params?.name || "") + "<br/>" + numberValue(params?.value) + " 条观察",
      },
      visualMap: {
        min: 0,
        max: maxValue,
        left: 8,
        bottom: 8,
        itemWidth: 10,
        itemHeight: 60,
        text: ["多", "少"],
        textStyle: { fontSize: 10, color: "#5b6b50" },
        inRange: { color: ["#eef5e7", "#c3ddab", "#8fbf72", "#4f8a44", "#2f6b31"] },
      },
      series: [
        {
          type: "map",
          map: "china",
          roam: true,
          zoom: 1.18,
          label: { show: true, fontSize: 9, color: "#33512a" },
          itemStyle: { borderColor: "#ffffff", borderWidth: 0.6, areaColor: "#eef4e8" },
          emphasis: {
            label: { show: true, fontSize: 10, fontWeight: "bold", color: "#1f3d18" },
            itemStyle: { areaColor: "#ffd166" },
          },
          select: { itemStyle: { areaColor: "#e6a23c" } },
          data,
        },
      ],
    })
  } catch (error) {
    console.error("2D 地图渲染失败", error)
    supported.value = false
  }
}

onMounted(() => {
  // #ifdef H5
  isH5.value = true
  void render()
  // #endif
})

onUnmounted(() => {
  chart?.dispose()
  chart = null
})

watch(
  () => props.stats,
  () => {
    void render()
  },
  { deep: true },
)
</script>

<style scoped>
.china-map { width: 100%; }
.china-map__canvas { width: 100%; background: #f7faf4; border-radius: 14rpx; }
.china-map__fallback { color: #999; font-size: 24rpx; padding: 30rpx 0; text-align: center; }
</style>
