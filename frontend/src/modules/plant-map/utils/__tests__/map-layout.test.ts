import { describe, expect, it } from "vitest"
import { connectorPath, layoutFeaturedCards, sideOf, type LayoutInput } from "../mapLayout"

function item(key: string, provinceCode: string, x: number, y: number): LayoutInput {
  return { key, provinceCode, anchor: { x, y } }
}

const options = { width: 1000, height: 600, cardWidth: 180, cardHeight: 120, gap: 14, padding: 16 }

describe("mapLayout 精选卡片布局", () => {
  it("西部省份放左侧、东部放右侧", () => {
    const cards = layoutFeaturedCards([item("a", "510000", 300, 200), item("b", "330000", 800, 260)], options)
    expect(cards.find((c) => c.key === "a")?.side).toBe("left")
    expect(cards.find((c) => c.key === "b")?.side).toBe("right")
    expect(cards.find((c) => c.key === "a")?.x).toBe(16)
    expect(cards.find((c) => c.key === "b")?.x).toBe(1000 - 180 - 16)
  })

  it("同侧卡片按锚点 Y 排序且不重叠", () => {
    const cards = layoutFeaturedCards([item("late", "510000", 300, 400), item("early", "520000", 320, 100)], options)
    const left = cards.filter((c) => c.side === "left").sort((a, b) => a.y - b.y)
    expect(left.map((c) => c.key)).toEqual(["early", "late"])
    expect(left[1].y - left[0].y).toBeGreaterThanOrEqual(options.cardHeight + options.gap)
  })

  it("卡片整体不会超出画布底部", () => {
    const many = [1, 2, 3, 4, 5, 6].map((i) => item("k" + i, "51000" + i, 200 + i, 100 + i * 90))
    const cards = layoutFeaturedCards(many, options)
    for (const card of cards) {
      expect(card.y + options.cardHeight).toBeLessThanOrEqual(options.height)
    }
  })

  it("sideOf 以地图中线分侧", () => {
    expect(sideOf({ x: 499, y: 0 }, 1000)).toBe("left")
    expect(sideOf({ x: 501, y: 0 }, 1000)).toBe("right")
  })

  it("引导线为折线并连接到卡片侧边", () => {
    const cards = layoutFeaturedCards([item("a", "510000", 300, 200)], options)
    const path = connectorPath(cards[0], 180, 120)
    expect(path.startsWith("M 300.0 200.0")).toBe(true)
    expect(path.split("L").length - 1).toBe(3)
    expect(path.endsWith((16 + 180).toFixed(1) + " " + (cards[0].y + 60).toFixed(1))).toBe(true)
  })
})
