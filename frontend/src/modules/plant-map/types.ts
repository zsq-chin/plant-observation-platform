export type MapStage =
  | "CHINA_OVERVIEW"
  | "PROVINCE_FLYING"
  | "PROVINCE_OVERVIEW"
  | "SPECIES_FOCUS"
  | "OBSERVATION_FOCUS"
  | "RETURNING"

export interface TopSpeciesItem {
  speciesId: number | string
  commonName?: string | null
  coverUrl?: string | null
  observationCount: number | string
}

export interface RegionNode {
  regionCode: string
  regionName: string
  centerLng?: number | null
  centerLat?: number | null
  speciesCount: number | string
  observationCount: number | string
  studentCount: number | string
  topSpecies?: TopSpeciesItem[]
}

export interface ProvinceVisual {
  province: {
    code: string
    name?: string | null
    speciesCount: number | string
    observationCount: number | string
    studentCount: number | string
  }
  regions: RegionNode[]
  topSpecies: TopSpeciesItem[]
}

export interface ChinaStatRow {
  provinceCode: string
  provinceName?: string | null
  speciesCount: number | string
  observationCount: number | string
  studentCount: number | string
}

export interface MapFilter {
  categoryId?: string
  classId?: string
  year?: number
  keyword?: string
  speciesId?: string
}

export interface SpeciesObsRow {
  observationId: number | string
  coverUrl?: string | null
  commonName?: string | null
  provinceName?: string | null
  cityName?: string | null
  submitterName?: string | null
  className?: string | null
  observedAt?: string | null
  featured?: boolean
  averageRating?: number | null
  commentCount?: number | string
  viewCount?: number | string
}
