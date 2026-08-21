/**
 * ENGLISH: One chat bubble - user or assistant, and every real status
 * an assistant bubble can honestly be in: SENDING (a real request is in
 * flight - a loading indicator, never placeholder text pretending to be
 * a reply), SENT (real content - only ever populated once a real
 * backend response exists, Phase 3.2+), ERROR (the real classified
 * failure from services/aiService.ts's classifyAiError, with a Retry
 * action - never a fabricated apology pretending to be the assistant
 * talking). What it does: renders exactly what useAiChat.ts's state
 * says happened, nothing invented - copy-to-clipboard is only offered
 * on real SENT content, never on an error message (copying a
 * synthesized error string would be actively misleading). Why it
 * exists: this phase's Step 4 required message/timestamp/status/
 * loading/error/retry/copy features, and its hard "no mock AI
 * response" rule - enforced here at the single point every assistant
 * bubble renders through. How it will communicate with the backend:
 * N/A directly - the `message` prop already reflects a real backend
 * outcome (or lack of one) computed by useAiChat.ts.
 *
 * HINGLISH: Ek chat bubble - user ya assistant, aur har real status
 * jisme ek assistant bubble honestly ho sakta hai: SENDING (ek real
 * request in flight hai - ek loading indicator, kabhi placeholder text
 * nahi jo reply hone ka dikhava kare), SENT (real content - sirf tab
 * populate hota hai jab ek real backend response exist kare, Phase
 * 3.2+), ERROR (services/aiService.ts ke classifyAiError se real
 * classified failure, ek Retry action ke saath - kabhi ek fabricated
 * apology nahi jo assistant bolne ka dikhava kare). Ye kya karti hai:
 * exactly wahi render karta hai jo useAiChat.ts ka state kehta hai hua
 * hai, kuch invent nahi - copy-to-clipboard sirf real SENT content par
 * offer hota hai, kabhi ek error message par nahi (ek synthesized
 * error string copy karna actively misleading hota). Ye dashboard me
 * kyu hai: is phase ka Step 4 required message/timestamp/status/
 * loading/error/retry/copy features, aur uska hard "no mock AI
 * response" rule - yahan us ek point par enforce hota hai jahan se har
 * assistant bubble render hota hai. Backend se kaise connect hogi: N/A
 * directly - `message` prop already ek real backend outcome (ya uski
 * kami) reflect karta hai jo useAiChat.ts ne compute kiya hai.
 */
import { useState } from 'react'
import { Box, CircularProgress, IconButton, Paper, Stack, Tooltip, Typography } from '@mui/material'
import ContentCopyOutlinedIcon from '@mui/icons-material/ContentCopyOutlined'
import CheckIcon from '@mui/icons-material/Check'
import ReplayOutlinedIcon from '@mui/icons-material/ReplayOutlined'
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline'
import type { AiMessage } from '../../types/ai'
import { formatTimestamp } from '../../utils/formatters'

interface ChatMessageProps {
  message: AiMessage
  onRetry: (messageId: string) => void
}

export function ChatMessage({ message, onRetry }: ChatMessageProps) {
  const [copied, setCopied] = useState(false)
  const isUser = message.role === 'USER'

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(message.content)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // Clipboard API unavailable/denied - no real content was lost, just no copy confirmation to show.
    }
  }

  return (
    <Stack direction="row" justifyContent={isUser ? 'flex-end' : 'flex-start'} sx={{ px: 2, py: 0.75 }}>
      <Stack spacing={0.5} sx={{ maxWidth: { xs: '85%', sm: '70%' } }} alignItems={isUser ? 'flex-end' : 'flex-start'}>
        <Paper
          variant="outlined"
          sx={{
            px: 1.75,
            py: 1.25,
            borderRadius: 2,
            bgcolor: isUser ? 'primary.main' : message.status === 'ERROR' ? 'transparent' : 'background.paper',
            color: isUser ? 'primary.contrastText' : 'text.primary',
            borderColor: message.status === 'ERROR' ? 'error.main' : 'divider',
          }}
        >
          {message.status === 'SENDING' && (
            <Stack direction="row" alignItems="center" spacing={1.25}>
              <CircularProgress size={14} thickness={5} />
              <Typography variant="body2" color="text.secondary">
                PaymentX AI is thinking…
              </Typography>
            </Stack>
          )}

          {message.status === 'SENT' && (
            <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {message.content}
            </Typography>
          )}

          {message.status === 'ERROR' && message.error && (
            <Stack spacing={1}>
              <Stack direction="row" spacing={1} alignItems="flex-start">
                <ErrorOutlineIcon fontSize="small" color="error" sx={{ mt: 0.2 }} />
                <Typography variant="body2" color="error.main">
                  {message.error.message}
                </Typography>
              </Stack>
              <Box>
                <Tooltip title="Retry this message">
                  <IconButton size="small" onClick={() => onRetry(message.id)} aria-label="Retry message">
                    <ReplayOutlinedIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </Box>
            </Stack>
          )}
        </Paper>

        <Stack direction="row" spacing={1} alignItems="center" sx={{ px: 0.5 }}>
          <Typography variant="caption" color="text.secondary">
            {formatTimestamp(message.timestamp)}
          </Typography>
          {message.status === 'SENT' && !isUser && (
            <Tooltip title={copied ? 'Copied' : 'Copy response'}>
              <IconButton size="small" onClick={handleCopy} aria-label="Copy response" sx={{ p: 0.25 }}>
                {copied ? <CheckIcon sx={{ fontSize: 14 }} /> : <ContentCopyOutlinedIcon sx={{ fontSize: 14 }} />}
              </IconButton>
            </Tooltip>
          )}
        </Stack>
      </Stack>
    </Stack>
  )
}
