/**
 * The "/create-payment" page - a real, guided form for submitting a real PaymentX payment
 * through the EXISTING entry point, POST /api/v1/validations (ValidationController - see
 * PAYMENTX_CREATE_PAYMENT_SCHEME_DESIGN.md). What it does: the user picks a real scheme/network
 * (the exact three values PaymentScheme/RoutingScheme/Scheme already share - INSTANT_PAYMENT,
 * REAL_TIME_PAYMENT, CARD_PAYMENT), a real debtor/creditor participant (from the existing GET
 * /api/v1/postgres/participants list), an amount/currency/account pair, and a real credential
 * they already hold (Authorization or X-Api-Key - the same "operator supplies their own"
 * pattern the API Tester already uses for this exact endpoint) - then submits through the
 * existing, already-allowlisted API Tester passthrough (see createPaymentService.ts). The
 * chosen scheme is sent verbatim as PaymentValidationRequest.scheme, the one field Routing
 * Service already uses end to end - there is no separate "payment type" selector here, because
 * the real PaymentValidationRequest contract has no such field (PaymentType is assigned
 * internally when a payment is persisted, not chosen by the caller at submission time - a
 * verified finding of the design audit, not an oversight). The payment reference doubles as the
 * real idempotency key (the endpoint's actual mechanism - a DB unique constraint, not a
 * header); it is generated once per form session and only changes when the user explicitly
 * clicks "Generate new" or starts another payment, so an accidental double-click safely
 * reproduces the exact same request instead of silently creating a second one. Credential input
 * is never persisted (component state only), matching ApiTesterService's own logging/storage
 * discipline.
 */
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Alert,
  AlertTitle,
  Box,
  Button,
  Grid2 as Grid,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import SendIcon from '@mui/icons-material/Send'
import RefreshIcon from '@mui/icons-material/Refresh'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { useParticipants } from '../hooks/usePostgresData'
import { useCreatePayment } from '../hooks/useCreatePayment'
import { toApiError } from '../api/axiosClient'
import { SCHEME_OPTIONS, type CredentialHeaderName, type PaymentScheme } from '../services/createPaymentService'

function createReference(): string {
  const id =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `CC-${id}`
}

const AMOUNT_PATTERN = /^\d+(\.\d{1,2})?$/
const CURRENCY_PATTERN = /^[A-Z]{3}$/

export default function CreatePaymentPage() {
  const navigate = useNavigate()
  const participants = useParticipants(0, 100)
  const createPaymentMutation = useCreatePayment()

  const [paymentReference, setPaymentReference] = useState(createReference)
  const [scheme, setScheme] = useState<PaymentScheme | ''>('')
  const [amount, setAmount] = useState('')
  const [currency, setCurrency] = useState('USD')
  const [debtorBankId, setDebtorBankId] = useState('')
  const [debtorAccount, setDebtorAccount] = useState('')
  const [creditorBankId, setCreditorBankId] = useState('')
  const [creditorAccount, setCreditorAccount] = useState('')
  const [credentialHeaderName, setCredentialHeaderName] = useState<CredentialHeaderName>('X-Api-Key')
  const [credentialValue, setCredentialValue] = useState('')

  const amountValid = amount.trim() !== '' && AMOUNT_PATTERN.test(amount.trim()) && Number(amount) >= 0.01
  const currencyValid = CURRENCY_PATTERN.test(currency.trim())
  const referenceValid = paymentReference.trim() !== ''
  const schemeValid = scheme !== ''
  const debtorValid = debtorBankId.trim() !== '' && debtorAccount.trim() !== ''
  const creditorValid = creditorBankId.trim() !== '' && creditorAccount.trim() !== ''
  const credentialValid = credentialValue.trim() !== ''

  const canSubmit = useMemo(
    () => referenceValid && schemeValid && amountValid && currencyValid && debtorValid && creditorValid && credentialValid,
    [referenceValid, schemeValid, amountValid, currencyValid, debtorValid, creditorValid, credentialValid],
  )

  function handleSubmit() {
    if (!canSubmit || !scheme || createPaymentMutation.isPending) return
    createPaymentMutation.mutate({
      paymentReference: paymentReference.trim(),
      scheme,
      amount: amount.trim(),
      currency: currency.trim().toUpperCase(),
      debtorAccount: debtorAccount.trim(),
      debtorBankId: debtorBankId.trim(),
      creditorAccount: creditorAccount.trim(),
      creditorBankId: creditorBankId.trim(),
      credentialHeaderName,
      credentialValue,
    })
  }

  function startNewPayment() {
    setPaymentReference(createReference())
    createPaymentMutation.reset()
  }

  const outcome = createPaymentMutation.data

  return (
    <PageContainer>
      <PageHeader
        title="Create Payment"
        description="Submit a real payment through the existing entry point (POST /api/v1/validations). The scheme you select here is the same field Routing Service already uses to resolve the route."
      />

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Stack spacing={3}>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Payment Reference (also the idempotency key)"
                value={paymentReference}
                onChange={(e) => setPaymentReference(e.target.value)}
                fullWidth
                size="small"
                error={!referenceValid}
                helperText="Resubmitting the same reference safely returns the existing result instead of creating a duplicate."
                InputProps={{
                  endAdornment: (
                    <Button size="small" startIcon={<RefreshIcon />} onClick={() => setPaymentReference(createReference())}>
                      Generate new
                    </Button>
                  ),
                }}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                select
                label="Scheme / Network"
                value={scheme}
                onChange={(e) => setScheme(e.target.value as PaymentScheme)}
                fullWidth
                size="small"
                error={!schemeValid}
                helperText="Sent as PaymentValidationRequest.scheme - determines Routing Service's route resolution."
              >
                {SCHEME_OPTIONS.map((s) => (
                  <MenuItem key={s} value={s}>
                    {s}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>

            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Amount"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                fullWidth
                size="small"
                placeholder="100.00"
                error={amount.trim() !== '' && !amountValid}
                helperText="Decimal, minimum 0.01"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Currency"
                value={currency}
                onChange={(e) => setCurrency(e.target.value.toUpperCase())}
                fullWidth
                size="small"
                inputProps={{ maxLength: 3 }}
                error={currency.trim() !== '' && !currencyValid}
                helperText="3-letter ISO currency code"
              />
            </Grid>

            <Grid size={{ xs: 12 }}>
              <Typography variant="subtitle2">Source (Debtor)</Typography>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                select
                label="Source Participant"
                value={debtorBankId}
                onChange={(e) => setDebtorBankId(e.target.value)}
                fullWidth
                size="small"
                error={debtorBankId.trim() === ''}
              >
                <MenuItem value="" disabled>
                  {participants.isLoading ? 'Loading participants…' : 'Select a participant'}
                </MenuItem>
                {participants.data?.content.map((p) => (
                  <MenuItem key={p.bankId} value={p.bankId}>
                    {p.legalName} ({p.bankId})
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Debtor Account"
                value={debtorAccount}
                onChange={(e) => setDebtorAccount(e.target.value)}
                fullWidth
                size="small"
                error={debtorAccount.trim() === ''}
              />
            </Grid>

            <Grid size={{ xs: 12 }}>
              <Typography variant="subtitle2">Destination (Creditor)</Typography>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                select
                label="Destination Participant"
                value={creditorBankId}
                onChange={(e) => setCreditorBankId(e.target.value)}
                fullWidth
                size="small"
                error={creditorBankId.trim() === ''}
              >
                <MenuItem value="" disabled>
                  {participants.isLoading ? 'Loading participants…' : 'Select a participant'}
                </MenuItem>
                {participants.data?.content.map((p) => (
                  <MenuItem key={p.bankId} value={p.bankId}>
                    {p.legalName} ({p.bankId})
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Creditor Account"
                value={creditorAccount}
                onChange={(e) => setCreditorAccount(e.target.value)}
                fullWidth
                size="small"
                error={creditorAccount.trim() === ''}
              />
            </Grid>

            <Grid size={{ xs: 12 }}>
              <Typography variant="subtitle2">Credential</Typography>
              <Typography variant="caption" color="text.secondary">
                Your own real, already-provisioned API key or JWT for the API Gateway - the same credential the API
                Tester requires for this endpoint. Never stored; forwarded once and discarded.
              </Typography>
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                select
                label="Credential Type"
                value={credentialHeaderName}
                onChange={(e) => setCredentialHeaderName(e.target.value as CredentialHeaderName)}
                fullWidth
                size="small"
              >
                <MenuItem value="X-Api-Key">X-Api-Key</MenuItem>
                <MenuItem value="Authorization">Authorization (Bearer JWT)</MenuItem>
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 8 }}>
              <TextField
                label={credentialHeaderName === 'Authorization' ? 'Bearer token' : 'X-Api-Key value'}
                type="password"
                value={credentialValue}
                onChange={(e) => setCredentialValue(e.target.value)}
                fullWidth
                size="small"
                error={!credentialValid}
              />
            </Grid>
          </Grid>

          <Box>
            <Button
              variant="contained"
              startIcon={<SendIcon />}
              disabled={!canSubmit || createPaymentMutation.isPending}
              onClick={handleSubmit}
            >
              Create Payment
            </Button>
          </Box>
        </Stack>
      </Paper>

      {createPaymentMutation.isPending && <LoadingState message="Submitting to validation service…" />}
      {createPaymentMutation.isError && (
        <ErrorState message={toApiError(createPaymentMutation.error).message} onRetry={handleSubmit} />
      )}

      {outcome && outcome.kind === 'VALIDATED' && (
        <Alert severity="success" action={
          <Stack direction="row" spacing={1}>
            <Button color="inherit" size="small" onClick={() => navigate(`/payment-flow?reference=${encodeURIComponent(outcome.paymentReference)}`)}>
              View Payment Flow
            </Button>
            <Button
              color="inherit"
              size="small"
              onClick={() => navigate('/ai-agents/execute', {
                state: { agentId: 'database-analysis-agent', paymentReference: outcome.paymentReference },
              })}
            >
              Verify with AI Agent
            </Button>
          </Stack>
        }>
          <AlertTitle>Payment validated</AlertTitle>
          <Stack spacing={0.5}>
            <Typography variant="body2">Reference: {outcome.paymentReference}</Typography>
            <Typography variant="body2">Scheme: {scheme}</Typography>
            <Typography variant="body2">Amount: {amount} {currency}</Typography>
            {outcome.traceId && <Typography variant="body2">Trace ID: {outcome.traceId}</Typography>}
          </Stack>
        </Alert>
      )}

      {outcome && outcome.kind === 'REJECTED' && (
        <Alert severity="warning">
          <AlertTitle>Rejected by validation</AlertTitle>
          {outcome.reason}
        </Alert>
      )}

      {outcome && outcome.kind === 'DUPLICATE' && (
        <Alert severity="warning" action={
          <Stack direction="row" spacing={1}>
            <Button color="inherit" size="small" onClick={() => navigate(`/payment-flow?reference=${encodeURIComponent(outcome.paymentReference)}`)}>
              View Payment Flow
            </Button>
            <Button
              color="inherit"
              size="small"
              onClick={() => navigate('/ai-agents/execute', {
                state: { agentId: 'database-analysis-agent', paymentReference: outcome.paymentReference },
              })}
            >
              Verify with AI Agent
            </Button>
          </Stack>
        }>
          <AlertTitle>Already submitted</AlertTitle>
          {outcome.reason}
        </Alert>
      )}

      {outcome && outcome.kind === 'UNAUTHORIZED' && (
        <Alert severity="error">
          <AlertTitle>Unauthorized</AlertTitle>
          {outcome.message}
        </Alert>
      )}

      {outcome && outcome.kind === 'UPSTREAM_ERROR' && (
        <Alert severity="error">
          <AlertTitle>Request failed ({outcome.status})</AlertTitle>
          {outcome.message}
        </Alert>
      )}

      {outcome && (
        <Box>
          <Button size="small" onClick={startNewPayment}>
            Start another payment
          </Button>
        </Box>
      )}
    </PageContainer>
  )
}
