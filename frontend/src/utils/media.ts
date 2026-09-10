/** 统一媒体 URL 解析（V3 §14）：禁止页面自行拼 URL。 */
export const PLANT_PLACEHOLDER = "/images/plant-placeholder.svg"

export function resolveMediaUrl(value?: string | null): string {
  if (!value) {
    return PLANT_PLACEHOLDER
  }
  if (
    value.startsWith("http://") ||
    value.startsWith("https://") ||
    value.startsWith("data:") ||
    value.startsWith("blob:")
  ) {
    return value
  }
  if (value.startsWith("/")) {
    return value
  }
  return `/media/plants/${value}`
}

/** 图片加载失败回退占位图（onerror 回调）。 */
export function onMediaError(event: Event): void {
  const img = event.target as HTMLImageElement
  if (img && img.src !== PLANT_PLACEHOLDER) {
    img.src = PLANT_PLACEHOLDER
  }
}
