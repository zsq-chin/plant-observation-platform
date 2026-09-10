import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"
import FeaturedWorkOverlay from "../FeaturedWorkOverlay.vue"

const items = [
  {
    key: "1",
    provinceCode: "510000",
    anchor: { x: 200, y: 200 },
    work: { observationId: "1", commonName: "珙桐", provinceName: "四川省", displayName: "青禾", coverUrl: "/media/a.jpg" },
  },
  {
    key: "2",
    provinceCode: "330000",
    anchor: { x: 820, y: 300 },
    work: { observationId: "2", commonName: "银杏", provinceName: "浙江省", displayName: "木槿", coverUrl: "/media/b.jpg" },
  },
]

function mountOverlay() {
  return mount(FeaturedWorkOverlay, {
    props: { items, size: { width: 1000, height: 600 } },
    global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
  })
}

describe("FeaturedWorkOverlay 全国精选层", () => {
  it("为每张精选卡片绘制一条引导线", () => {
    const wrapper = mountOverlay()
    expect(wrapper.findAll(".featured-overlay__card")).toHaveLength(2)
    expect(wrapper.findAll("path")).toHaveLength(2)
    for (const path of wrapper.findAll("path")) {
      expect(path.attributes("d")).toMatch(/^M \d/)
    }
  })

  it("展示植物名、省份与花名，并链接到详情页", () => {
    const wrapper = mountOverlay()
    const text = wrapper.text()
    expect(text).toContain("珙桐")
    expect(text).toContain("四川省")
    expect(wrapper.html()).toContain("/plant/observations/1")
  })

  it("hover 卡片时对外抛出 hover 事件（用于省份与连线高亮）", async () => {
    const wrapper = mountOverlay()
    await wrapper.findAll(".featured-overlay__card")[0].trigger("mouseenter")
    expect(wrapper.emitted("hover")?.[0]).toEqual(["1"])
    await wrapper.findAll(".featured-overlay__card")[0].trigger("mouseleave")
    expect(wrapper.emitted("hover")?.[1]).toEqual([null])
  })

  it("无精选数据时展示空提示", () => {
    const wrapper = mount(FeaturedWorkOverlay, {
      props: { items: [], size: { width: 1000, height: 600 } },
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    })
    expect(wrapper.text()).toContain("暂无精选作品")
  })
})
