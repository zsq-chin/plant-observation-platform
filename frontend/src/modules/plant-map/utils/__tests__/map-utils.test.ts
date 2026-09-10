import { describe, expect, it } from "vitest"
import { buildMapQuery, nextStage, parseMapQuery } from "../mapState"
import { bboxOfFeature, featureWithStats, visualHeightOf, type GeoFeature } from "../geo"

describe("map state machine (V3 §28)", () => {
  it("moves CHINA -> FLYING -> PROVINCE", () => {
    expect(nextStage("CHINA_OVERVIEW", "PROVINCE_CLICKED")).toBe("PROVINCE_FLYING")
    expect(nextStage("PROVINCE_FLYING", "FLY_FINISHED")).toBe("PROVINCE_OVERVIEW")
  })

  it("moves PROVINCE -> SPECIES -> OBSERVATION and back", () => {
    expect(nextStage("PROVINCE_OVERVIEW", "SPECIES_CLICKED")).toBe("SPECIES_FOCUS")
    expect(nextStage("SPECIES_FOCUS", "OBSERVATION_CLICKED")).toBe("OBSERVATION_FOCUS")
    expect(nextStage("OBSERVATION_FOCUS", "BACK_TO_PROVINCE")).toBe("PROVINCE_OVERVIEW")
  })

  it("ignores invalid transitions (guard against double clicks)", () => {
    expect(nextStage("PROVINCE_FLYING", "SPECIES_CLICKED")).toBe("PROVINCE_FLYING")
    expect(nextStage("RETURNING", "PROVINCE_CLICKED")).toBe("RETURNING")
    expect(nextStage("CHINA_OVERVIEW", "BACK_TO_CHINA")).toBe("CHINA_OVERVIEW")
  })

  it("returns to CHINA after RETURNING settles", () => {
    expect(nextStage("RETURNING", "FLY_FINISHED")).toBe("CHINA_OVERVIEW")
  })
})

describe("map url state (V3 §49)", () => {
  it("parses and builds query state", () => {
    const parsed = parseMapQuery({ province: "330000", species: "10001", categoryId: "1", year: "2026" })
    expect(parsed.province).toBe("330000")
    expect(parsed.species).toBe("10001")
    expect(parsed.year).toBe(2026)
    const built = buildMapQuery({ province: "330000", species: "10001", categoryId: "1", classId: null, year: 2026 })
    expect(built.province).toBe("330000")
    expect(built.year).toBe(2026)
    expect(built.classId).toBeUndefined()
  })

  it("ignores malformed year", () => {
    expect(parseMapQuery({ year: "abc" }).year).toBeNull()
  })
})

describe("geo utils", () => {
  it("computes bbox of a polygon feature", () => {
    const feature = {
      type: "Feature" as const,
      id: "330000",
      properties: { adcode: "330000", name: "浙江省", observationCount: 0, speciesCount: 0, studentCount: 0, visualHeight: 0 },
      geometry: {
        type: "Polygon",
        coordinates: [[[118, 27], [123, 27], [123, 31], [118, 31], [118, 27]]],
      },
    } as unknown as GeoFeature
    const bounds = bboxOfFeature(feature)
    expect(bounds).not.toBeNull()
    expect(bounds?.west).toBe(118)
    expect(bounds?.north).toBe(31)
  })

  it("clamps visual height by sqrt of count", () => {
    expect(visualHeightOf(0)).toBe(2000)
    expect(visualHeightOf(10000)).toBeLessThanOrEqual(90000)
    const small = visualHeightOf(1)
    expect(small).toBeGreaterThanOrEqual(4000)
    expect(featureWithStats({} as GeoFeature, 9, 2, 3).properties.observationCount).toBe(9)
  })
})
