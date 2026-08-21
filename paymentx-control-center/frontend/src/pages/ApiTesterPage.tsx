/**
 * ENGLISH: The "/api-tester" page - a real, Postman-like tester scoped
 * to the backend's real, fixed allowlist (see backend
 * ApiTesterAllowlist.java) - NOT a free-URL tool anymore. What it does:
 * the user picks a real, allowlisted endpoint (service + method +
 * path template) from a dropdown built from GET
 * /api/v1/api-tester/endpoints, fills in any {placeholder} segments
 * and optional query params/headers/body, and sends it - the backend
 * (ApiTesterService) is the ONLY thing that ever constructs the actual
 * outbound URL, and it rejects anything not on that same fixed
 * allowlist, which is the real SSRF defense here (this page cannot
 * bypass it - there is no field anywhere for a raw URL). Destructive
 * endpoints (the two real DELETE endpoints on the allowlist) require
 * an extra confirmation step. Result history is bounded and local
 * (browser localStorage), and deliberately stores only
 * service/method/path/status/duration/timestamp - never headers or
 * body, so a real Authorization/X-Api-Key the user pastes in is never
 * persisted. Why it exists: required Phase 5 "API Tester" module,
 * reworked for the Phase 5 "MUST NOT allow arbitrary URLs" security
 * requirement. How it will communicate with the backend: via
 * useApiTesterEndpoints/useExecuteApiTesterRequest -> apiTesterService.ts
 * -> GET/POST /api/v1/api-tester/*.
 *
 * HINGLISH: "/api-tester" page - ek real, Postman-jaisa tester jo
 * backend ke real, fixed allowlist tak scoped hai (backend
 * ApiTesterAllowlist.java dekho) - ab ek free-URL tool NAHI hai. Ye kya
 * karti hai: user GET /api/v1/api-tester/endpoints se bane dropdown se
 * ek real, allowlisted endpoint (service + method + path template)
 * chunta hai, koi bhi {placeholder} segments aur optional query
 * params/headers/body bharta hai, aur bhejta hai - backend
 * (ApiTesterService) hi ek matra cheez hai jo kabhi actual outbound URL
 * construct karti hai, aur wo kisi bhi cheez ko reject kar deti hai jo
 * usi fixed allowlist par na ho, yehi real SSRF defense hai (ye page
 * ise bypass nahi kar sakta - kahin bhi koi raw URL ke liye field nahi
 * hai). Destructive endpoints (allowlist ke do real DELETE endpoints)
 * ko ek extra confirmation step chahiye. Result history bounded aur
 * local hai (browser localStorage), aur jaan-boojh kar sirf
 * service/method/path/status/duration/timestamp store karta hai -
 * kabhi headers ya body nahi, taaki user jo real Authorization/X-Api-Key
 * paste kare wo kabhi persist na ho. Ye dashboard me kyu hai: required
 * Phase 5 "API Tester" module, Phase 5 ke "arbitrary URLs allow nahi"
 * security requirement ke liye rework kiya gaya. Backend se kaise
 * connect hogi: useApiTesterEndpoints/useExecuteApiTesterRequest ->
 * apiTesterService.ts -> GET/POST /api/v1/api-tester/* ke through.
 */
import { useMemo, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Chip,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import SendIcon from '@mui/icons-material/Send'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { ErrorState } from '../components/ErrorState'
import { LoadingState } from '../components/LoadingState'
import { ConfirmationDialog } from '../components/ConfirmationDialog'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { useApiTesterEndpoints, useExecuteApiTesterRequest } from '../hooks/useApiTester'
import { toApiError } from '../api/axiosClient'
import { formatTimestamp } from '../utils/formatters'
import {
  appendApiTesterHistory,
  clearApiTesterHistory,
  loadApiTesterHistory,
  type ApiTesterHistoryEntry,
} from '../utils/apiTesterHistory'
import type { ApiTesterEndpointDescriptor } from '../services/apiTesterService'

function isValidJsonObject(value: string): boolean {
  if (value.trim() === '') return true
  try {
    const parsed = JSON.parse(value)
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
  } catch {
    return false
  }
}

function parseJsonObject(value: string): Record<string, string> {
  if (value.trim() === '') return {}
  return JSON.parse(value)
}

const historyColumns: DataTableColumn<ApiTesterHistoryEntry>[] = [
  { key: 'method', label: 'Method', render: (row) => <Chip size="small" label={row.method} /> },
  { key: 'service', label: 'Service', render: (row) => row.service },
  { key: 'path', label: 'Path', render: (row) => row.path },
  {
    key: 'status',
    label: 'Status',
    render: (row) => (
      <Chip size="small" color={row.succeeded ? 'success' : 'error'} label={row.status ?? 'error'} />
    ),
  },
  { key: 'responseTime', label: 'Response Time', align: 'right', render: (row) => (row.responseTimeMillis != null ? `${row.responseTimeMillis} ms` : '—') },
  { key: 'timestamp', label: 'When', render: (row) => formatTimestamp(row.timestamp) },
]

export default function ApiTesterPage() {
  const endpoints = useApiTesterEndpoints()
  const execute = useExecuteApiTesterRequest()

  const [selectedKey, setSelectedKey] = useState('')
  const [path, setPath] = useState('')
  const [queryParamsJson, setQueryParamsJson] = useState('')
  const [headersJson, setHeadersJson] = useState('')
  const [bodyJson, setBodyJson] = useState('')
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [history, setHistory] = useState<ApiTesterHistoryEntry[]>(() => loadApiTesterHistory())

  const endpointKey = (e: ApiTesterEndpointDescriptor) => `${e.service}::${e.method}::${e.pathTemplate}`
  const selected = useMemo(
    () => endpoints.data?.find((e) => endpointKey(e) === selectedKey) ?? null,
    [endpoints.data, selectedKey],
  )

  function selectEndpoint(key: string) {
    setSelectedKey(key)
    const endpoint = endpoints.data?.find((e) => endpointKey(e) === key)
    setPath(endpoint?.pathTemplate ?? '')
  }

  const queryParamsValid = isValidJsonObject(queryParamsJson)
  const headersValid = isValidJsonObject(headersJson)
  const bodyValid = isValidJsonObject(bodyJson) || bodyJson.trim() === ''
  const canSend = !!selected && path.trim() !== '' && queryParamsValid && headersValid && bodyValid

  function doSend() {
    if (!selected) return
    const startedAt = new Date().toISOString()
    execute.mutate(
      {
        service: selected.service,
        method: selected.method,
        path: path.trim(),
        queryParams: parseJsonObject(queryParamsJson),
        headers: parseJsonObject(headersJson),
        body: bodyJson.trim() === '' ? null : bodyJson,
      },
      {
        onSuccess: (result) => {
          setHistory(
            appendApiTesterHistory({
              service: selected.service,
              method: selected.method,
              path: path.trim(),
              status: result.status,
              responseTimeMillis: result.responseTimeMillis,
              succeeded: result.status < 400,
              timestamp: startedAt,
            }),
          )
        },
        onError: () => {
          setHistory(
            appendApiTesterHistory({
              service: selected.service,
              method: selected.method,
              path: path.trim(),
              status: null,
              responseTimeMillis: null,
              succeeded: false,
              timestamp: startedAt,
            }),
          )
        },
      },
    )
  }

  function handleSendClick() {
    if (!canSend) return
    if (selected?.destructive) {
      setConfirmOpen(true)
      return
    }
    doSend()
  }

  const result = execute.data

  return (
    <PageContainer>
      <PageHeader
        title="API Tester"
        description="Send real requests to a fixed allowlist of real PaymentX endpoints - never an arbitrary URL. Requests are proxied by this dashboard's own backend, which enforces the allowlist server-side."
      />

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Stack spacing={2}>
          {endpoints.isLoading && <LoadingState message="Loading the allowed endpoint list…" />}
          {endpoints.isError && <ErrorState message={toApiError(endpoints.error).message} onRetry={() => endpoints.refetch()} />}

          {endpoints.data && (
            <TextField
              select
              label="Endpoint (service + method + path)"
              value={selectedKey}
              onChange={(e) => selectEndpoint(e.target.value)}
              size="small"
              fullWidth
            >
              {endpoints.data.map((e) => (
                <MenuItem key={endpointKey(e)} value={endpointKey(e)}>
                  {e.method} — {e.service} — {e.pathTemplate}
                  {e.destructive ? ' ⚠' : ''}
                </MenuItem>
              ))}
            </TextField>
          )}

          {selected && (
            <>
              <Typography variant="body2" color="text.secondary">
                {selected.description}
                {selected.destructive && (
                  <Chip size="small" color="warning" label="Destructive - requires confirmation" sx={{ ml: 1 }} />
                )}
              </Typography>

              <Stack direction="row" spacing={2}>
                <TextField label="Method" value={selected.method} size="small" sx={{ width: 140 }} disabled />
                <TextField
                  label="Path (fill in any {placeholders})"
                  value={path}
                  onChange={(e) => setPath(e.target.value)}
                  size="small"
                  fullWidth
                  error={path.trim() === ''}
                />
              </Stack>

              <TextField
                label="Query Params (JSON object, optional)"
                placeholder='{"page": "0", "size": "20"}'
                value={queryParamsJson}
                onChange={(e) => setQueryParamsJson(e.target.value)}
                multiline
                minRows={2}
                size="small"
                error={!queryParamsValid}
                helperText={!queryParamsValid ? 'Must be a JSON object or empty' : undefined}
              />

              <TextField
                label="Headers (JSON object, optional - e.g. a real Authorization or X-Api-Key you already hold)"
                placeholder='{"Authorization": "Bearer ..."}'
                value={headersJson}
                onChange={(e) => setHeadersJson(e.target.value)}
                multiline
                minRows={2}
                size="small"
                error={!headersValid}
                helperText={!headersValid ? 'Must be a JSON object or empty' : 'Never persisted in local history'}
              />

              {['POST', 'PUT', 'PATCH'].includes(selected.method) && (
                <TextField
                  label="Request Body (JSON, optional)"
                  placeholder='{"key": "value"}'
                  value={bodyJson}
                  onChange={(e) => setBodyJson(e.target.value)}
                  multiline
                  minRows={4}
                  size="small"
                  error={!bodyValid}
                  helperText={!bodyValid ? 'Must be valid JSON or empty' : undefined}
                />
              )}

              <Box>
                <Button
                  variant="contained"
                  startIcon={<SendIcon />}
                  disabled={!canSend || execute.isPending}
                  onClick={handleSendClick}
                >
                  Send request
                </Button>
              </Box>
            </>
          )}
        </Stack>
      </Paper>

      <ConfirmationDialog
        open={confirmOpen}
        title="Send a destructive request?"
        message={`This will call ${selected?.method} ${path} on the real ${selected?.service}, which permanently deletes real data. This action cannot be undone.`}
        confirmLabel="Send it"
        destructive
        onConfirm={() => {
          setConfirmOpen(false)
          doSend()
        }}
        onCancel={() => setConfirmOpen(false)}
      />

      {execute.isPending && <LoadingState message="Sending request…" />}
      {execute.isError && <ErrorState message={toApiError(execute.error).message} />}
      {result && (
        <Paper variant="outlined" sx={{ p: 3 }}>
          <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 2 }}>
            <Chip color={result.status < 400 ? 'success' : 'error'} label={`${result.status} ${result.statusText}`} />
            <Typography variant="body2" color="text.secondary">
              {result.responseTimeMillis} ms
            </Typography>
          </Stack>
          <Typography variant="subtitle2" gutterBottom>
            Headers
          </Typography>
          <Box component="pre" sx={{ m: 0, mb: 2, p: 2, bgcolor: 'action.hover', borderRadius: 1, overflow: 'auto', fontSize: '0.75rem', maxHeight: 200 }}>
            {JSON.stringify(result.headers, null, 2)}
          </Box>
          <Typography variant="subtitle2" gutterBottom>
            Body
          </Typography>
          <Box component="pre" sx={{ m: 0, p: 2, bgcolor: 'action.hover', borderRadius: 1, overflow: 'auto', fontSize: '0.8rem', maxHeight: 420 }}>
            {result.body ?? '(empty body)'}
          </Box>
        </Paper>
      )}

      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="h2" component="h2">
          Result History (local, bounded to 20, no secrets stored)
        </Typography>
        {history.length > 0 && (
          <Button size="small" color="inherit" onClick={() => setHistory(clearApiTesterHistory())}>
            Clear history
          </Button>
        )}
      </Stack>
      {history.length === 0 ? (
        <Alert severity="info">No requests sent yet in this browser.</Alert>
      ) : (
        <DataTable rows={history} columns={historyColumns} getRowKey={(row) => `${row.timestamp}-${row.path}`} />
      )}
    </PageContainer>
  )
}
