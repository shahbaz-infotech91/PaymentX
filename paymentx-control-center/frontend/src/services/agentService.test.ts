/**
 * Phase 4.7 - proves classifyAgentError's real mapping from a backend/network failure to an
 * honest, enterprise-friendly message, mirroring aiService.test.ts's own classifyAiError coverage.
 */
import { describe, expect, it } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { classifyAgentError } from './agentService'

function axiosErrorWith(status: number, errorCode?: string, message = 'backend message'): AxiosError {
  return new AxiosError('Request failed', String(status), undefined, undefined, {
    status,
    statusText: 'error',
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
    data: errorCode ? { error: { errorCode, message, path: '/api/v1/agents/execute' } } : undefined,
  })
}

describe('classifyAgentError', () => {
  it('classifies AI_NOT_CONFIGURED with an honest, specific message', () => {
    expect(classifyAgentError(axiosErrorWith(503, 'AI_NOT_CONFIGURED'))).toContain('not configured')
  })

  it('classifies VALIDATION_ERROR using the real backend message', () => {
    expect(classifyAgentError(axiosErrorWith(400, 'VALIDATION_ERROR', 'userQuery must not be blank'))).toBe(
      'userQuery must not be blank',
    )
  })

  it('classifies EXECUTION_NOT_FOUND with an honest, specific message', () => {
    expect(classifyAgentError(axiosErrorWith(502, 'EXECUTION_NOT_FOUND'))).toContain('No execution was found')
  })

  it('classifies a real timeout with a retry-oriented message', () => {
    const error = new AxiosError('timeout of 95000ms exceeded', 'ECONNABORTED')
    expect(classifyAgentError(error)).toContain('took too long')
  })

  it('falls back to the real backend message for an unrecognized errorCode', () => {
    expect(classifyAgentError(axiosErrorWith(500, 'INTERNAL_ERROR', 'unexpected failure'))).toBe('unexpected failure')
  })
})
