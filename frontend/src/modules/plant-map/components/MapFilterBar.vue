<template>
  <div class="map-filter-bar">
    <el-form inline size="small">
      <el-form-item label="类别">
        <el-select v-model="local.categoryId" placeholder="全部类别" clearable style="width: 130px">
          <el-option v-for="c in categories" :key="String(c.id)" :label="c.name" :value="String(c.id)" />
        </el-select>
      </el-form-item>
      <el-form-item label="班级">
        <el-select v-model="local.classId" placeholder="全部班级" clearable style="width: 130px">
          <el-option v-for="c in classes" :key="String(c.id)" :label="c.dictLabel" :value="String(c.id)" />
        </el-select>
      </el-form-item>
      <el-form-item label="年份">
        <el-select v-model="local.year" placeholder="全部年份" clearable style="width: 110px">
          <el-option v-for="y in years" :key="y" :label="String(y)" :value="y" />
        </el-select>
      </el-form-item>
      <el-form-item label="植物">
        <el-input v-model="local.keyword" placeholder="名称关键词" clearable style="width: 140px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="emit('apply')">应用筛选</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from "vue"
import type { CategoryItem, ClassItem } from "@/api/plant"
import type { MapFilter } from "../types"

const props = defineProps<{
  modelValue: MapFilter
  categories: CategoryItem[]
  classes: ClassItem[]
}>()

const emit = defineEmits<{ (event: "apply"): void }>()

const years = [2024, 2025, 2026]
const local = reactive<MapFilter>({ ...props.modelValue })

watch(() => props.modelValue, (value) => {
  local.categoryId = value.categoryId
  local.classId = value.classId
  local.year = value.year
  local.keyword = value.keyword
})

function reset() {
  local.categoryId = undefined
  local.classId = undefined
  local.year = undefined
  local.keyword = undefined
  emit("apply")
}

defineExpose({ getLocal: () => ({ ...local }) })
</script>

<style scoped>
.map-filter-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
</style>
