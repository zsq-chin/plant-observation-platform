<!-- eslint-disable -->
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue"

export interface ProvinceStat {
  provinceCode: string
  provinceName?: string | null
  observationCount: number | string
}

const props = withDefaults(defineProps<{ stats: ProvinceStat[]; height?: number }>(), {
  height: 320,
})

const emit = defineEmits<{ (event: "select", provinceCode: string, provinceName: string): void }>()

const failed = ref(false)

/** 传给 renderjs 的数据（需可序列化） */
const renderPayload = computed(() => ({
  stats: props.stats.map((item) => ({
    provinceCode: String(item.provinceCode || ""),
    provinceName: String(item.provinceName || ""),
    observationCount: Number(item.observationCount || 0),
  })),
  height: props.height,
}))

/** 去重：callMethod 与 DOM 事件可能同时到达，600ms 内相同省份只处理一次 */
let lastSelect = { code: "", at: 0 }

/** 点击省份（renderjs 通过 callMethod 或 window 事件调用本方法） */
function onProvinceSelect(payload: { code?: string; name?: string }) {
  const code = String(payload?.code || "")
  if (!code) return
  const now = Date.now()
  if (code === lastSelect.code && now - lastSelect.at < 600) return
  lastSelect = { code, at: now }
  emit("select", code, String(payload?.name || code))
}

defineExpose({ onProvinceSelect, failed })

/** H5：renderjs 在视图层通过 window 自定义事件回传（uni 对象在 renderjs 中不可见） */
function onWindowSelect(event: Event) {
  onProvinceSelect((((event as CustomEvent).detail || {}) as { code?: string; name?: string }))
}

onMounted(() => {
  if (typeof window !== "undefined" && typeof window.addEventListener === "function") {
    window.addEventListener("china-map-select", onWindowSelect)
  }
})

onUnmounted(() => {
  if (typeof window !== "undefined" && typeof window.removeEventListener === "function") {
    window.removeEventListener("china-map-select", onWindowSelect)
  }
})
</script>

<template>
  <!--
    重要（实测结论）：
    1) uni-app H5 的 <view> 不会把 id 透传到 DOM（document.getElementById 永远拿不到），
       因此不能在逻辑层用 id 取容器初始化 ECharts；
    2) renderjs 的 this.$el 指向「组件根元素」，所以承载地图的根节点必须自带高度，
       否则 ECharts 会拿到 0 高度容器（canvas 高 0，看起来"地图没出来"）。
    这里把高度、prop 绑定与 renderjs 渲染统一放在根节点上，H5 与原生 App 一致。
  -->
  <!-- @vue-ignore renderjs 模块由视图层执行，逻辑层类型系统无法推断 -->
  <view
    class="china-map"
    :style="{ height: height + 'px' }"
    :prop="renderPayload"
    :change:prop="mapRender.render"
  >
    <view class="china-map__note">按省级聚合展示，点击省份查看该省学生作品</view>
  </view>
</template>

<script module="mapRender" lang="renderjs" src="./china-map.renderjs.js"></script>

<style scoped>
.china-map { position: relative; width: 100%; background: #f7faf4; border-radius: 14rpx; overflow: hidden; }
.china-map__note { position: absolute; left: 0; right: 0; bottom: 6rpx; text-align: center; color: #9aa79a; font-size: 20rpx; pointer-events: none; }
</style>
