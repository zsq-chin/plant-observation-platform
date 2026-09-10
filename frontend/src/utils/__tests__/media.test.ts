import { describe, expect, it } from "vitest"
import { onMediaError, PLANT_PLACEHOLDER, resolveMediaUrl } from "@/utils/media"

describe("resolveMediaUrl", () => {
  it("returns placeholder for null and empty", () => {
    expect(resolveMediaUrl(null)).toBe(PLANT_PLACEHOLDER)
    expect(resolveMediaUrl(undefined)).toBe(PLANT_PLACEHOLDER)
    expect(resolveMediaUrl("")).toBe(PLANT_PLACEHOLDER)
  })

  it("keeps absolute http(s) urls", () => {
    const http = "http://cdn.example/1.jpg"
    const https = "https://cdn.example/1.jpg"
    expect(resolveMediaUrl(http)).toBe(http)
    expect(resolveMediaUrl(https)).toBe(https)
  })

  it("keeps root-relative /media urls", () => {
    const url = "/media/plants/original/9281/a.jpg"
    expect(resolveMediaUrl(url)).toBe(url)
  })

  it("prefixes storage keys with /media/plants/", () => {
    expect(resolveMediaUrl("original/9281/a.jpg")).toBe("/media/plants/original/9281/a.jpg")
  })

  it("keeps data and blob urls", () => {
    const data = "data:image/png;base64,xxx"
    const blob = "blob:http://localhost/abc"
    expect(resolveMediaUrl(data)).toBe(data)
    expect(resolveMediaUrl(blob)).toBe(blob)
  })
})

describe("onMediaError", () => {
  it("falls back to placeholder without looping", () => {
    const img = { src: "/media/plants/original/1/broken.jpg" } as HTMLImageElement
    onMediaError({ target: img } as unknown as Event)
    expect(img.src).toBe(PLANT_PLACEHOLDER)
  })
})
