<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import EChart from '@/components/EChart.vue'
import MetricCard from '@/components/MetricCard.vue'
import { api } from '@/api/client'
import type {
  AssetItem,
  AssetQuery,
  AuthStatus,
  BucketMetric,
  FilterOptions,
  OwnerMetric,
  PartitionItem,
  SnapshotItem,
  Summary,
  TopTable,
  TrendPoint,
} from '@/types'
import { formatBytes, formatDateTime, formatNumber, shortName } from '@/utils'

type ViewName = 'overview' | 'assets' | 'snapshots'

const emptySummary: Summary = {
  batchId: null,
  snapshotAt: null,
  totalSizeBytes: 0,
  bucketCount: 0,
  databaseCount: 0,
  tableCount: 0,
  partitionCount: 0,
  partitionedTableCount: 0,
  ownerCount: 0,
  zeroSizeTableCount: 0,
  latestModifiedAt: null,
  rawRecordCount: 0,
}

const activeView = ref<ViewName>('overview')
const authLoading = ref(true)
const authCheckError = ref('')
const authError = ref('')
const loginRedirecting = ref(false)
const authStatus = ref<AuthStatus>({
  authEnabled: false,
  configured: false,
  authenticated: false,
  user: null,
})
const loading = ref(true)
const apiError = ref('')
const summary = ref<Summary>(emptySummary)
const buckets = ref<BucketMetric[]>([])
const topTables = ref<TopTable[]>([])
const owners = ref<OwnerMetric[]>([])
const trend = ref<TrendPoint[]>([])
const snapshots = ref<SnapshotItem[]>([])
const filters = ref<FilterOptions>({ buckets: [], databases: [], scanTypes: [], owners: [] })
const assets = ref<AssetItem[]>([])
const assetTotal = ref(0)
const assetLoading = ref(false)
const uploadLoading = ref(false)
const fileInput = ref<HTMLInputElement>()
const partitionDrawer = ref(false)
const selectedAsset = ref<AssetItem | null>(null)
const partitions = ref<PartitionItem[]>([])
const partitionTotal = ref(0)
const partitionPage = ref(0)
const partitionLoading = ref(false)

const trendDrawer = ref(false)
const trendAsset = ref<AssetItem | null>(null)
const tableTrend = ref<TrendPoint[]>([])
const tableTrendLoading = ref(false)

const assetQuery = reactive<AssetQuery>({
  page: 0,
  size: 20,
  scanType: 'table',
  sort: 'sizeBytes',
  direction: 'desc',
})

const navItems: Array<{ key: ViewName; label: string; caption: string; icon: string }> = [
  { key: 'overview', label: '资产总览', caption: 'Overview', icon: '◫' },
  { key: 'assets', label: '数据资产', caption: 'Inventory', icon: '▤' },
  { key: 'snapshots', label: '采集快照', caption: 'Snapshots', icon: '◷' },
]

const currentViewLabel = computed(
  () => navItems.find((item) => item.key === activeView.value)?.label ?? '资产总览',
)

const tableSizeRatio = computed(() => {
  const max = owners.value[0]?.sizeBytes ?? 1
  return (value: number) => `${Math.max(2, (value / max) * 100)}%`
})

const trendOption = computed<EChartsOption>(() => ({
  animationDuration: 650,
  color: ['#44d7d2', '#7f8cff'],
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(8, 19, 34, .96)',
    borderColor: '#203a52',
    textStyle: { color: '#d9e9f5' },
    formatter: (params: unknown) => {
      const rows = params as Array<{ axisValue: string; marker: string; seriesName: string; value: number }>
      if (!rows.length) return ''
      return [
        `<strong>${rows[0].axisValue}</strong>`,
        ...rows.map((row) => `${row.marker}${row.seriesName}　${formatBytes(row.value)}`),
      ].join('<br/>')
    },
  },
  legend: {
    right: 8,
    top: 0,
    itemWidth: 9,
    itemHeight: 9,
    textStyle: { color: '#829bb0' },
  },
  grid: { left: 8, right: 12, top: 48, bottom: 8, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trend.value.map((item) => item.date.slice(5)),
    axisLine: { lineStyle: { color: '#24374a' } },
    axisLabel: { color: '#70899e' },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    axisLabel: { color: '#70899e', formatter: (value: number) => formatBytes(value, 0) },
    splitLine: { lineStyle: { color: 'rgba(58, 83, 105, .24)', type: 'dashed' } },
  },
  series: [
    {
      name: '总容量',
      type: 'line',
      smooth: true,
      showSymbol: trend.value.length < 8,
      symbolSize: 8,
      lineStyle: { width: 3 },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(68, 215, 210, .30)' },
            { offset: 1, color: 'rgba(68, 215, 210, .01)' },
          ],
        },
      },
      data: trend.value.map((item) => item.totalSizeBytes),
    },
  ],
}))

const bucketOption = computed<EChartsOption>(() => ({
  animationDuration: 700,
  color: ['#44d7d2', '#5b8ff9', '#8b7cf6', '#f0b45e', '#e86f91', '#66bb8a'],
  tooltip: {
    trigger: 'item',
    backgroundColor: 'rgba(8, 19, 34, .96)',
    borderColor: '#203a52',
    textStyle: { color: '#d9e9f5' },
    formatter: (item: unknown) => {
      const row = item as { marker: string; name: string; value: number; percent: number }
      return `${row.marker}${row.name}<br/><strong>${formatBytes(row.value)}</strong> · ${row.percent}%`
    },
  },
  legend: {
    orient: 'vertical',
    right: 0,
    top: 'middle',
    itemWidth: 9,
    itemHeight: 9,
    textStyle: { color: '#8ca3b7', fontSize: 11 },
    formatter: (name: string) => shortName(name, 19),
  },
  series: [
    {
      type: 'pie',
      radius: ['55%', '77%'],
      center: ['35%', '53%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#0d1a29', borderWidth: 3, borderRadius: 4 },
      label: { show: false },
      emphasis: { scaleSize: 6 },
      data: buckets.value.map((item) => ({ name: item.bucket, value: item.sizeBytes })),
    },
  ],
  graphic: [
    {
      type: 'text',
      left: '25%',
      top: '45%',
      style: { text: `${buckets.value.length}`, fill: '#edf8ff', font: '600 28px Inter' },
    },
    {
      type: 'text',
      left: '23%',
      top: '58%',
      style: { text: 'BUCKETS', fill: '#668198', font: '10px Inter' },
    },
  ],
}))

const topTablesOption = computed<EChartsOption>(() => {
  const rows = [...topTables.value].reverse()
  return {
    animationDuration: 700,
    grid: { left: 8, right: 24, top: 12, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(8, 19, 34, .96)',
      borderColor: '#203a52',
      textStyle: { color: '#d9e9f5' },
      formatter: (params: unknown) => {
        const row = (params as Array<{ dataIndex: number; marker: string; value: number }>)[0]
        const table = rows[row.dataIndex]
        return `${row.marker}${table.database}.${table.table}<br/><strong>${formatBytes(row.value)}</strong>`
      },
    },
    xAxis: {
      type: 'value',
      axisLabel: { color: '#70899e', formatter: (value: number) => formatBytes(value, 0) },
      splitLine: { lineStyle: { color: 'rgba(58, 83, 105, .22)', type: 'dashed' } },
    },
    yAxis: {
      type: 'category',
      data: rows.map((item) => shortName(item.table, 24)),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#8ca3b7', fontSize: 11 },
    },
    series: [
      {
        type: 'bar',
        barWidth: 10,
        showBackground: true,
        backgroundStyle: { color: 'rgba(45, 68, 88, .22)', borderRadius: 5 },
        itemStyle: {
          borderRadius: 5,
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#3d8cf0' },
              { offset: 1, color: '#49d3cf' },
            ],
          },
        },
        data: rows.map((item) => item.sizeBytes),
      },
    ],
  }
})

const tableTrendOption = computed<EChartsOption>(() => ({
  animationDuration: 650,
  color: ['#44d7d2'],
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(8, 19, 34, .96)',
    borderColor: '#203a52',
    textStyle: { color: '#d9e9f5' },
    formatter: (params: unknown) => {
      const rows = params as Array<{ axisValue: string; marker: string; seriesName: string; value: number }>
      if (!rows.length) return ''
      return [
        `<strong>${rows[0].axisValue}</strong>`,
        ...rows.map((row) => `${row.marker}${row.seriesName}　${formatBytes(row.value)}`),
      ].join('<br/>')
    },
  },
  grid: { left: 8, right: 12, top: 36, bottom: 8, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: tableTrend.value.map((item) => item.date.slice(5)),
    axisLine: { lineStyle: { color: '#24374a' } },
    axisLabel: { color: '#70899e' },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    axisLabel: { color: '#70899e', formatter: (value: number) => formatBytes(value, 0) },
    splitLine: { lineStyle: { color: 'rgba(58, 83, 105, .24)', type: 'dashed' } },
  },
  series: [
    {
      name: '容量',
      type: 'line',
      smooth: true,
      showSymbol: tableTrend.value.length < 8,
      symbolSize: 8,
      lineStyle: { width: 3 },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(68, 215, 210, .30)' },
            { offset: 1, color: 'rgba(68, 215, 210, .01)' },
          ],
        },
      },
      data: tableTrend.value.map((item) => item.totalSizeBytes),
    },
  ],
}))

const growthDelta = computed(() => {
  if (tableTrend.value.length < 2) return 0
  return tableTrend.value[tableTrend.value.length - 1].totalSizeBytes - tableTrend.value[0].totalSizeBytes
})

async function refreshDashboard() {
  loading.value = true
  apiError.value = ''
  try {
    const [summaryData, bucketData, tableData, ownerData, trendData, snapshotData, filterData] =
      await Promise.all([
        api.summary(),
        api.buckets(),
        api.topTables(),
        api.owners(),
        api.trend(30),
        api.snapshots(),
        api.filters(),
      ])
    summary.value = summaryData
    buckets.value = bucketData
    topTables.value = tableData
    owners.value = ownerData
    trend.value = trendData
    snapshots.value = snapshotData
    filters.value = filterData
    await loadAssets()
  } catch (error) {
    apiError.value = error instanceof Error ? error.message : '无法连接数据服务'
  } finally {
    loading.value = false
  }
}

async function loadAssets() {
  assetLoading.value = true
  try {
    const result = await api.assets(assetQuery)
    assets.value = result.items
    assetTotal.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资产列表加载失败')
  } finally {
    assetLoading.value = false
  }
}

async function openPartitions(asset: AssetItem) {
  if (asset.partitionCount === 0) return
  selectedAsset.value = asset
  partitionPage.value = 0
  partitionDrawer.value = true
  await loadPartitions()
}

async function openTableTrend(asset: AssetItem) {
  trendAsset.value = asset
  trendDrawer.value = true
  await loadTableTrend()
}

async function loadTableTrend() {
  if (!trendAsset.value) return
  tableTrendLoading.value = true
  try {
    const asset = trendAsset.value
    tableTrend.value = await api.trend(90, asset.bucket, asset.database, asset.table)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '表趋势加载失败')
  } finally {
    tableTrendLoading.value = false
  }
}

async function loadPartitions() {
  if (!selectedAsset.value) return
  partitionLoading.value = true
  try {
    const asset = selectedAsset.value
    const result = await api.partitions(
      asset.bucket,
      asset.database,
      asset.table,
      partitionPage.value,
      100,
    )
    partitions.value = result.items
    partitionTotal.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '分区明细加载失败')
  } finally {
    partitionLoading.value = false
  }
}

function changeView(view: ViewName) {
  activeView.value = view
  if (view === 'assets') loadAssets()
}

function resetFilters() {
  Object.assign(assetQuery, {
    bucket: undefined,
    database: undefined,
    scanType: 'table',
    owner: undefined,
    keyword: undefined,
    page: 0,
    size: 20,
    sort: 'sizeBytes',
    direction: 'desc',
  })
  loadAssets()
}

function handleSort({ prop, order }: { prop: string; order: string | null }) {
  assetQuery.sort = prop || 'sizeBytes'
  assetQuery.direction = order === 'ascending' ? 'asc' : 'desc'
  assetQuery.page = 0
  loadAssets()
}

function openUpload() {
  fileInput.value?.click()
}

function readLoginError() {
  const url = new URL(window.location.href)
  const code = url.searchParams.get('login_error')
  if (!code) return
  const messages: Record<string, string> = {
    access_denied: '你取消了飞书授权，请重新登录。',
    invalid_state: '登录请求已经失效，请重新发起飞书登录。',
    authentication_failed: '飞书身份验证失败，请稍后重试或联系管理员。',
  }
  authError.value = messages[code] ?? '飞书登录未完成，请重新尝试。'
  url.searchParams.delete('login_error')
  window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
}

async function initialize() {
  authLoading.value = true
  authCheckError.value = ''
  readLoginError()
  try {
    authStatus.value = await api.authStatus()
    if (!authStatus.value.authEnabled || authStatus.value.authenticated) {
      await refreshDashboard()
    }
  } catch (error) {
    authCheckError.value = error instanceof Error ? error.message : '无法检查登录状态'
  } finally {
    authLoading.value = false
  }
}

function startFeishuLogin() {
  if (!authStatus.value.configured || loginRedirecting.value) return
  loginRedirecting.value = true
  window.location.assign(api.loginUrl())
}

async function logout() {
  try {
    authStatus.value = await api.logout()
    summary.value = emptySummary
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退出登录失败')
  }
}

async function handleFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  uploadLoading.value = true
  try {
    const result = await api.importNdjson(file)
    ElMessage.success(
      result.duplicate
        ? `该快照已经导入，共 ${result.recordCount} 条记录`
        : `导入成功：${result.recordCount} 条记录，${result.tableCount} 张表，${result.partitionCount} 个分区`,
    )
    await refreshDashboard()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文件导入失败')
  } finally {
    uploadLoading.value = false
    input.value = ''
  }
}

onMounted(initialize)
</script>

<template>
  <div v-if="authLoading" class="auth-page auth-loading-page">
    <div class="auth-loader"><span /><span /><span /></div>
    <p>正在检查登录状态…</p>
  </div>

  <div v-else-if="authCheckError" class="auth-page">
    <div class="auth-card">
      <div class="auth-brand-mark"><span /><span /><span /></div>
      <span class="auth-kicker">DATA ASSET PLATFORM</span>
      <h1>暂时无法连接数据平台</h1>
      <p>{{ authCheckError }}</p>
      <button class="primary-button auth-button" @click="initialize">重新连接</button>
    </div>
  </div>

  <div v-else-if="authStatus.authEnabled && !authStatus.authenticated" class="auth-page">
    <div class="auth-grid" />
    <div class="auth-card">
      <div class="auth-brand-mark"><span /><span /><span /></div>
      <span class="auth-kicker">SIGMOB · DATA OS</span>
      <h1>登录 DataScope</h1>
      <p>使用公司飞书账号验证身份后，进入数据资产看板。</p>
      <div v-if="authError" class="auth-error">
        <div class="auth-error-icon">⚠</div>
        <div class="auth-error-content">
          <strong>飞书身份验证失败</strong>
          <span>{{ authError }}</span>
        </div>
      </div>
      <div v-if="!authStatus.configured" class="auth-error">
        <div class="auth-error-icon">⚠</div>
        <div class="auth-error-content">
          <strong>飞书身份验证失败</strong>
          <span>飞书应用参数尚未配置，请联系管理员设置 App ID、App Secret 和回调地址。</span>
        </div>
      </div>
      <button class="feishu-login-button" :disabled="!authStatus.configured || loginRedirecting" @click="startFeishuLogin">
        <span class="feishu-logo">
          <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M8 24L16 8L24 24H8Z" fill="currentColor" opacity="0.9"/>
            <path d="M12 20L16 12L20 20H12Z" fill="currentColor"/>
          </svg>
        </span>
        使用飞书登录
        <i>→</i>
      </button>
      <small>仅用于身份验证，不会读取聊天记录或文档内容</small>
    </div>
    <div class="auth-footer">DataScope v0.1.0 · Internal Metadata Platform</div>
  </div>

  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark"><span /><span /><span /></div>
        <div>
          <strong>DataScope</strong>
          <small>SIGMOB · DATA OS</small>
        </div>
      </div>

      <div class="nav-label">工作台</div>
      <nav>
        <button
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: activeView === item.key }"
          @click="changeView(item.key)"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span><b>{{ item.label }}</b><small>{{ item.caption }}</small></span>
          <i />
        </button>
      </nav>

      <div class="sidebar-spacer" />
      <div class="collector-card">
        <div class="collector-head"><span class="live-dot" />采集服务</div>
        <strong>{{ summary.batchId ? '数据已就绪' : '等待首批数据' }}</strong>
        <p>Kafka Topic · oss_data</p>
        <div class="collector-line"><span :style="{ width: summary.batchId ? '100%' : '18%' }" /></div>
      </div>
      <div class="sidebar-foot">v0.1.0 · Metadata MVP</div>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <div>
          <span class="eyebrow">DATA ASSET PLATFORM</span>
          <h1>{{ currentViewLabel }}</h1>
        </div>
        <div class="topbar-actions">
          <div class="snapshot-clock">
            <span class="live-dot" />
            <div><small>最新数据快照</small><b>{{ formatDateTime(summary.snapshotAt) }}</b></div>
          </div>
          <button class="ghost-button" :disabled="loading" @click="refreshDashboard">↻ 刷新</button>
          <button class="primary-button" :disabled="uploadLoading" @click="openUpload">
            {{ uploadLoading ? '导入中…' : '＋ 导入 NDJSON' }}
          </button>
          <div v-if="authStatus.user" class="user-chip">
            <img v-if="authStatus.user.avatarUrl" :src="authStatus.user.avatarUrl" alt="" />
            <span v-else>{{ authStatus.user.name.slice(0, 1) }}</span>
            <div><b>{{ authStatus.user.name }}</b><small>飞书账号</small></div>
            <button title="退出登录" @click="logout">退出</button>
          </div>
          <div v-else class="local-mode">LOCAL</div>
          <input ref="fileInput" hidden type="file" accept=".ndjson,.json,application/x-ndjson" @change="handleFile" />
        </div>
      </header>

      <div v-if="apiError" class="error-banner">
        <strong>数据服务暂不可用</strong><span>{{ apiError }}</span>
        <button @click="refreshDashboard">重新连接</button>
      </div>

      <section v-if="activeView === 'overview'" v-loading="loading" class="content-stack">
        <div class="hero-strip">
          <div>
            <span class="section-kicker">OSS METADATA PULSE</span>
            <h2>公司数据资产，一屏掌握</h2>
            <p>统计口径为 <b>scan_type = table</b>，临时目录、回收站和用户目录单独保留，避免重复计算。</p>
          </div>
          <div class="hero-stat">
            <span>RAW RECORDS</span>
            <strong>{{ formatNumber(summary.rawRecordCount) }}</strong>
            <small>当前快照原始记录</small>
          </div>
        </div>

        <div class="metrics-grid">
          <MetricCard label="表存储总量" :value="formatBytes(summary.totalSizeBytes)" caption="仅统计表级资产" tone="cyan" icon="∑" />
          <MetricCard label="数据表" :value="formatNumber(summary.tableCount)" :caption="`${summary.databaseCount} 个数据库`" tone="blue" icon="▦" />
          <MetricCard label="数据分区" :value="formatNumber(summary.partitionCount)" :caption="`${summary.partitionedTableCount} 张分区表`" tone="violet" icon="⑂" />
          <MetricCard label="Buckets" :value="formatNumber(summary.bucketCount)" caption="当前已采集范围" tone="green" icon="◉" />
          <MetricCard label="资产所有者" :value="formatNumber(summary.ownerCount)" caption="按 owner 去重" tone="amber" icon="♙" />
          <MetricCard label="零容量表" :value="formatNumber(summary.zeroSizeTableCount)" caption="建议关注与核验" tone="rose" icon="○" />
        </div>

        <div class="dashboard-grid trend-row">
          <article class="panel trend-panel">
            <div class="panel-head">
              <div><span>STORAGE TREND</span><h3>整体容量趋势</h3></div>
              <div class="range-pill">近 30 天</div>
            </div>
            <div v-if="trend.length < 2" class="single-snapshot-note">
              <span>●</span> 当前只有 {{ trend.length }} 个日快照；持续采集后将自动形成增长曲线
            </div>
            <EChart :option="trendOption" height="310px" />
          </article>

          <article class="panel bucket-panel">
            <div class="panel-head">
              <div><span>BUCKET SHARE</span><h3>存储分布</h3></div>
            </div>
            <EChart :option="bucketOption" height="334px" />
          </article>
        </div>

        <div class="dashboard-grid ranking-row">
          <article class="panel ranking-panel">
            <div class="panel-head">
              <div><span>LARGEST ASSETS</span><h3>容量最大的 10 张表</h3></div>
              <button class="text-button" @click="changeView('assets')">查看全部 →</button>
            </div>
            <EChart :option="topTablesOption" height="356px" />
          </article>

          <article class="panel owner-panel">
            <div class="panel-head">
              <div><span>OWNERSHIP</span><h3>所有者容量排行</h3></div>
            </div>
            <div class="owner-list">
              <div v-for="(item, index) in owners" :key="item.owner" class="owner-row">
                <span class="rank">{{ String(index + 1).padStart(2, '0') }}</span>
                <div class="owner-data">
                  <div><b>{{ item.owner || '未知' }}</b><small>{{ item.tableCount }} 张表 · {{ formatBytes(item.sizeBytes) }}</small></div>
                  <div class="owner-track"><span :style="{ width: tableSizeRatio(item.sizeBytes) }" /></div>
                </div>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section v-else-if="activeView === 'assets'" class="content-stack">
        <article class="panel asset-panel">
          <div class="panel-head asset-head">
            <div><span>ASSET INVENTORY</span><h3>数据资产清单</h3><p>共 {{ formatNumber(assetTotal) }} 条匹配记录</p></div>
          </div>

          <div class="filter-bar">
            <el-input v-model="assetQuery.keyword" clearable placeholder="搜索 bucket / 数据库 / 表名" @keyup.enter="assetQuery.page = 0; loadAssets()">
              <template #prefix>⌕</template>
            </el-input>
            <el-select v-model="assetQuery.bucket" clearable placeholder="全部 Bucket" @change="assetQuery.page = 0; loadAssets()">
              <el-option v-for="item in filters.buckets" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="assetQuery.database" clearable filterable placeholder="全部数据库" @change="assetQuery.page = 0; loadAssets()">
              <el-option v-for="item in filters.databases" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="assetQuery.owner" clearable filterable placeholder="全部所有者" @change="assetQuery.page = 0; loadAssets()">
              <el-option v-for="item in filters.owners" :key="item" :label="item" :value="item" />
            </el-select>
            <button class="primary-button compact" @click="assetQuery.page = 0; loadAssets()">查询</button>
            <button class="ghost-button compact" @click="resetFilters">重置</button>
          </div>

          <el-table
            v-loading="assetLoading"
            :data="assets"
            class="asset-table"
            height="calc(100vh - 310px)"
            row-key="tableKey"
            @sort-change="handleSort"
          >
            <el-table-column prop="bucket" label="Bucket" min-width="158" show-overflow-tooltip />
            <el-table-column prop="database" label="数据库" min-width="130" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.database || '—' }}</template>
            </el-table-column>
            <el-table-column prop="table" label="表 / 目录" min-width="260" show-overflow-tooltip>
              <template #default="scope"><span class="table-name">{{ scope.row.table || `${scope.row.scanType} 目录` }}</span></template>
            </el-table-column>
            <el-table-column prop="partitionCount" label="分区" width="128" sortable="custom">
              <template #default="scope">
                <button v-if="scope.row.partitionCount > 0" class="partition-link" @click="openPartitions(scope.row)">
                  {{ formatNumber(scope.row.partitionCount) }} 个分区 →
                </button>
                <span v-else class="unpartitioned">非分区表</span>
              </template>
            </el-table-column>
            <el-table-column prop="sizeBytes" label="当前容量" width="145" sortable="custom" align="right">
              <template #default="scope"><b class="size-value">{{ formatBytes(scope.row.sizeBytes) }}</b></template>
            </el-table-column>
            <el-table-column prop="owner" label="所有者" min-width="120" sortable="custom" />
            <el-table-column prop="modTime" label="最后修改" width="172" sortable="custom">
              <template #default="scope">{{ formatDateTime(scope.row.modTime) }}</template>
            </el-table-column>
            <el-table-column prop="accessTime" label="最后访问" width="172">
              <template #default="scope"><span :class="{ muted: !scope.row.accessTime }">{{ formatDateTime(scope.row.accessTime) }}</span></template>
            </el-table-column>
            <el-table-column prop="collectHost" label="采集 Host" min-width="210" show-overflow-tooltip />
            <el-table-column label="操作" width="72" fixed="right">
              <template #default="scope">
                <button class="trend-link" @click="openTableTrend(scope.row)">趋势 ▸</button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-row">
            <span>每页 {{ assetQuery.size }} 条</span>
            <el-pagination
              :current-page="assetQuery.page + 1"
              :page-size="assetQuery.size"
              :total="assetTotal"
              layout="prev, pager, next"
              background
              @current-change="(page: number) => { assetQuery.page = page - 1; loadAssets() }"
            />
          </div>
        </article>
      </section>

      <section v-else class="content-stack">
        <article class="panel snapshot-panel">
          <div class="panel-head asset-head">
            <div><span>SNAPSHOT HISTORY</span><h3>采集批次</h3><p>只有发布成功的快照会进入正式看板</p></div>
          </div>
          <el-table :data="snapshots" class="asset-table" row-key="id">
            <el-table-column prop="snapshotAt" label="快照时间" min-width="185">
              <template #default="scope"><b>{{ formatDateTime(scope.row.snapshotAt) }}</b></template>
            </el-table-column>
            <el-table-column prop="source" label="来源" width="100">
              <template #default="scope"><span class="source-badge">{{ scope.row.source }}</span></template>
            </el-table-column>
            <el-table-column prop="sourceName" label="来源名称" min-width="230" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="120">
              <template #default="scope"><span class="status-badge" :class="scope.row.status.toLowerCase()">● {{ scope.row.status }}</span></template>
            </el-table-column>
            <el-table-column prop="recordCount" label="原始记录" width="120" align="right">
              <template #default="scope">{{ formatNumber(scope.row.recordCount) }}</template>
            </el-table-column>
            <el-table-column prop="tableCount" label="表数量" width="110" align="right">
              <template #default="scope">{{ formatNumber(scope.row.tableCount) }}</template>
            </el-table-column>
            <el-table-column prop="partitionCount" label="分区数量" width="110" align="right">
              <template #default="scope">{{ formatNumber(scope.row.partitionCount) }}</template>
            </el-table-column>
            <el-table-column prop="totalTableSizeBytes" label="表容量" width="150" align="right">
              <template #default="scope"><b class="size-value">{{ formatBytes(scope.row.totalTableSizeBytes) }}</b></template>
            </el-table-column>
            <el-table-column prop="publishedAt" label="发布时间" width="185">
              <template #default="scope">{{ formatDateTime(scope.row.publishedAt) }}</template>
            </el-table-column>
          </el-table>
          <div v-if="!snapshots.length && !loading" class="empty-state">
            <strong>还没有数据快照</strong><p>点击右上角“导入 NDJSON”导入第一份全量数据。</p>
          </div>
        </article>
      </section>
    </main>

    <el-drawer
      v-model="partitionDrawer"
      size="720px"
      direction="rtl"
      class="partition-drawer"
      destroy-on-close
    >
      <template #header>
        <div class="drawer-title">
          <span>PARTITION DETAIL</span>
          <h3>{{ selectedAsset?.database }}.{{ selectedAsset?.table }}</h3>
          <p>{{ selectedAsset?.bucket }} · {{ formatNumber(partitionTotal) }} 个分区 · {{ formatBytes(selectedAsset?.sizeBytes ?? 0) }}</p>
        </div>
      </template>
      <el-table v-loading="partitionLoading" :data="partitions" class="asset-table" height="calc(100vh - 190px)" row-key="id">
        <el-table-column prop="partition" label="分区" min-width="170">
          <template #default="scope"><span class="partition-name">{{ scope.row.partition }}</span></template>
        </el-table-column>
        <el-table-column prop="sizeBytes" label="容量" width="130" align="right">
          <template #default="scope"><b class="size-value">{{ formatBytes(scope.row.sizeBytes) }}</b></template>
        </el-table-column>
        <el-table-column prop="modTime" label="最后修改" width="170">
          <template #default="scope">{{ formatDateTime(scope.row.modTime) }}</template>
        </el-table-column>
        <el-table-column prop="owner" label="所有者" width="105" />
        <el-table-column prop="accessTime" label="最后访问" width="160">
          <template #default="scope"><span :class="{ muted: !scope.row.accessTime }">{{ formatDateTime(scope.row.accessTime) }}</span></template>
        </el-table-column>
      </el-table>
      <div class="pagination-row drawer-pagination">
        <span>每页 100 条</span>
        <el-pagination
          :current-page="partitionPage + 1"
          :page-size="100"
          :total="partitionTotal"
          layout="prev, pager, next"
          background
          @current-change="(page: number) => { partitionPage = page - 1; loadPartitions() }"
        />
      </div>
    </el-drawer>

    <el-drawer
      v-model="trendDrawer"
      size="680px"
      direction="rtl"
      class="trend-drawer"
      destroy-on-close
    >
      <template #header>
        <div class="drawer-title">
          <span>TABLE TREND</span>
          <h3>{{ trendAsset?.database }}.{{ trendAsset?.table }}</h3>
          <p>{{ trendAsset?.bucket }} · {{ formatBytes(trendAsset?.sizeBytes ?? 0) }} · 近 90 天</p>
        </div>
      </template>
      <div v-loading="tableTrendLoading" class="trend-drawer-body">
        <div v-if="tableTrend.length < 2 && !tableTrendLoading" class="single-snapshot-note" style="margin: 0 0 16px;">
          <span>●</span> 当前只有 {{ tableTrend.length }} 个日快照；持续采集后将自动形成增长曲线
        </div>
        <div class="trend-summary" v-if="tableTrend.length >= 2">
          <div class="trend-summary-item">
            <span>起始容量</span>
            <strong>{{ formatBytes(tableTrend[0].totalSizeBytes) }}</strong>
          </div>
          <div class="trend-summary-item">
            <span>最新容量</span>
            <strong>{{ formatBytes(tableTrend[tableTrend.length - 1].totalSizeBytes) }}</strong>
          </div>
          <div class="trend-summary-item">
            <span>增长量</span>
            <strong :class="{ negative: growthDelta < 0 }">{{ growthDelta >= 0 ? '+' : '' }}{{ formatBytes(growthDelta) }}</strong>
          </div>
        </div>
        <EChart :option="tableTrendOption" height="calc(100vh - 260px)" />
      </div>
    </el-drawer>
  </div>
</template>
