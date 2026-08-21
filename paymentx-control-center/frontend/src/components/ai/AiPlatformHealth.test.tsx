/**
 * ENGLISH: Phase 3.10.3 - proves AiPlatformHealth renders the real, per-component health breakdown
 * useAiHealth() already returns (never a second, fabricated health source), and degrades gracefully
 * (loading/error) exactly like AIStatus.tsx already does for the same underlying hook.
 *
 * HINGLISH: Phase 3.10.3 - AiPlatformHealth real, per-component health breakdown render karta hai jo
 * useAiHealth() already return karta hai (koi doosra, fabricated health source kabhi nahi), aur
 * loading/error me gracefully degrade hota hai, exactly AIStatus.tsx ki tarah usi underlying hook ke
 * liye.
 */
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { buildTheme } from '../../theme/theme'
import { AiPlatformHealth } from './AiPlatformHealth'
import { useAiHealth } from '../../hooks/useAiHealth'

vi.mock('../../hooks/useAiHealth')

function renderWithTheme() {
  return render(
    <ThemeProvider theme={buildTheme('light')}>
      <AiPlatformHealth />
    </ThemeProvider>,
  )
}

describe('AiPlatformHealth', () => {
  it('shows a loading state while the health check is in flight', () => {
    vi.mocked(useAiHealth).mockReturnValue({ data: undefined, isLoading: true, isError: false, error: null, refetch: vi.fn() } as never)
    renderWithTheme()
    expect(screen.getByText('Checking AI Platform health…')).toBeInTheDocument()
  })

  it('shows an error state with retry when the health endpoint is unreachable', () => {
    const refetch = vi.fn()
    vi.mocked(useAiHealth).mockReturnValue({ data: undefined, isLoading: false, isError: true, error: new Error('network error'), refetch } as never)
    renderWithTheme()
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument()
  })

  it('renders all 7 real components from the actual backend health breakdown', () => {
    vi.mocked(useAiHealth).mockReturnValue({
      data: {
        status: 'READY',
        components: {
          chatInterface: 'READY',
          promptService: 'READY',
          llmService: 'READY',
          embeddingService: 'READY',
          ragService: 'READY',
          mcpGateway: 'READY',
          agentOrchestrator: 'READY',
        },
        timestamp: new Date().toISOString(),
      },
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never)

    renderWithTheme()

    expect(screen.getByText('Chat Interface')).toBeInTheDocument()
    expect(screen.getByText('Prompt')).toBeInTheDocument()
    expect(screen.getByText('LLM')).toBeInTheDocument()
    expect(screen.getByText('Embedding')).toBeInTheDocument()
    expect(screen.getByText('RAG')).toBeInTheDocument()
    expect(screen.getByText('MCP Gateway')).toBeInTheDocument()
    expect(screen.getByText('Agent Orchestrator')).toBeInTheDocument()
    expect(screen.getAllByText('Healthy')).toHaveLength(7)
  })

  it('renders NOT_READY components as Down, never fabricating Healthy', () => {
    vi.mocked(useAiHealth).mockReturnValue({
      data: {
        status: 'NOT_READY',
        components: { llmService: 'NOT_READY', ragService: 'NOT_IMPLEMENTED' },
        timestamp: new Date().toISOString(),
      },
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never)

    renderWithTheme()

    expect(screen.getByText('Down')).toBeInTheDocument()
    expect(screen.getByText('Unknown')).toBeInTheDocument()
    expect(screen.queryByText('Healthy')).not.toBeInTheDocument()
  })
})
