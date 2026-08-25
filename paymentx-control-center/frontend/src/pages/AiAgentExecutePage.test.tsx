/**
 * Phase 4.7 - proves the real Execute Agent flow: dynamic agent selection driven by the real
 * registry, the execute button stays disabled until a valid agent+question are present, a
 * pending execution disables the button (no duplicate submit on a double-click), and a real
 * success/failure response is rendered honestly - never a frontend-fabricated answer.
 */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider } from '@mui/material/styles'
import { MemoryRouter } from 'react-router-dom'
import { buildTheme } from '../theme/theme'
import AiAgentExecutePage from './AiAgentExecutePage'
import { executeAgent, fetchAgents } from '../services/agentService'

vi.mock('../services/agentService', async () => {
  const actual = await vi.importActual<typeof import('../services/agentService')>('../services/agentService')
  return { ...actual, fetchAgents: vi.fn(), executeAgent: vi.fn() }
})

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <ThemeProvider theme={buildTheme('light')}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <AiAgentExecutePage />
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  )
}

const REAL_AGENTS = [
  {
    agentId: 'reconciliation-agent',
    name: 'PaymentX Reconciliation Agent',
    description: 'desc',
    version: '1.0',
    capabilities: ['RECONCILIATION_ANALYSIS'],
    allowedTools: ['payment.lookup', 'reconciliation.status'],
    riskLevel: 'LOW',
    enabled: true,
  },
]

describe('AiAgentExecutePage', () => {
  afterEach(() => vi.clearAllMocks())

  it('keeps Execute Agent disabled until a real agent and a question are both present', async () => {
    vi.mocked(fetchAgents).mockResolvedValue(REAL_AGENTS)
    const user = userEvent.setup()
    renderPage()

    const executeButton = await screen.findByRole('button', { name: 'Execute Agent' })
    expect(executeButton).toBeDisabled()

    await user.click(screen.getByLabelText('Agent'))
    await user.click(await screen.findByRole('option', { name: /PaymentX Reconciliation Agent/ }))
    expect(executeButton).toBeDisabled()

    await user.type(screen.getByRole('textbox', { name: 'Question' }), 'Is PMT-1 reconciled?')
    expect(executeButton).toBeEnabled()
  })

  it('renders the REAL backend response (answer, status, executionId, tools, sources) - never a fabricated one', async () => {
    vi.mocked(fetchAgents).mockResolvedValue(REAL_AGENTS)
    vi.mocked(executeAgent).mockResolvedValue({
      executionId: 'exec-real-1',
      correlationId: 'corr-real-1',
      agentId: 'reconciliation-agent',
      userQuery: 'Is PMT-1 reconciled?',
      paymentReference: 'PMT-1',
      status: 'SUCCESS',
      answer: 'Reconciliation Finding: RECONCILED.',
      sources: [{ source: 'reconciliation-01-reconciliation-status-and-batch-model', score: 0.9 }],
      toolsCalled: [{ tool: 'reconciliation.status', status: 'SUCCESS', result: {} }],
      executionMetadata: { iterations: 2, toolCallCount: 1, ragUsed: true, totalLatencyMs: 1200 },
      startedAt: new Date().toISOString(),
      completedAt: new Date().toISOString(),
      durationMs: 1200,
      error: null,
      provider: 'gemini',
      fallbackUsed: false,
      fallbackReason: null,
    })

    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByLabelText('Agent'))
    await user.click(await screen.findByRole('option', { name: /PaymentX Reconciliation Agent/ }))
    await user.type(screen.getByRole('textbox', { name: 'Question' }), 'Is PMT-1 reconciled?')
    await user.click(screen.getByRole('button', { name: 'Execute Agent' }))

    expect(await screen.findByText('Reconciliation Finding: RECONCILED.')).toBeInTheDocument()
    expect(screen.getByText('exec-real-1')).toBeInTheDocument()
    expect(screen.getByText('corr-real-1')).toBeInTheDocument()
    expect(screen.getByText('reconciliation.status')).toBeInTheDocument()
    expect(screen.getByText('reconciliation-01-reconciliation-status-and-batch-model')).toBeInTheDocument()
    expect(executeAgent).toHaveBeenCalledWith({
      agentId: 'reconciliation-agent',
      userQuery: 'Is PMT-1 reconciled?',
      paymentReference: undefined,
    })
  })

  it('disables Execute Agent while a request is in flight, preventing a duplicate submit on double-click', async () => {
    vi.mocked(fetchAgents).mockResolvedValue(REAL_AGENTS)
    let resolveExecute: (value: unknown) => void = () => {}
    vi.mocked(executeAgent).mockReturnValue(new Promise((resolve) => { resolveExecute = resolve }) as never)

    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByLabelText('Agent'))
    await user.click(await screen.findByRole('option', { name: /PaymentX Reconciliation Agent/ }))
    await user.type(screen.getByRole('textbox', { name: 'Question' }), 'Is PMT-1 reconciled?')

    const executeButton = screen.getByRole('button', { name: 'Execute Agent' })
    await user.click(executeButton)

    // The button is now genuinely unclickable (MUI applies pointer-events: none while disabled) -
    // userEvent's realistic pointer simulation refuses a click here, which IS the proof a
    // double-click cannot fire a second request; asserting that refusal directly is stronger than
    // attempting a second click and only checking the call count afterward.
    await expect(user.click(executeButton)).rejects.toThrow(/pointer-events: none/)
    expect(executeAgent).toHaveBeenCalledTimes(1)
    resolveExecute({
      executionId: 'exec-1',
      correlationId: 'corr-1',
      agentId: 'reconciliation-agent',
      userQuery: 'q',
      paymentReference: null,
      status: 'SUCCESS',
      answer: 'ok',
      sources: [],
      toolsCalled: [],
      executionMetadata: { iterations: 1, toolCallCount: 0, ragUsed: false, totalLatencyMs: 10 },
      startedAt: new Date().toISOString(),
      completedAt: new Date().toISOString(),
      durationMs: 10,
      error: null,
    })
  })

  it('renders a real failed execution honestly, not as a fabricated success', async () => {
    vi.mocked(fetchAgents).mockResolvedValue(REAL_AGENTS)
    vi.mocked(executeAgent).mockRejectedValue(
      Object.assign(new Error('Request failed with status code 503'), {
        isAxiosError: true,
        response: { status: 503, data: { error: { errorCode: 'AI_NOT_CONFIGURED', message: 'not configured', path: '/api/v1/agents/execute' } } },
      }),
    )

    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByLabelText('Agent'))
    await user.click(await screen.findByRole('option', { name: /PaymentX Reconciliation Agent/ }))
    await user.type(screen.getByRole('textbox', { name: 'Question' }), 'Is PMT-1 reconciled?')
    await user.click(screen.getByRole('button', { name: 'Execute Agent' }))

    expect(await screen.findByText('Execution failed')).toBeInTheDocument()
    expect(screen.queryByText(/RECONCILED/)).not.toBeInTheDocument()
  })
})
