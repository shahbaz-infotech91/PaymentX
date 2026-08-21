/**
 * ENGLISH: The message composer at the bottom of the chat pane. What
 * it does: a multiline text field with real keyboard ergonomics (Enter
 * sends, Shift+Enter inserts a newline - standard chat-app behavior),
 * a character counter enforcing the same 1-4000 bound the backend's
 * AiChatRequest.message actually validates (see
 * dto/ai/AiChatRequest.java's @Size) so a user gets instant feedback
 * instead of waiting for a real 400 VALIDATION_ERROR round trip, and a
 * Send button disabled whenever input is empty/over-limit or a request
 * is already in flight (never lets a second message queue mid-send).
 * Why it exists: this phase's Step 4 required "Enter to send", "Shift +
 * Enter for newline", "Disable Send when request is running",
 * "Character/token-safe input limits", "Accessible keyboard
 * navigation". How it will communicate with the backend: N/A directly
 * - calls the onSend callback useAiChat.ts's sendMessage is wired to.
 *
 * HINGLISH: Chat pane ke bottom par message composer. Ye kya karti hai:
 * ek multiline text field real keyboard ergonomics ke saath (Enter
 * send karta hai, Shift+Enter ek newline insert karta hai - standard
 * chat-app behavior), ek character counter jo wahi 1-4000 bound
 * enforce karta hai jo backend ka AiChatRequest.message actually
 * validate karta hai (dto/ai/AiChatRequest.java ka @Size dekho) taaki
 * user ko instant feedback mile, ek real 400 VALIDATION_ERROR round
 * trip ka wait kiye bina, aur ek Send button jab bhi input empty/
 * over-limit ho ya ek request already in flight ho tab disabled rehta
 * hai (kabhi ek dusra message mid-send queue nahi hone deta). Ye
 * dashboard me kyu hai: is phase ka Step 4 required "Enter to send",
 * "Shift + Enter for newline", "Disable Send when request is running",
 * "Character/token-safe input limits", "Accessible keyboard
 * navigation". Backend se kaise connect hogi: N/A directly - onSend
 * callback ko call karta hai jise useAiChat.ts ka sendMessage wired
 * hai.
 */
import { useState, type KeyboardEvent } from 'react'
import { Box, IconButton, Stack, TextField, Tooltip, Typography } from '@mui/material'
import SendRoundedIcon from '@mui/icons-material/SendRounded'

const MAX_MESSAGE_LENGTH = 4000

interface ChatInputProps {
  onSend: (message: string) => void
  disabled: boolean
  placeholder?: string
}

export function ChatInput({ onSend, disabled, placeholder = 'Ask PaymentX AI…' }: ChatInputProps) {
  const [value, setValue] = useState('')

  const trimmedLength = value.trim().length
  const overLimit = value.length > MAX_MESSAGE_LENGTH
  const canSend = !disabled && trimmedLength > 0 && !overLimit

  function submit() {
    if (!canSend) return
    onSend(value)
    setValue('')
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      submit()
    }
  }

  return (
    <Box sx={{ p: 1.5, borderTop: '1px solid', borderColor: 'divider' }}>
      <Stack direction="row" spacing={1} alignItems="flex-end">
        <TextField
          fullWidth
          multiline
          maxRows={6}
          size="small"
          placeholder={placeholder}
          value={value}
          onChange={(event) => setValue(event.target.value)}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          error={overLimit}
          helperText={overLimit ? `${value.length} / ${MAX_MESSAGE_LENGTH} characters - message is too long` : undefined}
          inputProps={{ 'aria-label': 'Message PaymentX AI', 'aria-describedby': 'ai-chat-input-hint' }}
        />
        <Tooltip title={disabled ? 'A response is already in progress' : 'Send message'}>
          <span>
            <IconButton color="primary" onClick={submit} disabled={!canSend} aria-label="Send message" sx={{ mb: 0.25 }}>
              <SendRoundedIcon />
            </IconButton>
          </span>
        </Tooltip>
      </Stack>
      <Typography id="ai-chat-input-hint" variant="caption" color="text.secondary" sx={{ pl: 0.5 }}>
        Enter to send • Shift + Enter for a new line
      </Typography>
    </Box>
  )
}
