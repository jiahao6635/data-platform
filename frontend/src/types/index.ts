export interface Summary {
  batchId: string | null
  snapshotAt: string | null
  totalSizeBytes: number
  bucketCount: number
  databaseCount: number
  tableCount: number
  partitionCount: number
  partitionedTableCount: number
  ownerCount: number
  zeroSizeTableCount: number
  latestModifiedAt: string | null
  rawRecordCount: number
}

export interface AuthUser {
  openId: string
  unionId: string
  name: string
  avatarUrl: string
  tenantKey: string
}

export interface AuthStatus {
  authEnabled: boolean
  configured: boolean
  authenticated: boolean
  user: AuthUser | null
}

export interface BucketMetric {
  bucket: string
  sizeBytes: number
  tableCount: number
  zeroSizeTableCount: number
}

export interface TopTable {
  bucket: string
  database: string
  table: string
  sizeBytes: number
  partitionCount: number
  modTime: string
  owner: string
}

export interface OwnerMetric {
  owner: string
  sizeBytes: number
  tableCount: number
}

export interface TrendPoint {
  date: string
  totalSizeBytes: number
  growthBytes: number
  tableCount: number
}

export interface AssetItem {
  tableKey: string
  bucket: string
  database: string
  table: string
  sizeBytes: number
  partitionCount: number
  modTime: string
  accessTime: string | null
  owner: string
  scanType: string
  collectHost: string
  collectTime: string
}

export interface PartitionItem {
  id: number
  partition: string
  sizeBytes: number
  modTime: string
  accessTime: string | null
  owner: string
  collectHost: string
  collectTime: string
}

export interface Page<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface FilterOptions {
  buckets: string[]
  databases: string[]
  scanTypes: string[]
  owners: string[]
}

export interface SnapshotItem {
  id: string
  source: string
  sourceName: string
  status: string
  snapshotAt: string | null
  publishedAt: string | null
  recordCount: number
  tableCount: number
  partitionCount: number
  totalTableSizeBytes: number
  errorCount: number
}

export interface ImportResult {
  batchId: string
  status: string
  recordCount: number
  tableCount: number
  partitionCount: number
  totalTableSizeBytes: number
  snapshotAt: string
  duplicate: boolean
}

export interface AssetQuery {
  bucket?: string
  database?: string
  scanType?: string
  owner?: string
  keyword?: string
  page: number
  size: number
  sort?: string
  direction?: string
}
