/**
 * ENGLISH: Phase 3.10.3 - proves the AI Metrics page renders every real section (LLM/RAG/MCP/Agent/
 * Embedding/Vector/Prompt) from real (mocked) backend data, degrades gracefully (loading, Prometheus
 * unavailable, missing metric, partial failure), and never fabricates a zero for a metric that genuinely
 * has no data. useAiPrometheusMetrics/useAiHealth are mocked here (not axios) - matching AIStatus.test.tsx's
 * own "mock the hook, not the transport" convention - since this page's only job is to render whatever
 * those hooks report.
 *
 * HINGLISH: Phase 3.10.3 - AI Metrics page har real section (LLM/RAG/MCP/Agent/Embedding/Vector/Prompt)
 * real (mocked) backend data se render karta hai, gracefully degrade hota hai (loading, Prometheus
 * unavailable, missing metric, partial failure), aur kabhi ek genuinely no-data metric ke liye zero
 * fabricate nahi karta.
 */
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { buildTheme } from '../theme/theme'
import AiMetricsPage from './AiMetricsPage'
import { useAiPrometheusMetrics } from '../hooks/usePrometheusMetrics'
import { useAiHealth } from '../hooks/useAiHealth'
import type { PrometheusQueryResult } from '../services/prometheusService'

vi.mock('../hooks/usePrometheusMetrics')
vi.mock('../hooks/useAiHealth')

function mockHealthReady() {
  vi.mocked(useAiHealth).mockReturnValue({
    data: { status: 'READY', components: { llmService: 'READY' }, timestamp: new Date().toISOString() },
    isLoading: false,
    isError: false,
    error: null,
    refetch: vi.fn(),
  } as never)
}

function renderPage() {
  return render(
    <ThemeProvider theme={buildTheme('light')}>
      <AiMetricsPage />
    </ThemeProvider>,
  )
}

/** Every AI slug this page queries, all reported success with no samples by default - individual tests
 * override specific slugs to exercise real data / failure / missing-metric states. */
function fullResultSet(overrides: Record<string, PrometheusQueryResult> = {}): PrometheusQueryResult[] {
  const slugs = [
    'ai-llm-request-rate', 'ai-llm-success-rate', 'ai-llm-failure-rate', 'ai-llm-latency-p95',
    'ai-rag-query-rate', 'ai-rag-success-rate', 'ai-rag-insufficient-context-rate', 'ai-rag-failure-rate', 'ai-rag-latency-p95',
    'ai-mcp-tool-call-rate', 'ai-mcp-tool-success-rate', 'ai-mcp-tool-failure-rate', 'ai-mcp-tool-denied-rate', 'ai-mcp-tool-latency-p95',
    'ai-agent-execution-rate', 'ai-agent-success-rate', 'ai-agent-failure-rate', 'ai-agent-latency-p95',
    'ai-embedding-request-rate', 'ai-embedding-latency-p95',
    'ai-vector-search-rate', 'ai-vector-latency-p95',
    'ai-prompt-request-rate', 'ai-prompt-failure-rate',
  ]
  return slugs.map((slug) => overrides[slug] ?? { querySlug: slug, promQl: 'n/a', success: true, errorMessage: null, samples: [] })
}

describe('AiMetricsPage', () => {
  it('shows a loading state while metrics are being fetched', () => {
    mockHealthReady()
    vi.mocked(useAiPrometheusMetrics).mockReturnValue({ data: undefined, isLoading: true, isError: false, error: null, refetch: vi.fn() } as never)

    renderPage()

    expect(screen.getByText('Querying Prometheus for AI Platform metrics…')).toBeInTheDocument()
  })

  it('shows an honest error state when Prometheus is unavailable, with a retry action', () => {
    mockHealthReady()
    const refetch = vi.fn()
    vi.mocked(useAiPrometheusMetrics).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error('unreachable'),
      refetch,
    } as never)

    renderPage()

    // toApiError's own real classification of the error text is covered by its own test suite - this
    // page only needs to prove it renders SOME real ErrorState (never silently blank, never a crash)
    // and that retry actually calls back into the query.
    expect(screen.getByRole('alert')).toBeInTheDocument()
    screen.getByRole('button', { name: /retry/i }).click()
    expect(refetch).toHaveBeenCalledTimes(1)
  })

  it('renders every real AI Platform section with real data', () => {
    mockHealthReady()
    vi.mocked(useAiPrometheusMetrics).mockReturnValue({
      data: fullResultSet({
        'ai-llm-request-rate': { querySlug: 'ai-llm-request-rate', promQl: 'n/a', success: true, errorMessage: null, samples: [{ labels: { provider: 'anthropic' }, value: 1.5, timestampEpochSeconds: 0 }] },
      }),
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never)

    renderPage()

    expect(screen.getByRole('heading', { name: 'LLM' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'RAG' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'MCP' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Agent' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Embedding' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Vector' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Prompt' })).toBeInTheDocument()
    expect(screen.getByText('1.50/s')).toBeInTheDocument()
  })

  it('shows an honest "No data" state for a metric with zero real samples, never a fabricated zero', () => {
    mockHealthReady()
    vi.mocked(useAiPrometheusMetrics).mockReturnValue({
      data: fullResultSet(),
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never)

    renderPage()

    expect(screen.queryByText('0.00/s')).not.toBeInTheDocument()
    expect(screen.getAllByText('No data').length).toBeGreaterThan(0)
  })

  it('shows one card as Unavailable on a partial query failure, without affecting the other real cards', () => {
    mockHealthReady()
    vi.mocked(useAiPrometheusMetrics).mockReturnValue({
      data: fullResultSet({
        'ai-rag-latency-p95': { querySlug: 'ai-rag-latency-p95', promQl: 'n/a', success: false, errorMessage: 'Prometheus returned a non-success status', samples: [] },
        'ai-rag-query-rate': { querySlug: 'ai-rag-query-rate', promQl: 'n/a', success: true, errorMessage: null, samples: [{ labels: {}, value: 2, timestampEpochSeconds: 0 }] },
      }),
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never)

    renderPage()

    expect(screen.getAllByText('Unavailable').length).toBeGreaterThan(0)
    // the sibling RAG card with real data still renders correctly despite the other RAG card failing
    expect(screen.getByText('2.00/s')).toBeInTheDocument()
  })

  it('shows Unavailable for a metric the backend did not return at all (missing metric)', () => {
    mockHealthReady()
    const withoutOneSlug = fullResultSet().filter((r) => r.querySlug !== 'ai-vector-search-rate')
    vi.mocked(useAiPrometheusMetrics).mockReturnValue({
      data: withoutOneSlug,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never)

    renderPage()

    expect(screen.getAllByText('Unavailable').length).toBeGreaterThan(0)
  })

  // ================================================================
  // Phase 3.10.3 NaN-crash fix - the confirmed root cause: a sparse histogram_quantile() normalizes to
  // a `null` sample value (real backend behavior since this fix), and any residual non-finite number
  // must be handled by the frontend's own independent defensive layer too. None of these may crash the
  // page - that IS the bug this suite proves fixed.
  // ================================================================

  it('renders "No data" (never a crash) when a latency-p95 sample value is null - the real, confirmed root cause', () => {
    mockHealthReady()
    vi.mocked(useAiPrometheusMetrics).mockReturnValue({
      data: fullResultSet({
        'ai-rag-latency-p95': { querySlug: 'ai-rag-latency-p95', promQl: 'n/a', success: true, errorMessage: null, samples: [{ labels: {}, value: null, timestampEpochSeconds: 0 }] },
      }),
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never)

    expect(() => renderPage()).not.toThrow()
    expect(screen.queryByText(/^This page hit an unexpected error$/)).not.toBeInTheDocument()
    expect(screen.getAllByText('No data').length).toBeGreaterThan(0)
  })

  it('renders "No data" (never a crash) if a raw NaN number slips through independently of backend normalization', () => {
    mockHealthReady()
    vi.mocked(useAiPrometheusMetrics).mockReturnValue({
      data: fullResultSet({
        'ai-mcp-tool-latency-p95': { querySlug: 'ai-mcp-tool-latency-p95', promQl: 'n/a', success: true, errorMessage: null, samples: [{ labels: {}, value: Number.NaN, timestampEpochSeconds: 0 }] },
      }),
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never)

    expect(() => renderPage()).not.toThrow()
    expect(screen.getAllByText('No data').length).toBeGreaterThan(0)
  })

  it('renders "No data" (never a crash) for an Infinity sample value', () => {
    mockHealthReady()
    vi.mocked(useAiPrometheusMetrics).mockReturnValue({
      data: fullResultSet({
        'ai-embedding-latency-p95': { querySlug: 'ai-embedding-latency-p95', promQl: 'n/a', success: true, errorMessage: null, samples: [{ labels: {}, value: Number.POSITIVE_INFINITY, timestampEpochSeconds: 0 }] },
      }),
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never)

    expect(() => renderPage()).not.toThrow()
    expect(screen.getAllByText('No data').length).toBeGreaterThan(0)
  })

  it('renders a valid latency percentile using the existing, unchanged formatting', () => {
    mockHealthReady()
    vi.mocked(useAiPrometheusMetrics).mockReturnValue({
      data: fullResultSet({
        'ai-agent-latency-p95': { querySlug: 'ai-agent-latency-p95', promQl: 'n/a', success: true, errorMessage: null, samples: [{ labels: {}, value: 9.84, timestampEpochSeconds: 0 }] },
      }),
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never)

    renderPage()

    expect(screen.getByText('9.84 s')).toBeInTheDocument()
  })

  it('renders a real, measured zero as 0 - never as "No data" and never crashing', () => {
    mockHealthReady()
    vi.mocked(useAiPrometheusMetrics).mockReturnValue({
      data: fullResultSet({
        'ai-rag-query-rate': { querySlug: 'ai-rag-query-rate', promQl: 'n/a', success: true, errorMessage: null, samples: [{ labels: {}, value: 0, timestampEpochSeconds: 0 }] },
      }),
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never)

    renderPage()

    expect(screen.getByText('0.000/s')).toBeInTheDocument()
  })
})
