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
      <picker v-else-if="field.fieldType === 'DATE'" mode="date" @change="onText(field, $event)">
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
/** 统一从 uni 事件中安全读取 detail（兼容 Event / InputEvent / 自定义 detail 类型） */
function detailValue(event: unknown): unknown {
  return (event as { detail?: { value?: unknown } } | undefined)?.detail?.value
}

function onInput(field: FieldDef, event: Event) {
  set(field, String(detailValue(event) ?? ""))
}

function onText(field: FieldDef, event: Event) {
  set(field, String(detailValue(event) ?? ""))
}

function onBool(field: FieldDef, event: Event) {
  set(field, Boolean(detailValue(event)))
}

function onPick(field: FieldDef, event: Event) {
  const list = options(field)
  const index = Number(detailValue(event))
  set(field, Number.isFinite(index) ? list[index] || "" : "")
}

function onMulti(field: FieldDef, event: Event) {
  const list = options(field)
  const raw = detailValue(event)
  const indexes = Array.isArray(raw) ? raw.map(Number) : []
  const selected = indexes.map((i) => list[i]).filter(Boolean)
  set(field, selected)
}
</script>

<style scoped>
.fields { display: flex; flex-direction: column; gap: 22rpx; }
.field { display: flex; flex-direction: column; gap: 10rpx; }
.field__label { font-size: 26rpx; color: #55645a; font-weight: 600; }
.req { color: #d24a4a; margin-left: 6rpx; }
.input {
  border: 1rpx solid #e3ebe0;
  border-radius: 18rpx;
  padding: 20rpx 24rpx;
  background: #fbfdfa;
  font-size: 28rpx;
  color: #1f2d24;
}
.textarea { height: 140rpx; }
</style>
