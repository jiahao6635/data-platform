import type {
  AssetItem,
  AssetQuery,
  AuthStatus,
  BucketMetric,
  FilterOptions,
  ImportResult,
  OwnerMetric,
  Page,
  PartitionItem,
  SnapshotItem,
  Summary,
  TopTable,
  TrendPoint,
} from '@/types'

const API_ROOT = import.meta.env.VITE_API_ROOT ?? '/api/v1'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_ROOT}${path}`, { credentials: 'include', ...init })
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: `请求失败 (${response.status})` }))
    if (response.status === 401 && !path.startsWith('/auth/')) window.location.assign('/')
    throw new Error(error.message ?? `请求失败 (${response.status})`)
  }
  return response.json() as Promise<T>
}

function queryString(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value))
  })
  const value = search.toString()
  return value ? `?${value}` : ''
}

export const api = {
  authStatus: () => request<AuthStatus>('/auth/me'),
  loginUrl: () => `${API_ROOT}/auth/login`,
  logout: () => request<AuthStatus>('/auth/logout', { method: 'POST' }),
  summary: () => request<Summary>('/dashboard/summary'),
  buckets: () => request<BucketMetric[]>('/dashboard/buckets'),
  topTables: (limit = 10) => request<TopTable[]>(`/dashboard/top-tables?limit=${limit}`),
  owners: (limit = 8) => request<OwnerMetric[]>(`/dashboard/owners?limit=${limit}`),
  trend: (days = 30, bucket?: string) =>
    request<TrendPoint[]>(`/dashboard/trend${queryString({ days, bucket })}`),
  assets: (query: AssetQuery) =>
    request<Page<AssetItem>>(`/assets${queryString({ ...query })}`),
  filters: () => request<FilterOptions>('/assets/filters'),
  partitions: (bucket: string, database: string, table: string, page = 0, size = 100) =>
    request<Page<PartitionItem>>(
      `/partitions${queryString({ bucket, database, table, page, size })}`,
    ),
  snapshots: (limit = 20) => request<SnapshotItem[]>(`/snapshots?limit=${limit}`),
  importNdjson: (file: File) => {
    const body = new FormData()
    body.append('file', file)
    return request<ImportResult>('/imports/ndjson', { method: 'POST', body })
  },
}
