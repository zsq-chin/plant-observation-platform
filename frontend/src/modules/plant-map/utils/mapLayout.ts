/** 全国地图精选卡片布局与引导线（下一步开发计划 §4.3/§4.4，纯函数便于单测）。 */

export interface Anchor {
  x: number
  y: number
}

export type CardSide = "left" | "right"

export interface LayoutInput {
  key: string
  provinceCode: string
  anchor: Anchor
  side?: CardSide
}

export interface LayoutCard {
  key: string
  provinceCode: string
  side: CardSide
  /** 卡片左上角坐标 */
  x: number
  y: number
  anchor: Anchor
}

export interface LayoutOptions {
  width: number
  height: number
  cardWidth: number
  cardHeight: number
  gap?: number
  padding?: number
  /** 地图核心保护区（横向比例，默认 25%~75%）：卡片尽量不进中央，避免遮挡中国主体 */
  protectedArea?: { minX: number; maxX: number }
}

/** 卡片所在侧：默认按锚点在地图左右半区决定（西部左侧、东部右侧）。 */
export function sideOf(anchor: Anchor, mapWidth: number): CardSide {
  return anchor.x < mapWidth / 2 ? "left" : "right"
}

/**
 * 自动布局：左右分侧 → 各侧按省份屏幕 Y 排序 → 自上而下排布并保证最小间距，
 * 超出画布时自下而上回压（不引入复杂物理布局算法）。
 */
export function layoutFeaturedCards(items: LayoutInput[], options: LayoutOptions): LayoutCard[] {
  const gap = options.gap ?? 14
  const padding = options.padding ?? 16
  const sides: Record<CardSide, LayoutInput[]> = { left: [], right: [] }
  for (const item of items) {
    const side = item.side ?? sideOf(item.anchor, options.width)
    sides[side].push(item)
  }
  const result: LayoutCard[] = []
  for (const side of ["left", "right"] as CardSide[]) {
    const column = sides[side].slice().sort((a, b) => a.anchor.y - b.anchor.y)
    const guard = options.protectedArea ?? { minX: 0.25, maxX: 0.75 }
    const guardLeft = options.width * guard.minX
    const guardRight = options.width * guard.maxX
    let x = side === "left" ? padding : Math.max(padding, options.width - options.cardWidth - padding)
    // 核心保护区：若卡片与中央区域相交，则按所在侧推到保护区外
    if (x + options.cardWidth > guardLeft && x < guardRight) {
      x =
        side === "left"
          ? Math.max(padding, guardLeft - options.cardWidth - 4)
          : Math.min(Math.max(padding, options.width - options.cardWidth - padding), guardRight + 4)
    }
    const maxY = Math.max(padding, options.height - options.cardHeight - padding)
    let cursor = padding
    const placed: LayoutCard[] = []
    for (const item of column) {
      const y = Math.min(cursor, maxY)
      placed.push({ key: item.key, provinceCode: item.provinceCode, side, x, y, anchor: item.anchor })
      cursor = y + options.cardHeight + gap
    }
    let overflow = placed.length
      ? placed[placed.length - 1].y + options.cardHeight - (options.height - padding)
      : 0
    for (let i = placed.length - 1; i >= 0 && overflow > 0; i -= 1) {
      const card = placed[i]
      const lowerBound = i === placed.length - 1 ? maxY : placed[i + 1].y - options.cardHeight - gap
      const next = Math.max(padding, Math.min(card.y - overflow, lowerBound))
      overflow -= card.y - next
      card.y = next
    }
    result.push(...placed)
  }
  return result
}

/** 折线引导线：省份锚点 → 水平折点 → 垂直折点 → 卡片侧边中点（§4.3）。 */
export function connectorPath(card: LayoutCard, cardWidth: number, cardHeight: number, elbow = 26): string {
  const startX = card.anchor.x
  const startY = card.anchor.y
  const endX = card.side === "left" ? card.x + cardWidth : card.x
  const endY = card.y + cardHeight / 2
  const elbowX = card.side === "left" ? endX + elbow : endX - elbow
  const midX = card.side === "left" ? Math.max(elbowX, startX + elbow / 2) : Math.min(elbowX, startX - elbow / 2)
  return [
    "M", startX.toFixed(1), startY.toFixed(1),
    "L", midX.toFixed(1), startY.toFixed(1),
    "L", midX.toFixed(1), endY.toFixed(1),
    "L", endX.toFixed(1), endY.toFixed(1),
  ].join(" ")
}
