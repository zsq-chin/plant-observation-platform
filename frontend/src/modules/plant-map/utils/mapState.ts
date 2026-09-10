export type { MapStage } from "../types"

import type { MapStage } from "../types"

export type MapEvent =
  | "PROVINCE_CLICKED"
  | "FLY_FINISHED"
  | "SPECIES_CLICKED"
  | "OBSERVATION_CLICKED"
  | "BACK_TO_PROVINCE"
  | "BACK_TO_CHINA"

const TRANSITIONS: Record<MapStage, Partial<Record<MapEvent, MapStage>>> = {
  CHINA_OVERVIEW: { PROVINCE_CLICKED: "PROVINCE_FLYING" },
  PROVINCE_FLYING: { FLY_FINISHED: "PROVINCE_OVERVIEW", BACK_TO_CHINA: "RETURNING" },
  PROVINCE_OVERVIEW: {
    SPECIES_CLICKED: "SPECIES_FOCUS",
    OBSERVATION_CLICKED: "OBSERVATION_FOCUS",
    PROVINCE_CLICKED: "PROVINCE_FLYING",
    BACK_TO_CHINA: "RETURNING",
  },
  SPECIES_FOCUS: {
    OBSERVATION_CLICKED: "OBSERVATION_FOCUS",
    BACK_TO_PROVINCE: "PROVINCE_OVERVIEW",
    PROVINCE_CLICKED: "PROVINCE_FLYING",
    BACK_TO_CHINA: "RETURNING",
  },
  OBSERVATION_FOCUS: {
    BACK_TO_PROVINCE: "PROVINCE_OVERVIEW",
    PROVINCE_CLICKED: "PROVINCE_FLYING",
    BACK_TO_CHINA: "RETURNING",
  },
  RETURNING: { FLY_FINISHED: "CHINA_OVERVIEW" },
}

/** 状态机：非法迁移返回原状态（V3 §28/56）。 */
export function nextStage(current: MapStage, event: MapEvent): MapStage {
  const next = TRANSITIONS[current]?.[event]
  return next ?? current
}

export interface MapUrlState {
  province?: string
  species?: string | null
  speciesFilter?: string | null
  categoryId?: string | null
  classId?: string | null
  year?: number | null
}

export function parseMapQuery(query: Record<string, unknown>): MapUrlState {
  const province = typeof query.province === "string" ? query.province : undefined
  const speciesRaw = typeof query.species === "string" ? query.species : null
  const categoryId = typeof query.categoryId === "string" ? query.categoryId : null
  const classId = typeof query.classId === "string" ? query.classId : null
  const yearRaw = typeof query.year === "string" && /^\d{4}$/.test(query.year) ? Number(query.year) : null
  const speciesFilter = typeof query.sp === "string" ? query.sp : null
  return { province, species: speciesRaw, categoryId, classId, year: yearRaw, speciesFilter }
}

export function buildMapQuery(state: MapUrlState): Record<string, unknown> {
  const query: Record<string, unknown> = {}
  if (state.province) query.province = state.province
  if (state.species) query.species = state.species
  if (state.categoryId) query.categoryId = state.categoryId
  if (state.classId) query.classId = state.classId
  if (state.year) query.year = state.year
  if (state.speciesFilter) query.sp = state.speciesFilter
  return query
}