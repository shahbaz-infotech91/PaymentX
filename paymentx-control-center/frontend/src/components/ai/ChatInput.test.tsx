/**
 * ENGLISH: Proves ChatInput's real keyboard/interaction behavior - this
 * phase's Step 4/18 required "Enter to send", "Shift + Enter for
 * newline", "Disable Send when request is running" features. What it
 * verifies: Enter alone calls onSend and clears the field; Shift+Enter
 * does not call onSend; the Send button is disabled while `disabled` is
 * true and while the field is empty.
 *
 * HINGLISH: ChatInput ka real keyboard/interaction behavior prove
 * karta hai - is phase ka Step 4/18 required "Enter to send", "Shift +
 * Enter for newline", "Disable Send when request is running" features.
 * Ye kya verify karta hai: sirf Enter onSend call karta hai aur field
 * clear karta hai; Shift+Enter onSend call nahi karta; Send button
 * disabled rehta hai jab tak `disabled` true ho aur jab tak field empty
 * ho.
 */
import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ChatInput } from './ChatInput'

describe('ChatInput', () => {
  it('sends the message and clears the field on Enter', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} disabled={false} />)

    const input = screen.getByLabelText('Message PaymentX AI')
    await user.type(input, 'Why did PMT-123 fail?')
    await user.keyboard('{Enter}')

    expect(onSend).toHaveBeenCalledWith('Why did PMT-123 fail?')
    expect(input).toHaveValue('')
  })

  it('does not send on Shift+Enter and inserts a newline instead', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    render(<ChatInput onSend={onSend} disabled={false} />)

    const input = screen.getByLabelText('Message PaymentX AI')
    await user.type(input, 'line one')
    await user.keyboard('{Shift>}{Enter}{/Shift}')
    await user.type(input, 'line two')

    expect(onSend).not.toHaveBeenCalled()
    expect(input).toHaveValue('line one\nline two')
  })

  it('disables the Send button while a request is running', () => {
    render(<ChatInput onSend={vi.fn()} disabled />)
    expect(screen.getByRole('button', { name: 'Send message' })).toBeDisabled()
  })

  it('disables the Send button when the field is empty', () => {
    render(<ChatInput onSend={vi.fn()} disabled={false} />)
    expect(screen.getByRole('button', { name: 'Send message' })).toBeDisabled()
  })

  it('enables the Send button once real text is entered', async () => {
    const user = userEvent.setup()
    render(<ChatInput onSend={vi.fn()} disabled={false} />)

    await user.type(screen.getByLabelText('Message PaymentX AI'), 'hello')

    expect(screen.getByRole('button', { name: 'Send message' })).toBeEnabled()
  })
})
