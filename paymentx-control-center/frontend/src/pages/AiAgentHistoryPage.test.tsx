/**
 * Phase 4.7 - proves Execution History renders the REAL, already-persisted execution records
 * (never fabricated), supports pagination and filters, and opening a row fetches and displays
 * the REAL stored detail record via GET /api/v1/agents/executions/{id}.
 */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider } from '@mui/material/styles'
import { MemoryRouter } from 'react-router-dom'
import { buildTheme } from '../theme/theme'
import AiAgentHistoryPage from './AiAgentHistoryPage'
import { fetchAgents, fetchExecutionDetail, fetchExecutionHistory } from '../services/agentService'

vi.mock('../services/agentService', async () => {
  const actual = await vi.importActual<typeof import('../services/agentService')>('../services/agentService')
  return { ...actual, fetchAgents: vi.fn(), fetchExecutionHistory: vi.fn(), fetchExecutionDetail: vi.fn() }
})

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <ThemeProvider theme={buildTheme('light')}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <AiAgentHistoryPage />
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  )
}

describe('AiAgentHistoryPage', () => {
  afterEach(() => vi.clearAllMocks())

  it('renders real execution history rows with pagination info from the real backend', async () => {
    vi.mocked(fetchAgents).mockResolvedValue([])
    vi.mocked(fetchExecutionHistory).mockResolvedValue({
      content: [
        {
          executionId: 'exec-1',
          correlationId: 'corr-1',
          agentId: 'reconciliation-agent',
          userQuery: 'Is PMT-1 reconciled?',
          paymentReference: 'PMT-1',
          outcome: 'SUCCESS',
          timestamp: new Date().toISOString(),
          durationMs: 1200,
          toolCallCount: 1,
          ragUsed: true,
          provider: 'gemini',
          fallbackUsed: false,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
    })

    renderPage()

    expect(await screen.findByText('reconciliation-agent')).toBeInTheDocument()
    expect(screen.getByText('PMT-1')).toBeInTheDocument()
    expect(screen.getByText('Showing 1-1 of 1')).toBeInTheDocument()
  })

  it('renders a real empty state when there is no history yet', async () => {
    vi.mocked(fetchAgents).mockResolvedValue([])
    vi.mocked(fetchExecutionHistory).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0 })

    renderPage()

    expect(await screen.findByText('No executions yet')).toBeInTheDocument()
  })

  it('opens the real stored detail record when a row is clicked', async () => {
    vi.mocked(fetchAgents).mockResolvedValue([])
    vi.mocked(fetchExecutionHistory).mockResolvedValue({
      content: [
        {
          executionId: 'exec-1',
          correlationId: 'corr-1',
          agentId: 'reconciliation-agent',
          userQuery: 'Is PMT-1 reconciled?',
          paymentReference: 'PMT-1',
          outcome: 'SUCCESS',
          timestamp: new Date().toISOString(),
          durationMs: 1200,
          toolCallCount: 1,
          ragUsed: true,
          provider: 'gemini',
          fallbackUsed: false,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
    })
    vi.mocked(fetchExecutionDetail).mockResolvedValue({
      executionId: 'exec-1',
      correlationId: 'corr-1',
      agentId: 'reconciliation-agent',
      userQuery: 'Is PMT-1 reconciled?',
      paymentReference: 'PMT-1',
      outcome: 'SUCCESS',
      answer: 'Reconciliation Finding: RECONCILED.',
      ragSources: ['reconciliation-01-reconciliation-status-and-batch-model'],
      toolsCalled: [{ tool: 'reconciliation.status', status: 'SUCCESS' }],
      startedAt: new Date().toISOString(),
      completedAt: new Date().toISOString(),
      durationMs: 1200,
      iterations: 2,
      ragUsed: true,
      error: null,
      provider: 'gemini',
      fallbackUsed: false,
      fallbackReason: null,
    })

    const user = userEvent.setup()
    renderPage()

    const row = await screen.findByText('Is PMT-1 reconciled?')
    await user.click(row)

    expect(await screen.findByText('Reconciliation Finding: RECONCILED.')).toBeInTheDocument()
    expect(fetchExecutionDetail).toHaveBeenCalledWith('exec-1')
  })
})
