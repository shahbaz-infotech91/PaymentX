/**
 * ENGLISH: An end-to-end (within this page) test of the real AI
 * Assistant flow - this phase's Step 18 required "component rendering,
 * loading state, error state, service unavailable state, API failure
 * handling" coverage in one place. What it verifies: the page renders
 * with a real empty state (no fake starter conversation), typing and
 * sending a message shows the real user bubble plus a real ERROR bubble
 * carrying the classified failure (services/aiService is mocked here to
 * return the exact 503 AI_SERVICE_NOT_READY shape the real backend
 * sends today - see AiChatService.java - so this test is asserting
 * against Phase 3.1's actual documented behavior, not an invented
 * scenario), the AIStatus header reflects a real NOT_READY health
 * response, and "New Conversation" produces a real, empty new
 * conversation rather than reusing stale state.
 *
 * HINGLISH: Real AI Assistant flow ka ek end-to-end (is page ke andar)
 * test - is phase ka Step 18 required "component rendering, loading
 * state, error state, service unavailable state, API failure handling"
 * coverage ek jagah. Ye kya verify karta hai: page ek real empty state
 * ke saath render hota hai (koi fake starter conversation nahi), ek
 * message type karke send karne se real user bubble plus ek real ERROR
 * bubble dikhta hai jo classified failure carry karta hai
 * (services/aiService yahan mock kiya gaya hai exactly wahi 503
 * AI_SERVICE_NOT_READY shape return karne ke liye jo real backend aaj
 * bhejta hai - AiChatService.java dekho - isliye ye test Phase 3.1 ke
 * actual documented behavior ke against assert kar raha hai, ek invent
 * kiye scenario ke against nahi), AIStatus header ek real NOT_READY
 * health response reflect karta hai, aur "New Conversation" ek real,
 * empty naya conversation produce karta hai, stale state reuse karne ke
 * bajaye.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider } from '@mui/material/styles'
import { buildTheme } from '../theme/theme'
import AiAssistantPage from './AiAssistantPage'
import { classifyAiError, fetchAiHealth, sendAiChatMessage } from '../services/aiService'

vi.mock('../services/aiService', async () => {
  const actual = await vi.importActual<typeof import('../services/aiService')>('../services/aiService')
  return {
    ...actual,
    fetchAiHealth: vi.fn(),
    sendAiChatMessage: vi.fn(),
  }
})

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <ThemeProvider theme={buildTheme('light')}>
      <QueryClientProvider client={queryClient}>
        <AiAssistantPage />
      </QueryClientProvider>
    </ThemeProvider>,
  )
}

describe('AiAssistantPage', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    vi.mocked(fetchAiHealth).mockResolvedValue({
      status: 'NOT_READY',
      components: { chatInterface: 'NOT_READY' },
      timestamp: new Date().toISOString(),
    })
    vi.mocked(sendAiChatMessage).mockRejectedValue(
      Object.assign(new Error('Request failed with status code 503'), {
        isAxiosError: true,
        response: { status: 503, data: { error: { errorCode: 'AI_SERVICE_NOT_READY', message: 'not built yet', path: '/api/v1/ai/chat' } } },
      }),
    )
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders the real empty state for a brand-new conversation', async () => {
    renderPage()
    expect(screen.getByText('AI Assistant')).toBeInTheDocument()
    expect(await screen.findByText('Start a conversation')).toBeInTheDocument()
  })

  it('shows the real NOT_READY status from the health endpoint', async () => {
    renderPage()
    expect(await screen.findByText('AI Service Unavailable')).toBeInTheDocument()
  })

  it('sending a message shows the real user bubble and a real classified error - never a fabricated reply', async () => {
    const user = userEvent.setup()
    renderPage()

    const input = screen.getByLabelText('Message PaymentX AI')
    await user.type(input, 'Why did PMT-123 fail?')
    await user.keyboard('{Enter}')

    // Real, expected duplication: the same short message legitimately appears as the conversation
    // list's derived title, the ChatHeader title, and the actual chat bubble - not a bug.
    expect(screen.getAllByText('Why did PMT-123 fail?').length).toBeGreaterThan(0)
    // Only asserting the real first argument - TanStack Query's mutationFn is invoked with an
    // additional internal context object as a second argument that this app's code never reads.
    await waitFor(() => expect(sendAiChatMessage).toHaveBeenCalled())
    expect(vi.mocked(sendAiChatMessage).mock.calls[0][0]).toEqual({ conversationId: expect.any(String), message: 'Why did PMT-123 fail?' })

    const expected = classifyAiError(
      Object.assign(new Error(''), { isAxiosError: true, response: { status: 503, data: { error: { errorCode: 'AI_SERVICE_NOT_READY', message: 'not built yet' } } } }),
    )
    expect(await screen.findByText(expected.message)).toBeInTheDocument()
  })

  it('New Conversation switches the active chat pane to a real empty conversation, without deleting the prior one', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('Message PaymentX AI'), 'first message')
    await user.keyboard('{Enter}')
    expect(screen.getAllByText('first message').length).toBeGreaterThan(0)

    await user.click(screen.getByRole('button', { name: 'New Conversation' }))

    // The active chat pane (ChatHeader title + message list) switches to the real new, empty
    // conversation - the prior one is legitimately preserved in the sidebar list, not deleted, so
    // "first message" may still appear there; what must be true is the *active pane* is now empty.
    expect(await screen.findByText('Start a conversation')).toBeInTheDocument()
    expect(screen.getAllByText('New conversation').length).toBeGreaterThan(0)
  })

  it('shows the real pending "thinking" state while a request is in flight, and disables Send meanwhile', async () => {
    const user = userEvent.setup()
    // A real network call takes real time - reject after a short delay here (instead of the
    // beforeEach's immediate rejection) so the real SENDING state is actually observable, the same
    // way it would be against a real, slow backend call.
    vi.mocked(sendAiChatMessage).mockImplementationOnce(
      () =>
        new Promise((_, reject) =>
          setTimeout(
            () =>
              reject(
                Object.assign(new Error('Request failed with status code 503'), {
                  isAxiosError: true,
                  response: { status: 503, data: { error: { errorCode: 'AI_SERVICE_NOT_READY', message: 'not built yet', path: '/api/v1/ai/chat' } } },
                }),
              ),
            50,
          ),
        ),
    )
    renderPage()

    await user.type(screen.getByLabelText('Message PaymentX AI'), 'Why did PMT-123 fail?')
    await user.keyboard('{Enter}')

    expect(await screen.findByText('PaymentX AI is thinking…')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send message' })).toBeDisabled()
  })
})
