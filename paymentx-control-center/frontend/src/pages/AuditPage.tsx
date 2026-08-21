/**
 * ENGLISH: The "/audit" page - real, paginated audit_event records
 * from paymentx_audit.audit_event, via the backend's read-only
 * Postgres layer. payload (jsonb) is deliberately not shown here -
 * the backend's summary DTO already excludes it. Why it exists:
 * required route "/audit", now wired to the real Phase 2 backend.
 * How it will communicate with the backend: via useAuditEvents ->
 * postgresService.ts -> GET /api/v1/postgres/audit-events.
 *
 * HINGLISH: "/audit" page - real, paginated audit_event records,
 * paymentx_audit.audit_event se, backend ke read-only Postgres layer
 * ke through. payload (jsonb) yahan jaan-boojh kar nahi dikhaya gaya -
 * backend ka summary DTO use pehle se exclude karta hai. Ye dashboard
 * me kyu hai: required route "/audit", ab real Phase 2 backend se
 * wired hai. Backend se kaise connect hogi: useAuditEvents ->
 * postgresService.ts -> GET /api/v1/postgres/audit-events ke through.
 */
import { useState } from 'react'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { Pager } from '../components/Pager'
import { FilterBar } from '../components/FilterBar'
import { SearchBar } from '../components/SearchBar'
import { useAuditEvents } from '../hooks/usePostgresData'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { toApiError } from '../api/axiosClient'
import { formatTimestamp } from '../utils/formatters'
import type { AuditEventSummary } from '../services/postgresService'
import { Chip } from '@mui/material'

const PAGE_SIZE = 25

const columns: DataTableColumn<AuditEventSummary>[] = [
  { key: 'eventType', label: 'Event Type', render: (row) => row.eventType },
  { key: 'eventStatus', label: 'Status', render: (row) => <Chip size="small" label={row.eventStatus} /> },
  { key: 'sourceService', label: 'Entity', render: (row) => row.sourceService },
  { key: 'reference', label: 'Payment Reference', render: (row) => row.reference ?? '—' },
  { key: 'participantId', label: 'Participant', render: (row) => row.participantId ?? '—' },
  { key: 'correlationId', label: 'Correlation ID', render: (row) => row.correlationId ?? '—' },
  { key: 'traceId', label: 'Trace ID', render: (row) => row.traceId ?? '—' },
  { key: 'actor', label: 'Actor', render: (row) => (row.actorId ? `${row.actorId}${row.actorType ? ` (${row.actorType})` : ''}` : '—') },
  { key: 'occurredAt', label: 'Timestamp', render: (row) => formatTimestamp(row.occurredAt) },
]

export default function AuditPage() {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebouncedValue(search)
  const { data, isLoading, isError, error, refetch } = useAuditEvents(page, PAGE_SIZE, debouncedSearch)

  return (
    <PageContainer>
      <PageHeader title="Audit" description="Real audit event trail from paymentx_audit.audit_event." />
      <FilterBar>
        <SearchBar
          value={search}
          onChange={(value) => {
            setSearch(value)
            setPage(0)
          }}
          placeholder="Search event type, source, actor, correlation ID, reference…"
        />
      </FilterBar>
      {isLoading && <LoadingState message="Loading audit events…" />}
      {isError && <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />}
      {data && (
        <>
          <DataTable
            rows={data.content}
            columns={columns}
            getRowKey={(row) => row.id}
            emptyTitle="No audit events yet"
            emptyMessage="No records exist in paymentx_audit.audit_event yet."
          />
          <Pager page={data.page} size={data.size} totalElements={data.totalElements} onPageChange={setPage} />
        </>
      )}
    </PageContainer>
  )
}
