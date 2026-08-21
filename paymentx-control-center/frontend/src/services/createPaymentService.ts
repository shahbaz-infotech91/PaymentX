/**
 * The real API client for the Create Payment feature. What it does: builds a real
 * PaymentValidationRequest-shaped body (see paymentx-validation-service
 * dto/PaymentValidationRequest.java - paymentReference, scheme, amount, currency,
 * debtorAccount, debtorBankId, creditorAccount, creditorBankId) and sends it through the
 * EXISTING, already-allowlisted Phase 5 API Tester passthrough (executeApiTesterRequest,
 * service="api-gateway", method="POST", path="/api/v1/validations" - see backend
 * ApiTesterAllowlist.java line 76-77) rather than a new backend endpoint, since the design
 * audit (PAYMENTX_CREATE_PAYMENT_SCHEME_DESIGN.md) found no new backend capability is
 * required. classifyResponse() turns the real HTTP status + real ValidationResponse body
 * (status/rejectionReason - see ValidationResponse.java) into an honest, typed outcome -
 * VALIDATED/REJECTED/DUPLICATE mirror the endpoint's own three real statuses exactly, never
 * inventing a fourth "success" case for a rejection. Amount is inserted into the JSON body as
 * a raw decimal literal (never round-tripped through a JS number) so a value like "12.50"
 * reaches the backend's BigDecimal parser byte-for-byte, matching @DecimalMin("0.01") exactly.
 */
import { executeApiTesterRequest } from './apiTesterService'

export const SCHEME_OPTIONS = ['INSTANT_PAYMENT', 'REAL_TIME_PAYMENT', 'CARD_PAYMENT'] as const
export type PaymentScheme = (typeof SCHEME_OPTIONS)[number]

export type CredentialHeaderName = 'Authorization' | 'X-Api-Key'

export interface CreatePaymentRequest {
  paymentReference: string
  scheme: PaymentScheme
  amount: string
  currency: string
  debtorAccount: string
  debtorBankId: string
  creditorAccount: string
  creditorBankId: string
  credentialHeaderName: CredentialHeaderName
  credentialValue: string
}

interface RawValidationResponse {
  paymentReference?: string
  traceId?: string
  status?: 'VALIDATED' | 'REJECTED' | 'DUPLICATE'
  rejectionReason?: string | null
}

export type CreatePaymentOutcome =
  | { kind: 'VALIDATED'; paymentReference: string; traceId: string | null }
  | { kind: 'REJECTED'; paymentReference: string; reason: string }
  | { kind: 'DUPLICATE'; paymentReference: string; reason: string }
  | { kind: 'UNAUTHORIZED'; message: string }
  | { kind: 'UPSTREAM_ERROR'; status: number; message: string }

/** JSON-escapes a string field; never used for `amount`, which must stay a raw numeric literal. */
function jsonStringField(value: string): string {
  return JSON.stringify(value)
}

function buildRequestBody(request: CreatePaymentRequest): string {
  const amountLiteral = request.amount.trim()
  return (
    '{' +
    `"paymentReference":${jsonStringField(request.paymentReference)},` +
    `"scheme":${jsonStringField(request.scheme)},` +
    `"amount":${amountLiteral},` +
    `"currency":${jsonStringField(request.currency)},` +
    `"debtorAccount":${jsonStringField(request.debtorAccount)},` +
    `"debtorBankId":${jsonStringField(request.debtorBankId)},` +
    `"creditorAccount":${jsonStringField(request.creditorAccount)},` +
    `"creditorBankId":${jsonStringField(request.creditorBankId)}` +
    '}'
  )
}

function buildCredentialHeader(request: CreatePaymentRequest): Record<string, string> {
  const value = request.credentialValue.trim()
  if (!value) return {}
  if (request.credentialHeaderName === 'Authorization') {
    return { Authorization: /^bearer\s/i.test(value) ? value : `Bearer ${value}` }
  }
  return { 'X-Api-Key': value }
}

function safeParseValidationResponse(body: string | null): RawValidationResponse | null {
  if (!body) return null
  try {
    return JSON.parse(body) as RawValidationResponse
  } catch {
    return null
  }
}

export async function createPayment(request: CreatePaymentRequest): Promise<CreatePaymentOutcome> {
  const response = await executeApiTesterRequest({
    service: 'api-gateway',
    method: 'POST',
    path: '/api/v1/validations',
    queryParams: {},
    headers: buildCredentialHeader(request),
    body: buildRequestBody(request),
  })

  if (response.status === 401 || response.status === 403) {
    return { kind: 'UNAUTHORIZED', message: response.body || 'Invalid or missing credential (Authorization / X-Api-Key).' }
  }

  const parsed = safeParseValidationResponse(response.body)

  if (response.status === 409) {
    return {
      kind: 'DUPLICATE',
      paymentReference: parsed?.paymentReference ?? request.paymentReference,
      reason: parsed?.rejectionReason ?? 'This payment reference has already been submitted.',
    }
  }

  if (response.status === 200 && parsed?.status === 'REJECTED') {
    return {
      kind: 'REJECTED',
      paymentReference: parsed.paymentReference ?? request.paymentReference,
      reason: parsed.rejectionReason ?? 'Rejected by validation.',
    }
  }

  if (response.status === 200 && parsed?.status === 'VALIDATED') {
    return {
      kind: 'VALIDATED',
      paymentReference: parsed.paymentReference ?? request.paymentReference,
      traceId: parsed.traceId ?? null,
    }
  }

  return {
    kind: 'UPSTREAM_ERROR',
    status: response.status,
    message: response.body || `${response.status} ${response.statusText}`,
  }
}
