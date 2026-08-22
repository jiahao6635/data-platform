export function formatBytes(value: number, digits = 2): string {
  if (!Number.isFinite(value) || value === 0) return '0 B'
  const safeDigits = Math.max(0, Math.min(6, Math.trunc(digits)))
  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB']
  const index = Math.min(Math.floor(Math.log(Math.abs(value)) / Math.log(1000)), units.length - 1)
  const formatted = value / 1000 ** index
  return `${formatted.toLocaleString('zh-CN', {
    maximumFractionDigits: safeDigits,
    minimumFractionDigits: Math.min(index > 2 ? 1 : 0, safeDigits),
  })} ${units[index]}`
}

export function formatNumber(value: number): string {
  return value.toLocaleString('zh-CN')
}

export function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ')
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

export function shortName(value: string, length = 32): string {
  return value.length > length ? `${value.slice(0, length - 1)}…` : value
}
