<template>
  <view class="fields">
    <view v-for="field in fields" :key="field.id" class="field">
      <view class="field__label">{{ field.fieldLabel }}<text v-if="field.required === 1" class="req">*</text></view>
      <picker v-if="field.fieldType === 'SELECT'" :range="options(field)" @change="onPick(field, $event)">
        <view class="input">{{ textValue(field) || '请选择' }}</view>
      </picker>
      <picker v-else-if="field.fieldType === 'MULTI_SELECT'" mode="multiSelector" :range="[options(field)]" @change="onMulti(field, $event)">
        <view class="input">{{ multiValue(field) || '请选择' }}</view>
      </picker>
      <switch v-else-if="field.fieldType === 'BOOLEAN'" :checked="boolValue(field)" @change="onBool(field, $event)" />
      <picker v-else-if="field.fieldType === 'DATE'" mode="date" @change="onText(field, $event.detail.value)">
        <view class="input">{{ value(field) || '选择日期' }}</view>
      </picker>
      <input v-else-if="field.fieldType === 'NUMBER'" type="number" class="input" :value="value(field)" @input="onInput(field, $event)" placeholder="请输入" />
      <textarea v-else-if="field.fieldType === 'TEXTAREA'" class="input textarea" :value="value(field)" @input="onInput(field, $event)" placeholder="请输入" />
      <input v-else class="input" :value="value(field)" @input="onInput(field, $event)" placeholder="请输入" />
    </view>
  </view>
</template>

<script setup lang="ts">
import type { FieldDef } from "@/types/models"

const props = defineProps<{ fields: FieldDef[]; model: Record<string, unknown> }>()

function options(field: FieldDef): string[] {
  try {
    const parsed = JSON.parse(field.optionsJson || "[]")
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    return []
  }
}
function value(field: FieldDef): string {
  const v = props.model[field.id]
  return v === undefined || v === null ? "" : String(v)
}
function textValue(field: FieldDef): string {
  const text = value(field)
  return field.fieldType === "SELECT" ? text : ""
}
function multiValue(field: FieldDef): string {
  const v = props.model[field.id]
  return Array.isArray(v) ? (v as string[]).join("、") : String(v || "")
}
function boolValue(field: FieldDef): boolean {
  return Boolean(props.model[field.id])
}
function set(field: FieldDef, v: unknown) {
  props.model[field.id] = v
}
function onInput(field: FieldDef, event: { detail: { value: string } }) {
  set(field, event.detail.value)
}
function onText(field: FieldDef, text: string) {
  set(field, text)
}
function onBool(field: FieldDef, event: { detail: { value: boolean } }) {
  set(field, event.detail.value)
}
function onPick(field: FieldDef, event: { detail: { value: number } }) {
  const list = options(field)
  set(field, list[event.detail.value] || "")
}
function onMulti(field: FieldDef, event: { detail: { value: number[] } }) {
  const list = options(field)
  const selected = (event.detail.value as unknown as number[]).map((i) => list[i]).filter(Boolean)
  set(field, selected)
}
</script>

<style scoped>
.fields { display: flex; flex-direction: column; gap: 18rpx; }
.field { display: flex; flex-direction: column; gap: 8rpx; }
.field__label { font-size: 26rpx; color: #444; }
.req { color: #e54d42; margin-left: 4rpx; }
.input { border: 1rpx solid #ddd; border-radius: 10rpx; padding: 14rpx 16rpx; background: #fff; font-size: 28rpx; }
.textarea { height: 120rpx; }
</style>
