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

// 地图边界内容指纹（构建期注入）；文件名不带哈希，靠查询串破长缓存
const GEO_VERSION = typeof __GEO_VERSION__ === "string" ? __GEO_VERSION__ : "dev"
const GEO_URL = "static/geo/china-provinces.json?v=" + GEO_VERSION

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
        console.log("[1] 开始加载地图数据", GEO_URL)
        const response = await fetch(GEO_URL)
        if (!response.ok) throw new Error("地图数据加载失败 " + response.status)
        const geo = await response.json()
        const map = {}
        let taiwanFound = false
        for (const feature of geo.features || []) {
          const props = feature.properties || {}
          const name = String(props.name || "")
          // 编码兜底：properties.adcode 优先，其次 feature.id（不同来源 GeoJSON 字段不一致）
          const code = String(props.adcode || feature.id || "")
          if (name && code) map[name] = code
          if (code === "710000" || name === "台湾省") taiwanFound = true
        }
        this.nameToCode = map
        console.log("[2] GeoJSON 省份数", Object.keys(map).length, "含台湾", taiwanFound, "四川=", map["四川省"])
        echarts.registerMap("china", geo)
        this.chart = echarts.init(el)
        this.chart.on("click", (params) => {
          const name = String((params && params.name) || "")
          const code = this.nameToCode[name]
          console.log("[3] ECharts 点击", name, "-> provinceCode =", code || "(未匹配)")
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
      console.log("[4] 回传逻辑层", JSON.stringify(payload), "callMethod=", !!(this.$ownerInstance && this.$ownerInstance.callMethod))
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
      // 台湾面积小：数据项标签上移，避免文字压住岛屿本体（与 PC 端一致）
      data.push({ name: "台湾省", value: 0, label: { show: true, position: "top" } })
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
          inRange: { color: ["#bfd9a4", "#9cc97c", "#78b055", "#548f3c", "#2f6b31"] },
        },
        series: [
          {
            type: "map",
            map: "china",
            roam: true,
            zoom: 1.18,
            label: { show: true, fontSize: 9, color: "#33512a" },
            // 无数据省份底色加深（原 #eef4e8 太浅，台湾等小岛几乎看不出），边界加粗便于辨认轮廓
            itemStyle: { borderColor: "#4f7a3c", borderWidth: 1.1, areaColor: "#bfd9a4" },
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
