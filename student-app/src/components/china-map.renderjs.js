/**
 * 手机端 2D 中国地图：renderjs 视图层实现（H5 与 uni-app 原生 App 通用）。
 *
 * 为什么用 renderjs：
 *  - H5：this.$el 是真实的 DOM 节点（uni-view），可安全交给 ECharts；
 *    若在逻辑层用 document.getElementById 拿 uni 组件节点再初始化，会因 Vue 重渲染
 *    出现容器 id 丢失、高度为 0 的问题。
 *  - App：逻辑层没有 DOM，renderjs 运行在 WebView 视图层，可直接加载 ECharts。
 * 点击省份：通过 this.$ownerInstance.callMethod 回传逻辑层（ChinaMap2D.vue 的 onProvinceSelect）。
 */
import * as echarts from "echarts"

const GEO_URL = "static/geo/china-provinces.json"

export default {
  data() {
    return {
      chart: null,
      nameToCode: {},
      ready: false,
      payload: null,
    }
  },
  mounted() {
    this.init()
  },
  beforeDestroy() {
    if (this.chart) {
      this.chart.dispose()
      this.chart = null
    }
  },
  methods: {
    async init() {
      if (this.chart) return
      const el = this.$el
      if (!el) return
      try {
        const response = await fetch(GEO_URL)
        if (!response.ok) throw new Error("地图数据加载失败 " + response.status)
        const geo = await response.json()
        const map = {}
        for (const feature of geo.features || []) {
          const name = String((feature.properties || {}).name || "")
          const code = String((feature.properties || {}).adcode || "")
          if (name && code) map[name] = code
        }
        this.nameToCode = map
        echarts.registerMap("china", geo)
        this.chart = echarts.init(el)
        this.chart.on("click", (params) => {
          const name = String((params && params.name) || "")
          const code = this.nameToCode[name]
          console.log("[china-map] click", name, code, "ownerInstance=", typeof this.$ownerInstance)
          if (!code) return
          this.emitSelect(code, name)
        })
        this.ready = true
        this.applyOption(this.payload)
      } catch (error) {
        console.error("地图初始化失败", error)
      }
    },
    /**
     * 回传逻辑层（双通道）：
     *  - 原生 App：视图层与逻辑层分离，用 $ownerInstance.callMethod；
     *  - H5：renderjs 与逻辑层同处一个 window，但 uni 对象在 renderjs 中不可见，
     *    改用 window 自定义事件通知逻辑层（逻辑层会做去重，避免双通道重复触发）。
     */
    emitSelect(code, name) {
      const payload = { code: code, name: name }
      if (this.$ownerInstance && typeof this.$ownerInstance.callMethod === "function") {
        try {
          this.$ownerInstance.callMethod("onProvinceSelect", payload)
        } catch (error) {
          console.warn("[china-map] callMethod 不可用，改用 DOM 事件", error)
        }
      }
      if (typeof window !== "undefined" && typeof window.dispatchEvent === "function") {
        window.dispatchEvent(new CustomEvent("china-map-select", { detail: payload }))
      }
    },
    applyOption(payload) {
      if (!this.chart) return
      const stats = (payload && payload.stats) || []
      const data = stats.map((item) => ({ name: item.provinceName, value: Number(item.observationCount || 0) }))
      const maxValue = Math.max(1, ...data.map((d) => d.value))
      this.chart.setOption({
        tooltip: { trigger: "item" },
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
            emphasis: { label: { show: true, fontWeight: "bold" }, itemStyle: { areaColor: "#ffd166" } },
            data,
          },
        ],
      })
      this.chart.resize()
    },
    render(newValue) {
      this.payload = newValue
      if (this.ready) this.applyOption(newValue)
      else this.init()
    },
  },
}
