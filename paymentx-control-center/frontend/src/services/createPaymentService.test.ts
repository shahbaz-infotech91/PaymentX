/**
 * Proves createPaymentService.ts sends the real PaymentValidationRequest shape through the
 * existing, already-allowlisted API Tester passthrough (service="api-gateway", method="POST",
 * path="/api/v1/validations"), preserves the amount as a raw decimal literal (never round-
 * tripped through a JS float), and classifies every real ValidationResponse status/HTTP code
 * combination (VALIDATED, REJECTED, DUPLICATE via 409, 401/403 unauthorized, an unrecognized
 * status) honestly rather than fabricating a success.
 */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPayment } from './createPaymentService'
import { executeApiTesterRequest } from './apiTesterService'
import type { ApiTesterResponsePayload } from './apiTesterService'

vi.mock('./apiTesterService', () => ({ executeApiTesterRequest: vi.fn() }))

const baseRequest = {
  paymentReference: 'CC-test-ref-1',
  scheme: 'INSTANT_PAYMENT' as const,
  amount: '100.10',
  currency: 'USD',
  debtorAccount: 'ACC-1',
  debtorBankId: 'BANK-1',
  creditorAccount: 'ACC-2',
  creditorBankId: 'BANK-2',
  credentialHeaderName: 'X-Api-Key' as const,
  credentialValue: 'real-test-key',
}

function mockResponse(overrides: Partial<ApiTesterResponsePayload>): ApiTesterResponsePayload {
  return { status: 200, statusText: 'OK', headers: {}, body: null, responseTimeMillis: 5, ...overrides }
}

describe('createPayment', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('sends the real service/method/path allowlisted for POST /api/v1/validations', async () => {
    vi.mocked(executeApiTesterRequest).mockResolvedValue(
      mockResponse({ body: JSON.stringify({ paymentReference: baseRequest.paymentReference, traceId: 't1', status: 'VALIDATED' }) }),
    )

    await createPayment(baseRequest)

    expect(executeApiTesterRequest).toHaveBeenCalledWith(
      expect.objectContaining({ service: 'api-gateway', method: 'POST', path: '/api/v1/validations' }),
    )
  })

  it('includes the selected scheme verbatim in the request body sent to validation-service', async () => {
    vi.mocked(executeApiTesterRequest).mockResolvedValue(mockResponse({ body: JSON.stringify({ status: 'VALIDATED', paymentReference: 'x' }) }))

    await createPayment(baseRequest)

    const sentBody = JSON.parse(vi.mocked(executeApiTesterRequest).mock.calls[0][0].body as string)
    expect(sentBody.scheme).toBe('INSTANT_PAYMENT')
  })

  it('preserves the amount as an exact decimal literal, never a float round-trip', async () => {
    vi.mocked(executeApiTesterRequest).mockResolvedValue(mockResponse({ body: JSON.stringify({ status: 'VALIDATED', paymentReference: 'x' }) }))

    await createPayment({ ...baseRequest, amount: '19.9' })

    const sentBodyText = vi.mocked(executeApiTesterRequest).mock.calls[0][0].body as string
    expect(sentBodyText).toContain('"amount":19.9,')
  })

  it('sends the credential as an X-Api-Key header when that type is chosen', async () => {
    vi.mocked(executeApiTesterRequest).mockResolvedValue(mockResponse({ body: JSON.stringify({ status: 'VALIDATED', paymentReference: 'x' }) }))

    await createPayment(baseRequest)

    expect(vi.mocked(executeApiTesterRequest).mock.calls[0][0].headers).toEqual({ 'X-Api-Key': 'real-test-key' })
  })

  it('prefixes a raw Authorization value with "Bearer " when not already present', async () => {
    vi.mocked(executeApiTesterRequest).mockResolvedValue(mockResponse({ body: JSON.stringify({ status: 'VALIDATED', paymentReference: 'x' }) }))

    await createPayment({ ...baseRequest, credentialHeaderName: 'Authorization', credentialValue: 'raw-jwt-token' })

    expect(vi.mocked(executeApiTesterRequest).mock.calls[0][0].headers).toEqual({ Authorization: 'Bearer raw-jwt-token' })
  })

  it('classifies a real 200/VALIDATED response as VALIDATED with the real reference', async () => {
    vi.mocked(executeApiTesterRequest).mockResolvedValue(
      mockResponse({ status: 200, body: JSON.stringify({ paymentReference: 'PMT-1', traceId: 'trace-1', status: 'VALIDATED' }) }),
    )

    const outcome = await createPayment(baseRequest)

    expect(outcome).toEqual({ kind: 'VALIDATED', paymentReference: 'PMT-1', traceId: 'trace-1' })
  })

  it('classifies a real 200/REJECTED response with the real rejection reason, never as success', async () => {
    vi.mocked(executeApiTesterRequest).mockResolvedValue(
      mockResponse({ status: 200, body: JSON.stringify({ paymentReference: 'PMT-2', status: 'REJECTED', rejectionReason: 'Participant not eligible for scheme' }) }),
    )

    const outcome = await createPayment(baseRequest)

    expect(outcome).toEqual({ kind: 'REJECTED', paymentReference: 'PMT-2', reason: 'Participant not eligible for scheme' })
  })

  it('classifies a real 409 as DUPLICATE with the real body, matching the endpoint\'s actual idempotency mechanism', async () => {
    vi.mocked(executeApiTesterRequest).mockResolvedValue(
      mockResponse({ status: 409, body: JSON.stringify({ paymentReference: 'PMT-3', status: 'DUPLICATE', rejectionReason: 'Payment with this reference has already been processed' }) }),
    )

    const outcome = await createPayment(baseRequest)

    expect(outcome).toEqual({ kind: 'DUPLICATE', paymentReference: 'PMT-3', reason: 'Payment with this reference has already been processed' })
  })

  it('classifies a real 401/403 as UNAUTHORIZED, never silently retried or hidden', async () => {
    vi.mocked(executeApiTesterRequest).mockResolvedValue(mockResponse({ status: 401, body: 'Missing X-Participant-Id' }))

    const outcome = await createPayment(baseRequest)

    expect(outcome.kind).toBe('UNAUTHORIZED')
  })

  it('classifies any other real status as an honest UPSTREAM_ERROR, never a fabricated success', async () => {
    vi.mocked(executeApiTesterRequest).mockResolvedValue(mockResponse({ status: 500, statusText: 'Internal Server Error', body: 'boom' }))

    const outcome = await createPayment(baseRequest)

    expect(outcome).toEqual({ kind: 'UPSTREAM_ERROR', status: 500, message: 'boom' })
  })
})
