/**
 * ENGLISH: The "/notifications" page - real, paginated notification
 * records from paymentx_notification.notification, via the backend's
 * read-only Postgres layer. body/template content is deliberately not
 * shown - the backend's summary DTO already excludes it. Why it
 * exists: required route "/notifications", now wired to the real
 * Phase 2 backend. How it will communicate with the backend: via
 * useNotifications -> postgresService.ts -> GET
 * /api/v1/postgres/notifications.
 *
 * HINGLISH: "/notifications" page - real, paginated notification
 * records, paymentx_notification.notification se, backend ke
 * read-only Postgres layer ke through. body/template content yahan
 * jaan-boojh kar nahi dikhaya gaya - backend ka summary DTO use pehle
 * se exclude karta hai. Ye dashboard me kyu hai: required route
 * "/notifications", ab real Phase 2 backend se wired hai. Backend se
 * kaise connect hogi: useNotifications -> postgresService.ts -> GET
 * /api/v1/postgres/notifications ke through.
 */
import { useState } from 'react'
import { Alert, Button, Chip, Stack } from '@mui/material'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { MetricCard } from '../components/MetricCard'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { Pager } from '../components/Pager'
import { FilterBar } from '../components/FilterBar'
import { SearchBar } from '../components/SearchBar'
import { StatusBadge } from '../components/StatusBadge'
import { useNotifications } from '../hooks/usePostgresData'
import { useMailHogStatus } from '../hooks/useNotificationInfra'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { toApiError } from '../api/axiosClient'
import { formatTimestamp } from '../utils/formatters'
import type { NotificationSummary } from '../services/postgresService'

const PAGE_SIZE = 25

// ENGLISH: Live data shows only the INTERNAL channel is ever actually written by notification-service
// (see backend dto/postgres/NotificationSummary.java) - EMAIL/SMS/PUSH columns below render honestly
// ("—") rather than fabricating values for channels the platform has not implemented traffic for yet.
// HINGLISH: Live data dikhata hai ki sirf INTERNAL channel hi notification-service actually likhta hai
// - neeche EMAIL/SMS/PUSH ke liye columns honestly "—" render karte hain, un channels ke liye values
// invent kiye bina jinka platform par abhi traffic implement nahi hua.
const columns: DataTableColumn<NotificationSummary>[] = [
  { key: 'channel', label: 'Channel', render: (row) => row.channel },
  { key: 'status', label: 'Status', render: (row) => <Chip size="small" label={row.status} /> },
  { key: 'recipient', label: 'Recipient', render: (row) => row.recipient },
  { key: 'subject', label: 'Subject', render: (row) => row.subject ?? '—' },
  { key: 'retries', label: 'Retry', align: 'right', render: (row) => `${row.retryCount}/${row.maxRetries}` },
  { key: 'failureReason', label: 'Failure Reason', render: (row) => row.failureReason ?? '—' },
  { key: 'createdAt', label: 'Created', render: (row) => formatTimestamp(row.createdAt) },
]

export default function NotificationsPage() {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebouncedValue(search)
  const { data, isLoading, isError, error, refetch } = useNotifications(page, PAGE_SIZE, debouncedSearch)
  const mailhog = useMailHogStatus()

  return (
    <PageContainer>
      <PageHeader title="Notifications" description="Real notification delivery history from paymentx_notification.notification. Only the channels this platform actually implements (verified live) are ever shown." />

      {mailhog.data && (
        <Stack spacing={1}>
          <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
            <MetricCard label="MailHog" value={<StatusBadge status={mailhog.data.reachable ? 'UP' : 'DOWN'} />} />
            <MetricCard label="Captured Messages" value={mailhog.data.messageCount ?? '—'} />
          </Stack>
          {mailhog.data.reachable && mailhog.data.webUiUrl && (
            <Alert severity="info" action={
              <Button color="inherit" size="small" href={mailhog.data.webUiUrl} target="_blank" rel="noreferrer">
                Open MailHog
              </Button>
            }>
              Real captured emails (when the EMAIL channel is used) can be safely previewed in MailHog's own UI.
            </Alert>
          )}
          {!mailhog.data.reachable && (
            <Alert severity="warning">MailHog is not reachable right now{mailhog.data.errorMessage ? `: ${mailhog.data.errorMessage}` : '.'}</Alert>
          )}
        </Stack>
      )}

      <FilterBar>
        <SearchBar
          value={search}
          onChange={(value) => {
            setSearch(value)
            setPage(0)
          }}
          placeholder="Search recipient, subject, correlation ID…"
        />
      </FilterBar>

      {isLoading && <LoadingState message="Loading notifications…" />}
      {isError && <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />}
      {data && (
        <>
          <DataTable
            rows={data.content}
            columns={columns}
            getRowKey={(row) => row.id}
            emptyTitle="No notifications yet"
            emptyMessage="No records exist in paymentx_notification.notification yet."
          />
          <Pager page={data.page} size={data.size} totalElements={data.totalElements} onPageChange={setPage} />
        </>
      )}
    </PageContainer>
  )
}
