/**
 * ENGLISH: The "/reporting" page - real report execution history from
 * paymentx_reporting.report_execution, via the backend's read-only
 * Postgres layer. What it does NOT do: trigger a new report
 * generation or download a report file - Phase 2's scope is read-only
 * Postgres monitoring, not proxying Reporting Service's generation
 * API, so that stays honestly out of scope here rather than faked.
 * Why it exists: required route "/reporting", now wired to real
 * execution-history data. How it will communicate with the backend:
 * via useReportExecutions -> postgresService.ts -> GET
 * /api/v1/postgres/report-executions.
 *
 * HINGLISH: "/reporting" page - real report execution history,
 * paymentx_reporting.report_execution se, backend ke read-only
 * Postgres layer ke through. Ye kya NAHI karti: ek nayi report
 * generation trigger karna ya ek report file download karna - Phase
 * 2 ka scope read-only Postgres monitoring hai, Reporting Service ke
 * generation API ko proxy karna nahi, isliye ye honestly yahan
 * out-of-scope raha, fake nahi kiya gaya. Ye dashboard me kyu hai:
 * required route "/reporting", ab real execution-history data se
 * wired hai. Backend se kaise connect hogi: useReportExecutions ->
 * postgresService.ts -> GET /api/v1/postgres/report-executions ke
 * through.
 */
import { useState } from 'react'
import { Button, Chip, Tab, Tabs } from '@mui/material'
import DownloadIcon from '@mui/icons-material/Download'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { Pager } from '../components/Pager'
import { useReportExecutions, useReportFiles } from '../hooks/usePostgresData'
import { toApiError } from '../api/axiosClient'
import { formatTimestamp, formatBytes } from '../utils/formatters'
import { downloadFileUrl } from '../services/filesService'
import type { ReportExecutionSummary, ReportFileSummary } from '../services/postgresService'

const PAGE_SIZE = 20

const executionColumns: DataTableColumn<ReportExecutionSummary>[] = [
  { key: 'requestId', label: 'Report Request', render: (row) => row.reportRequestId },
  { key: 'status', label: 'Status', render: (row) => <Chip size="small" label={row.status} /> },
  { key: 'rows', label: 'Rows', align: 'right', render: (row) => row.rowCount ?? '—' },
  { key: 'duration', label: 'Duration (ms)', align: 'right', render: (row) => row.generationTimeMillis ?? '—' },
  { key: 'startedAt', label: 'Started', render: (row) => formatTimestamp(row.startedAt) },
  { key: 'completedAt', label: 'Completed', render: (row) => formatTimestamp(row.completedAt) },
]

// ENGLISH: fileName is the exact bare filename GET /api/v1/files/download/{fileName} expects - reporting-
// service writes real export files into the same directory SafeFileService is configured to serve from.
// HINGLISH: fileName wahi exact bare filename hai jo GET /api/v1/files/download/{fileName} expect karta
// hai - reporting-service real export files usi directory me likhta hai jahan se SafeFileService serve karta hai.
const fileColumns: DataTableColumn<ReportFileSummary>[] = [
  { key: 'displayName', label: 'Name', render: (row) => row.displayName ?? row.reportType },
  { key: 'format', label: 'Type', render: (row) => <Chip size="small" label={row.format} /> },
  { key: 'createdAt', label: 'Created', render: (row) => formatTimestamp(row.createdAt) },
  { key: 'executionStatus', label: 'Status', render: (row) => <Chip size="small" label={row.executionStatus} /> },
  { key: 'fileSizeBytes', label: 'Size', align: 'right', render: (row) => formatBytes(row.fileSizeBytes) },
  {
    key: 'download',
    label: 'Download',
    render: (row) => (
      <Button size="small" startIcon={<DownloadIcon />} href={downloadFileUrl(row.fileName)} target="_blank" rel="noreferrer">
        Download
      </Button>
    ),
  },
]

function ExecutionsTab() {
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, error, refetch } = useReportExecutions(page, PAGE_SIZE)
  if (isLoading) return <LoadingState message="Loading report executions…" />
  if (isError) return <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />
  if (!data) return null
  return (
    <>
      <DataTable
        rows={data.content}
        columns={executionColumns}
        getRowKey={(row) => row.id}
        emptyTitle="No report executions yet"
        emptyMessage="No records exist in paymentx_reporting.report_execution yet."
      />
      <Pager page={data.page} size={data.size} totalElements={data.totalElements} onPageChange={setPage} />
    </>
  )
}

function FilesTab() {
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, error, refetch } = useReportFiles(page, PAGE_SIZE)
  if (isLoading) return <LoadingState message="Loading generated report files…" />
  if (isError) return <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />
  if (!data) return null
  return (
    <>
      <DataTable
        rows={data.content}
        columns={fileColumns}
        getRowKey={(row) => row.exportId}
        emptyTitle="No generated report files yet"
        emptyMessage="No real export files exist in the configured reports directory yet."
      />
      <Pager page={data.page} size={data.size} totalElements={data.totalElements} onPageChange={setPage} />
    </>
  )
}

export default function ReportingPage() {
  const [tab, setTab] = useState(0)
  return (
    <PageContainer>
      <PageHeader
        title="Reporting"
        description="Real report execution history and real, downloadable generated report files (CSV/PDF/XLSX/JSON)."
      />
      <Tabs value={tab} onChange={(_, value) => setTab(value)}>
        <Tab label="Executions" />
        <Tab label="Generated Files" />
      </Tabs>
      {tab === 0 ? <ExecutionsTab /> : <FilesTab />}
    </PageContainer>
  )
}
