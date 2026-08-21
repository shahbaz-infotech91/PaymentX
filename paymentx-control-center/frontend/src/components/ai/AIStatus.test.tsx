/**
 * ENGLISH: Proves AIStatus never fabricates a status - this phase's
 * Step 5 hard rule. What it verifies: loading -> "Connecting…",
 * useAiHealth erroring/unreachable -> "AI service is not configured
 * yet" wording, a real NOT_CONFIGURED backend response ->
 * "Not Configured", a real NOT_READY backend response ->
 * "AI Service Unavailable". hooks/useAiHealth is mocked here (not
 * axios) since this component's only job is to render whatever that
 * hook reports - the hook's own real HTTP behavior is exercised by
 * services/aiService.test.ts instead.
 *
 * HINGLISH: AIStatus kabhi ek status fabricate nahi karta - is phase
 * ka Step 5 hard rule - ye prove karta hai. Ye kya verify karta hai:
 * loading -> "Connecting…", useAiHealth error/unreachable ho ->
 * "AI service is not configured yet" wording, ek real NOT_CONFIGURED
 * backend response -> "Not Configured", ek real NOT_READY backend
 * response -> "AI Service Unavailable". Yahan hooks/useAiHealth mock
 * kiya gaya hai (axios nahi) kyunki is component ka sirf ek hi kaam hai
 * - jo bhi wo hook report kare use render karna - hook ka apna real
 * HTTP behavior services/aiService.test.ts se exercise hota hai.
 */
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { buildTheme } from '../../theme/theme'
import { AIStatus } from './AIStatus'
import { useAiHealth } from '../../hooks/useAiHealth'

vi.mock('../../hooks/useAiHealth')

function renderWithTheme() {
  return render(
    <ThemeProvider theme={buildTheme('light')}>
      <AIStatus />
    </ThemeProvider>,
  )
}

describe('AIStatus', () => {
  it('shows Connecting while loading', () => {
    vi.mocked(useAiHealth).mockReturnValue({ data: undefined, isLoading: true, isError: false } as never)
    renderWithTheme()
    expect(screen.getByText('Connecting…')).toBeInTheDocument()
  })

  it('shows the real "not configured yet" wording when the health call fails', () => {
    vi.mocked(useAiHealth).mockReturnValue({ data: undefined, isLoading: false, isError: true } as never)
    renderWithTheme()
    expect(screen.getByText('AI service is not configured yet')).toBeInTheDocument()
  })

  it('shows Not Configured for a real NOT_CONFIGURED backend response', () => {
    vi.mocked(useAiHealth).mockReturnValue({
      data: { status: 'NOT_CONFIGURED', components: {}, timestamp: new Date().toISOString() },
      isLoading: false,
      isError: false,
    } as never)
    renderWithTheme()
    expect(screen.getByText('Not Configured')).toBeInTheDocument()
  })

  it('shows AI Service Unavailable for a real NOT_READY backend response', () => {
    vi.mocked(useAiHealth).mockReturnValue({
      data: { status: 'NOT_READY', components: {}, timestamp: new Date().toISOString() },
      isLoading: false,
      isError: false,
    } as never)
    renderWithTheme()
    expect(screen.getByText('AI Service Unavailable')).toBeInTheDocument()
  })
})
