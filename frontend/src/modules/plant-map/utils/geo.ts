export interface LngLatBoundsTuple {
  west: number
  south: number
  east: number
  north: number
}

function walkCoordinates(value: unknown, visit: (lng: number, lat: number) => void): void {
  if (Array.isArray(value)) {
    if (value.length === 2 && typeof value[0] === "number" && typeof value[1] === "number") {
      visit(value[0], value[1])
      return
    }
    for (const item of value) walkCoordinates(item, visit)
  }
}

export function bboxOfFeature(feature: { geometry?: { type?: string; coordinates?: unknown } | null }): LngLatBoundsTuple | null {
  const geometry = feature.geometry
  if (!geometry || !geometry.coordinates) return null
  let west = Infinity
  let south = Infinity
  let east = -Infinity
  let north = -Infinity
  walkCoordinates(geometry.coordinates, (lng, lat) => {
    if (lng < west) west = lng
    if (lng > east) east = lng
    if (lat < south) south = lat
    if (lat > north) north = lat
  })
  if (!Number.isFinite(west)) return null
  return { west, south, east, north }
}

export interface GeoFeatureProps {
  adcode: string
  name: string
  observationCount: number
  speciesCount: number
  studentCount: number
  visualHeight: number
}

export type GeoFeature = {
  type: "Feature"
  id: string | number
  properties: GeoFeatureProps
  geometry: { type?: string; coordinates?: unknown } | null
}

const MIN_HEIGHT = 4000
const MAX_HEIGHT = 90000
const HEIGHT_FACTOR = 9000

/** 数据驱动高度：minHeight + sqrt(count)*factor，并做 clamp（V3 §29）。 */
export function visualHeightOf(count: number): number {
  if (count <= 0) return 2000
  const height = MIN_HEIGHT + Math.sqrt(count) * HEIGHT_FACTOR
  return Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT, height))
}

export function featureWithStats(feature: GeoFeature, count: number, species: number, students: number): GeoFeature {
  return {
    ...feature,
    properties: {
      ...feature.properties,
      observationCount: count,
      speciesCount: species,
      studentCount: students,
      visualHeight: visualHeightOf(count),
    },
  }
}
