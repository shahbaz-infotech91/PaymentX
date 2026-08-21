/**
 * ENGLISH: Proves classifyAiError's real mapping from a backend/network
 * failure to one honest AiErrorCategory - the mechanism this phase's
 * Step 12 ("clearly distinguish AI not configured / unavailable / auth
 * failure / rate limit / network error / server error / invalid
 * request / timeout") depends on. What it verifies: each real errorCode
 * the backend actually sends (AI_NOT_CONFIGURED, AI_SERVICE_NOT_READY,
 * VALIDATION_ERROR, UNAUTHORIZED) and each real HTTP status
 * (401/403/429/500) maps to the correct category with an
 * enterprise-friendly, non-raw message.
 *
 * HINGLISH: classifyAiError ka real mapping prove karta hai - ek
 * backend/network failure se ek honest AiErrorCategory tak - ye wahi
 * mechanism hai jispar is phase ka Step 12 ("AI not configured /
 * unavailable / auth failure / rate limit / network error / server
 * error / invalid request / timeout ko clearly distinguish karo")
 * depend karta hai. Ye kya verify karta hai: har real errorCode jo
 * backend actually bhejta hai (AI_NOT_CONFIGURED, AI_SERVICE_NOT_READY,
 * VALIDATION_ERROR, UNAUTHORIZED) aur har real HTTP status
 * (401/403/429/500) sahi category par map hota hai, ek
 * enterprise-friendly, non-raw message ke saath.
 */
import { describe, expect, it } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { classifyAiError } from './aiService'

function axiosErrorWith(status: number, errorCode?: string): AxiosError {
  const error = new AxiosError('Request failed', String(status), undefined, undefined, {
    status,
    statusText: 'error',
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
    data: errorCode ? { error: { errorCode, message: 'backend message', path: '/api/v1/ai/chat' } } : undefined,
  })
  return error
}

describe('classifyAiError', () => {
  it('classifies AI_NOT_CONFIGURED as NOT_CONFIGURED', () => {
    expect(classifyAiError(axiosErrorWith(503, 'AI_NOT_CONFIGURED')).category).toBe('NOT_CONFIGURED')
  })

  it('classifies AI_SERVICE_NOT_READY as NOT_READY', () => {
    expect(classifyAiError(axiosErrorWith(503, 'AI_SERVICE_NOT_READY')).category).toBe('NOT_READY')
  })

  it('classifies a 401 as UNAUTHORIZED', () => {
    expect(classifyAiError(axiosErrorWith(401, 'UNAUTHORIZED')).category).toBe('UNAUTHORIZED')
  })

  it('classifies a 403 as FORBIDDEN', () => {
    expect(classifyAiError(axiosErrorWith(403)).category).toBe('FORBIDDEN')
  })

  it('classifies a 429 as RATE_LIMITED', () => {
    expect(classifyAiError(axiosErrorWith(429)).category).toBe('RATE_LIMITED')
  })

  it('classifies a 400 VALIDATION_ERROR as VALIDATION_ERROR with the real backend message', () => {
    const classified = classifyAiError(axiosErrorWith(400, 'VALIDATION_ERROR'))
    expect(classified.category).toBe('VALIDATION_ERROR')
    expect(classified.message).toBe('backend message')
  })

  it('classifies a 500 as SERVER_ERROR', () => {
    expect(classifyAiError(axiosErrorWith(500)).category).toBe('SERVER_ERROR')
  })

  it('classifies a non-Axios error as UNKNOWN without throwing', () => {
    const classified = classifyAiError(new Error('boom'))
    expect(classified.category).toBe('UNKNOWN')
    expect(classified.message.length).toBeGreaterThan(0)
  })
})
