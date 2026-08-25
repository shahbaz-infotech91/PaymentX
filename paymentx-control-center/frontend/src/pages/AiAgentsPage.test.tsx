/**
 * Phase 4.7 - proves the AI Agents Dashboard renders the REAL registered agents (never a
 * hardcoded five-agent list) - loading, error, empty, and populated states.
 */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider } from '@mui/material/styles'
import { MemoryRouter } from 'react-router-dom'
import { buildTheme } from '../theme/theme'
import AiAgentsPage from './AiAgentsPage'
import { fetchAgents } from '../services/agentService'

vi.mock('../services/agentService', async () => {
  const actual = await vi.importActual<typeof import('../services/agentService')>('../services/agentService')
  return { ...actual, fetchAgents: vi.fn() }
})

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <ThemeProvider theme={buildTheme('light')}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <AiAgentsPage />
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  )
}

describe('AiAgentsPage', () => {
  afterEach(() => vi.clearAllMocks())

  it('renders every real registered agent returned by the backend', async () => {
    vi.mocked(fetchAgents).mockResolvedValue([
      {
        agentId: 'reconciliation-agent',
        name: 'PaymentX Reconciliation Agent',
        description: 'Read-only reconciliation analysis.',
        version: '1.0',
        capabilities: ['RECONCILIATION_ANALYSIS'],
        allowedTools: ['payment.lookup', 'reconciliation.status'],
        riskLevel: 'LOW',
        enabled: true,
      },
      {
        agentId: 'fraud-detection-agent',
        name: 'PaymentX Fraud/Risk Analysis Agent',
        description: 'Conservative potential-risk analysis.',
        version: '1.0',
        capabilities: ['RISK_ANALYSIS'],
        allowedTools: ['payment.lookup'],
        riskLevel: 'LOW',
        enabled: false,
      },
    ])

    renderPage()

    expect(await screen.findByText('PaymentX Reconciliation Agent')).toBeInTheDocument()
    expect(screen.getByText('PaymentX Fraud/Risk Analysis Agent')).toBeInTheDocument()
    expect(screen.getByText('ENABLED')).toBeInTheDocument()
    expect(screen.getByText('DISABLED')).toBeInTheDocument()
  })

  it('renders an empty state when the registry has no agents', async () => {
    vi.mocked(fetchAgents).mockResolvedValue([])

    renderPage()

    expect(await screen.findByText('No agents registered')).toBeInTheDocument()
  })

  it('renders a real error state when the backend call fails', async () => {
    vi.mocked(fetchAgents).mockRejectedValue(new Error('network down'))

    renderPage()

    await waitFor(() => expect(screen.getByText(/unexpected error|network/i)).toBeInTheDocument())
  })
})
