import request from "@/api/request"
import { numberValue, type PageResult } from "@/api/plant"
import type {
  ChinaStatRow,
  MapFeaturedWork,
  MapFilter,
  ProvinceVisual,
  ProvinceWorkRow,
  SpeciesObsRow,
} from "./types"

function unwrap<T>(payload: unknown): T {
  return (payload as { data: T }).data
}

function toParams(filter: MapFilter = {}) {
  const params: Record<string, unknown> = {}
  if (filter.categoryId) params.categoryId = filter.categoryId
  if (filter.classId) params.classId = filter.classId
  if (filter.year) params.year = filter.year
  if (filter.keyword) params.keyword = filter.keyword
  if (filter.speciesId) params.speciesId = filter.speciesId
  return params
}

export async function fetchChinaStats(filter: MapFilter = {}): Promise<ChinaStatRow[]> {
  return unwrap<ChinaStatRow[]>(await request.get("/api/public/plant/map/china", { params: toParams(filter) }))
}

export async function fetchProvinceVisual(provinceCode: string, filter: MapFilter = {}): Promise<ProvinceVisual> {
  return unwrap<ProvinceVisual>(
    await request.get("/api/public/plant/map/provinces/" + provinceCode + "/visualization", { params: toParams(filter) }),
  )
}

export async function fetchSpeciesObservations(
  speciesId: number | string,
  provinceCode?: string,
): Promise<SpeciesObsRow[]> {
  const payload = (await request.get("/api/public/plant/map/species/" + String(speciesId) + "/observations", {
    params: { provinceCode: provinceCode || undefined, page: 1, size: 50 },
  })) as { data: PageResult<SpeciesObsRow> }
  return payload.data.records
}

/** 全国地图精选作品（每省最多 1 条，§4.1）。 */
export async function fetchFeaturedMapWorks(size = 8): Promise<MapFeaturedWork[]> {
  return unwrap<MapFeaturedWork[]>(await request.get("/api/public/plant/map/featured-works", { params: { size } }))
}

/** 某省学生作品（点击省份后加载，§3.3/§7.3）。 */
export async function fetchProvinceWorks(
  provinceCode: string,
  page = 1,
  size = 12,
): Promise<PageResult<ProvinceWorkRow>> {
  return unwrap<PageResult<ProvinceWorkRow>>(
    await request.get("/api/public/plant/map/provinces/" + provinceCode + "/works", { params: { page, size } }),
  )
}

export function countOf(value: number | string | undefined): number {
  return numberValue(value)
}
