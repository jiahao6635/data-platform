<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    value: string
    caption: string
    tone?: 'cyan' | 'blue' | 'violet' | 'amber' | 'green' | 'rose'
    icon: string
  }>(),
  { tone: 'blue' },
)
</script>

<template>
  <article class="metric-card" :class="`tone-${tone}`">
    <div class="metric-icon">{{ icon }}</div>
    <div class="metric-copy">
      <p>{{ label }}</p>
      <strong>{{ value }}</strong>
      <span>{{ caption }}</span>
    </div>
    <i class="metric-glow" />
  </article>
</template>

