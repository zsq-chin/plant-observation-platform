import request from "@/api/request"
import { numberValue, type PageResult } from "@/api/plant"
import type { ChinaStatRow, MapFilter, ProvinceVisual, SpeciesObsRow } from "./types"

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

export function countOf(value: number | string | undefined): number {
  return numberValue(value)
}
