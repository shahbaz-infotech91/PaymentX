/**
 * ENGLISH: The "/files" page - a real, safe file browser scoped to
 * exactly the one explicitly-configured PaymentX generated-files
 * directory (the real reporting-service exports folder - see backend
 * ControlCenterProperties.Files), never arbitrary filesystem browsing.
 * What it does: lists real files with their real size/last-modified
 * time (GET /api/v1/files), with a real client-side search/sort over
 * that real list, and a real download link per file
 * (GET /api/v1/files/download/{filename}, safety-checked server-side
 * by SafeFileService). Why it exists: required Phase 4 "Files" module.
 * How it will communicate with the backend: via useFiles ->
 * filesService.ts -> GET /api/v1/files[/download/{filename}].
 *
 * HINGLISH: "/files" page - ek real, safe file browser, exactly us ek
 * explicitly-configured PaymentX generated-files directory tak scoped
 * (real reporting-service exports folder - backend
 * ControlCenterProperties.Files dekho), kabhi arbitrary filesystem
 * browsing nahi. Ye kya karti hai: real files ko unke real size/
 * last-modified time ke saath list karta hai (GET /api/v1/files), us
 * real list ke upar ek real client-side search/sort ke saath, aur har
 * file ke liye ek real download link (GET
 * /api/v1/files/download/{filename}, SafeFileService dwara
 * server-side safety-checked). Ye dashboard me kyu hai: required Phase
 * 4 "Files" module. Backend se kaise connect hogi: useFiles ->
 * filesService.ts -> GET /api/v1/files[/download/{filename}] ke
 * through.
 */
import { useMemo, useState } from 'react'
import { Button, MenuItem, TextField } from '@mui/material'
import DownloadIcon from '@mui/icons-material/Download'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { FilterBar } from '../components/FilterBar'
import { SearchBar } from '../components/SearchBar'
import { useFiles } from '../hooks/useFiles'
import { toApiError } from '../api/axiosClient'
import { formatTimestamp, formatBytes } from '../utils/formatters'
import { downloadFileUrl, type FileEntry } from '../services/filesService'

type SortField = 'name' | 'sizeBytes' | 'lastModified'

const columns = (sortField: SortField, direction: 'asc' | 'desc'): DataTableColumn<FileEntry>[] => {
  const base: DataTableColumn<FileEntry>[] = [
    { key: 'name', label: 'Name', render: (row) => row.name },
    { key: 'sizeBytes', label: 'Size', align: 'right', render: (row) => formatBytes(row.sizeBytes) },
    { key: 'lastModified', label: 'Last Modified', render: (row) => formatTimestamp(row.lastModified) },
    {
      key: 'download',
      label: 'Download',
      render: (row) => (
        <Button size="small" startIcon={<DownloadIcon />} href={downloadFileUrl(row.name)} target="_blank" rel="noreferrer">
          Download
        </Button>
      ),
    },
  ]
  return base.map((column) => (column.key === sortField ? { ...column, label: `${column.label} ${direction === 'asc' ? '↑' : '↓'}` } : column))
}

export default function FilesPage() {
  const { data, isLoading, isError, error, refetch } = useFiles()
  const [search, setSearch] = useState('')
  const [sortField, setSortField] = useState<SortField>('lastModified')
  const [direction, setDirection] = useState<'asc' | 'desc'>('desc')

  const rows = useMemo(() => {
    const filtered = (data ?? []).filter((file) => file.name.toLowerCase().includes(search.toLowerCase()))
    const sorted = [...filtered].sort((a, b) => {
      const cmp = sortField === 'name' ? a.name.localeCompare(b.name)
        : sortField === 'sizeBytes' ? a.sizeBytes - b.sizeBytes
        : new Date(a.lastModified).getTime() - new Date(b.lastModified).getTime()
      return direction === 'asc' ? cmp : -cmp
    })
    return sorted
  }, [data, search, sortField, direction])

  return (
    <PageContainer>
      <PageHeader
        title="Files"
        description="Real files in PaymentX's explicitly-configured generated-files directory (reporting-service exports) - never arbitrary filesystem browsing."
      />
      <FilterBar>
        <SearchBar value={search} onChange={setSearch} placeholder="Search file name…" />
        <TextField select size="small" label="Sort by" value={sortField} onChange={(e) => setSortField(e.target.value as SortField)} sx={{ minWidth: 160 }}>
          <MenuItem value="lastModified">Last Modified</MenuItem>
          <MenuItem value="name">Name</MenuItem>
          <MenuItem value="sizeBytes">Size</MenuItem>
        </TextField>
        <TextField select size="small" label="Direction" value={direction} onChange={(e) => setDirection(e.target.value as 'asc' | 'desc')} sx={{ minWidth: 120 }}>
          <MenuItem value="desc">Descending</MenuItem>
          <MenuItem value="asc">Ascending</MenuItem>
        </TextField>
      </FilterBar>
      {isLoading && <LoadingState message="Loading files…" />}
      {isError && <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />}
      {data && (
        <DataTable
          rows={rows}
          columns={columns(sortField, direction)}
          getRowKey={(row) => row.name}
          emptyTitle="No files found"
          emptyMessage="The configured directory has no files yet, or none match this search."
        />
      )}
    </PageContainer>
  )
}
