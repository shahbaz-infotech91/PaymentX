/**
 * Proves the real Create Payment flow: the page renders, required-field validation gates
 * submission, the real scheme dropdown offers exactly the three real domain values and the
 * selected one reaches createPayment() verbatim, and every real outcome (VALIDATED, REJECTED,
 * DUPLICATE, UNAUTHORIZED) is rendered honestly - never a fabricated success. Also proves the
 * page never renders a "Payment Type" selector, since the real, verified
 * PaymentValidationRequest contract has no such field (PaymentType is assigned internally, not
 * chosen by the caller - see PAYMENTX_CREATE_PAYMENT_SCHEME_DESIGN.md section 4), and that a
 * pending submission disables the button so a double-click cannot fire two requests.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider } from '@mui/material/styles'
import { MemoryRouter } from 'react-router-dom'
import { buildTheme } from '../theme/theme'
import CreatePaymentPage from './CreatePaymentPage'
import { fetchParticipants } from '../services/postgresService'
import { createPayment } from '../services/createPaymentService'

vi.mock('../services/postgresService', async () => {
  const actual = await vi.importActual<typeof import('../services/postgresService')>('../services/postgresService')
  return { ...actual, fetchParticipants: vi.fn() }
})

vi.mock('../services/createPaymentService', async () => {
  const actual = await vi.importActual<typeof import('../services/createPaymentService')>('../services/createPaymentService')
  return { ...actual, createPayment: vi.fn() }
})

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <ThemeProvider theme={buildTheme('light')}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <CreatePaymentPage />
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  )
}

/** Several sequential real MUI Select interactions - genuinely slower than the 5s default test timeout. */
async function fillMinimumValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByLabelText('Scheme / Network'))
  await user.click(await screen.findByRole('option', { name: 'INSTANT_PAYMENT' }))

  await user.type(screen.getByLabelText('Amount'), '100.00')

  await user.click(screen.getByLabelText('Source Participant'))
  await user.click(await screen.findByRole('option', { name: /Bank One/ }))
  await user.type(screen.getByLabelText('Debtor Account'), 'ACC-DEBTOR-1')

  await user.click(screen.getByLabelText('Destination Participant'))
  await user.click(await screen.findByRole('option', { name: /Bank Two/ }))
  await user.type(screen.getByLabelText('Creditor Account'), 'ACC-CREDITOR-1')

  await user.type(screen.getByLabelText('X-Api-Key value'), 'real-test-key')
}

const FORM_TEST_TIMEOUT = 15000

describe('CreatePaymentPage', () => {
  beforeEach(() => {
    vi.mocked(fetchParticipants).mockResolvedValue({
      content: [
        { id: 1, bankId: 'BANK-ONE', legalName: 'Bank One', status: 'ACTIVE', onboardedAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
        { id: 2, bankId: 'BANK-TWO', legalName: 'Bank Two', status: 'ACTIVE', onboardedAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
      ],
      page: 0,
      size: 100,
      totalElements: 2,
    })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders the Create Payment form', async () => {
    renderPage()
    expect(screen.getByRole('heading', { name: 'Create Payment' })).toBeInTheDocument()
    expect(screen.getByLabelText('Scheme / Network')).toBeInTheDocument()
    expect(screen.getByLabelText('Amount')).toBeInTheDocument()
    expect(await screen.findByLabelText('Source Participant')).toBeInTheDocument()
  })

  it('does not render a Payment Type selector - PaymentValidationRequest has no such field', async () => {
    renderPage()
    await screen.findByLabelText('Source Participant')
    expect(screen.queryByLabelText(/payment type/i)).not.toBeInTheDocument()
  })

  it('disables Create Payment until every required field is filled', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByLabelText('Source Participant')

    expect(screen.getByRole('button', { name: 'Create Payment' })).toBeDisabled()

    await fillMinimumValidForm(user)

    expect(screen.getByRole('button', { name: 'Create Payment' })).toBeEnabled()
  }, FORM_TEST_TIMEOUT)

  it('offers exactly the three real scheme values', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(screen.getByLabelText('Scheme / Network'))

    const listbox = await screen.findByRole('listbox')
    expect(within(listbox).getByRole('option', { name: 'INSTANT_PAYMENT' })).toBeInTheDocument()
    expect(within(listbox).getByRole('option', { name: 'REAL_TIME_PAYMENT' })).toBeInTheDocument()
    expect(within(listbox).getByRole('option', { name: 'CARD_PAYMENT' })).toBeInTheDocument()
    expect(within(listbox).getAllByRole('option')).toHaveLength(3)
  })

  it('sends the selected scheme verbatim to createPayment when submitted', async () => {
    const user = userEvent.setup()
    vi.mocked(createPayment).mockResolvedValue({ kind: 'VALIDATED', paymentReference: 'CC-1', traceId: 'trace-1' })
    renderPage()
    await screen.findByLabelText('Source Participant')

    await fillMinimumValidForm(user)
    await user.click(screen.getByRole('button', { name: 'Create Payment' }))

    await waitFor(() => expect(createPayment).toHaveBeenCalled())
    expect(vi.mocked(createPayment).mock.calls[0][0]).toEqual(
      expect.objectContaining({ scheme: 'INSTANT_PAYMENT', debtorBankId: 'BANK-ONE', creditorBankId: 'BANK-TWO' }),
    )
  }, FORM_TEST_TIMEOUT)

  it('renders the real reference and a Payment Flow link on a VALIDATED outcome', async () => {
    const user = userEvent.setup()
    vi.mocked(createPayment).mockResolvedValue({ kind: 'VALIDATED', paymentReference: 'CC-real-ref', traceId: 'trace-9' })
    renderPage()
    await screen.findByLabelText('Source Participant')

    await fillMinimumValidForm(user)
    await user.click(screen.getByRole('button', { name: 'Create Payment' }))

    expect(await screen.findByText('Payment validated')).toBeInTheDocument()
    expect(screen.getByText(/CC-real-ref/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'View Payment Flow' })).toBeInTheDocument()
  }, FORM_TEST_TIMEOUT)

  it('renders the real rejection reason on a REJECTED outcome, never as a success', async () => {
    const user = userEvent.setup()
    vi.mocked(createPayment).mockResolvedValue({ kind: 'REJECTED', paymentReference: 'CC-2', reason: 'Participant not eligible for scheme' })
    renderPage()
    await screen.findByLabelText('Source Participant')

    await fillMinimumValidForm(user)
    await user.click(screen.getByRole('button', { name: 'Create Payment' }))

    expect(await screen.findByText('Rejected by validation')).toBeInTheDocument()
    expect(screen.getByText('Participant not eligible for scheme')).toBeInTheDocument()
    expect(screen.queryByText('Payment validated')).not.toBeInTheDocument()
  }, FORM_TEST_TIMEOUT)

  it('renders an honest "already submitted" message on a DUPLICATE outcome', async () => {
    const user = userEvent.setup()
    vi.mocked(createPayment).mockResolvedValue({ kind: 'DUPLICATE', paymentReference: 'CC-3', reason: 'Payment with this reference has already been processed' })
    renderPage()
    await screen.findByLabelText('Source Participant')

    await fillMinimumValidForm(user)
    await user.click(screen.getByRole('button', { name: 'Create Payment' }))

    expect(await screen.findByText('Already submitted')).toBeInTheDocument()
    expect(screen.getByText('Payment with this reference has already been processed')).toBeInTheDocument()
  }, FORM_TEST_TIMEOUT)

  it('renders an honest Unauthorized message on a 401/403 outcome', async () => {
    const user = userEvent.setup()
    vi.mocked(createPayment).mockResolvedValue({ kind: 'UNAUTHORIZED', message: 'Invalid or missing credential.' })
    renderPage()
    await screen.findByLabelText('Source Participant')

    await fillMinimumValidForm(user)
    await user.click(screen.getByRole('button', { name: 'Create Payment' }))

    expect(await screen.findByText('Unauthorized')).toBeInTheDocument()
  }, FORM_TEST_TIMEOUT)

  it('disables the submit button while a request is pending, so a double-click cannot fire two requests', async () => {
    const user = userEvent.setup()
    let resolveCreate!: (value: Awaited<ReturnType<typeof createPayment>>) => void
    vi.mocked(createPayment).mockReturnValue(new Promise((resolve) => { resolveCreate = resolve }))
    renderPage()
    await screen.findByLabelText('Source Participant')

    await fillMinimumValidForm(user)
    const submitButton = screen.getByRole('button', { name: 'Create Payment' })
    await user.click(submitButton)

    // The button is now genuinely disabled (pending mutation) - a real second click cannot even
    // land through the browser's own pointer-events handling, which is what userEvent enforces
    // here too. fireEvent bypasses that interactability check to prove the click *handler* itself
    // is also guarded (defense in depth), not only the disabled attribute.
    expect(submitButton).toBeDisabled()
    fireEvent.click(submitButton)

    resolveCreate({ kind: 'VALIDATED', paymentReference: 'CC-4', traceId: null })
    await waitFor(() => expect(createPayment).toHaveBeenCalledTimes(1))
  }, FORM_TEST_TIMEOUT)
})
