/**
 * ENGLISH: The "/payment-flow" page - the real, 9-stage journey of one
 * real payment (Gateway -> Authentication -> Validation -> Routing ->
 * Payment -> Audit -> Notification -> Reconciliation -> Reporting).
 * What it does: looks a payment up by its real reference (typed in,
 * or arriving via ?reference= from Global Search / Transaction
 * Monitor), then renders each stage's real status from
 * PaymentFlowService on the backend - COMPLETED/PROCESSING/FAILED are
 * always backed by a real row that was actually found; NOT_STARTED
 * means the backend looked and found nothing yet; UNAVAILABLE
 * (Authentication, Reconciliation, Reporting) is a documented,
 * honest limitation of this schema, never silently shown as done.
 * Clicking a stage opens its real detail (event type / notification
 * channel / raw status, and the real timestamp it was observed at).
 * Why it exists: required route "/payment-flow", now wired to the
 * real Phase 3 backend. How it will communicate with the backend: via
 * usePaymentFlow -> postgresService.ts -> GET /api/v1/postgres/
 * payments/by-reference/{reference}/flow.
 *
 * HINGLISH: "/payment-flow" page - ek real payment ka real, 9-stage
 * journey (Gateway -> Authentication -> Validation -> Routing ->
 * Payment -> Audit -> Notification -> Reconciliation -> Reporting).
 * Ye kya karti hai: ek payment ko uske real reference se dhoondta hai
 * (type kiya gaya, ya Global Search / Transaction Monitor se
 * ?reference= ke through aaya), phir backend ke PaymentFlowService se
 * har stage ka real status render karta hai - COMPLETED/PROCESSING/
 * FAILED hamesha ek real row se backed hote hain jo actually mili;
 * NOT_STARTED ka matlab hai backend ne dekha aur abhi tak kuch nahi
 * mila; UNAVAILABLE (Authentication, Reconciliation, Reporting) is
 * schema ki ek documented, honest limitation hai, kabhi silently done
 * nahi dikhaya jaata. Ek stage par click karne se uska real detail
 * khulta hai (event type / notification channel / raw status, aur wo
 * real timestamp jab observe kiya gaya). Ye dashboard me kyu hai:
 * required route "/payment-flow", ab real Phase 3 backend se wired
 * hai. Backend se kaise connect hogi: usePaymentFlow ->
 * postgresService.ts -> GET /api/v1/postgres/payments/by-reference/
 * {reference}/flow ke through.
 */
import { useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogContent,
  DialogTitle,
  Stack,
  Typography,
} from '@mui/material'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import AutorenewIcon from '@mui/icons-material/Autorenew'
import CancelIcon from '@mui/icons-material/Cancel'
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked'
import BlockIcon from '@mui/icons-material/Block'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { SearchBar } from '../components/SearchBar'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { EmptyState } from '../components/EmptyState'
import { usePaymentFlow } from '../hooks/usePostgresData'
import { toApiError } from '../api/axiosClient'
import { formatTimestamp } from '../utils/formatters'
import type { PaymentFlowStage, PaymentFlowStageStatus } from '../services/postgresService'

const STAGE_APPEARANCE: Record<PaymentFlowStageStatus, { color: string; icon: ReactNode; label: string }> = {
  COMPLETED: { color: '#22c55e', icon: <CheckCircleIcon fontSize="small" />, label: 'Completed' },
  PROCESSING: { color: '#f59e0b', icon: <AutorenewIcon fontSize="small" />, label: 'Processing' },
  FAILED: { color: '#ef4444', icon: <CancelIcon fontSize="small" />, label: 'Failed' },
  NOT_STARTED: { color: '#94a3b8', icon: <RadioButtonUncheckedIcon fontSize="small" />, label: 'Not Started' },
  UNAVAILABLE: { color: '#64748b', icon: <BlockIcon fontSize="small" />, label: 'Unavailable' },
}

export default function PaymentFlowPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const initialReference = searchParams.get('reference') ?? ''
  const [input, setInput] = useState(initialReference)
  const [reference, setReference] = useState<string | null>(initialReference || null)
  const [selectedStage, setSelectedStage] = useState<PaymentFlowStage | null>(null)

  const flow = usePaymentFlow(reference)

  function lookUp() {
    const trimmed = input.trim()
    if (!trimmed) return
    setReference(trimmed)
    setSearchParams({ reference: trimmed })
  }

  return (
    <PageContainer>
      <PageHeader title="Payment Flow" description="Real, stage-by-stage journey of one real payment through the platform." />

      <Stack direction="row" spacing={1} alignItems="center">
        <SearchBar value={input} onChange={setInput} placeholder="Enter a real payment reference…" />
        <Button variant="contained" disabled={!input.trim()} onClick={lookUp}>
          Look up
        </Button>
      </Stack>

      {flow.isFetching && <LoadingState message="Loading payment flow…" />}
      {flow.isError && <ErrorState message={toApiError(flow.error).message} onRetry={() => flow.refetch()} />}
      {!reference && !flow.isFetching && (
        <EmptyState title="No payment selected" message="Enter a real payment reference above and click Look up." />
      )}

      {flow.data && (
        <>
          <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap alignItems="center">
            <Chip label={`Reference: ${flow.data.paymentReference}`} />
            <Chip label={`Correlation ID: ${flow.data.correlationId}`} variant="outlined" />
            <Chip label={`Trace ID: ${flow.data.traceId}`} variant="outlined" />
            <Chip color="primary" label={`Current status: ${flow.data.currentStatus}`} />
          </Stack>

          <Stack spacing={0} sx={{ mt: 2 }}>
            {flow.data.stages.map((stage, index) => {
              const appearance = STAGE_APPEARANCE[stage.status]
              return (
                <motion.div
                  key={stage.stage}
                  initial={{ opacity: 0, x: -12 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ duration: 0.25, delay: index * 0.05 }}
                >
                  <Box
                    onClick={() => setSelectedStage(stage)}
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 2,
                      py: 1.5,
                      px: 2,
                      cursor: 'pointer',
                      borderRadius: 1,
                      '&:hover': { bgcolor: 'action.hover' },
                    }}
                  >
                    <Box
                      sx={{
                        width: 36,
                        height: 36,
                        borderRadius: '50%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        bgcolor: `${appearance.color}22`,
                        color: appearance.color,
                        flexShrink: 0,
                      }}
                    >
                      {stage.status === 'PROCESSING' ? (
                        <motion.div
                          animate={{ rotate: 360 }}
                          transition={{ repeat: Infinity, duration: 1.6, ease: 'linear' }}
                        >
                          {appearance.icon}
                        </motion.div>
                      ) : (
                        appearance.icon
                      )}
                    </Box>
                    <Stack sx={{ flex: 1, minWidth: 0 }}>
                      <Typography variant="subtitle2" fontWeight={600}>
                        {stage.stage}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" noWrap>
                        {stage.detail ?? '—'}
                      </Typography>
                    </Stack>
                    <Chip size="small" label={appearance.label} sx={{ bgcolor: `${appearance.color}22`, color: appearance.color, fontWeight: 600 }} />
                  </Box>
                  {index < flow.data.stages.length - 1 && (
                    <Box sx={{ ml: '35px', width: 2, height: 16, bgcolor: 'divider' }} />
                  )}
                </motion.div>
              )
            })}
          </Stack>
        </>
      )}

      <Dialog open={selectedStage !== null} onClose={() => setSelectedStage(null)} maxWidth="xs" fullWidth>
        {selectedStage && (
          <>
            <DialogTitle>{selectedStage.stage}</DialogTitle>
            <DialogContent>
              <Stack spacing={1.5} sx={{ pb: 1 }}>
                <Chip
                  label={STAGE_APPEARANCE[selectedStage.status].label}
                  sx={{
                    alignSelf: 'flex-start',
                    bgcolor: `${STAGE_APPEARANCE[selectedStage.status].color}22`,
                    color: STAGE_APPEARANCE[selectedStage.status].color,
                    fontWeight: 600,
                  }}
                />
                <Typography variant="body2">{selectedStage.detail ?? 'No further detail available.'}</Typography>
                <Typography variant="caption" color="text.secondary">
                  Observed at: {selectedStage.occurredAt ? formatTimestamp(selectedStage.occurredAt) : '—'}
                </Typography>
              </Stack>
            </DialogContent>
          </>
        )}
      </Dialog>
    </PageContainer>
  )
}
