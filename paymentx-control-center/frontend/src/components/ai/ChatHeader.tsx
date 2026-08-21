/**
 * ENGLISH: The top bar of the chat pane - conversation title on the
 * left, real AIStatus indicator and a "Clear conversation" action on
 * the right. What it does: pure presentation - takes the active
 * conversation's title and callbacks as props, renders nothing it was
 * not given. Why it exists: keeps AiAssistantPage/ChatWindow from
 * duplicating this header markup, matching how PageHeader.tsx serves
 * every other page. How it will communicate with the backend: N/A
 * directly - AIStatus (rendered inside) is what makes the real
 * backend call.
 *
 * HINGLISH: Chat pane ka top bar - left par conversation title, right
 * par real AIStatus indicator aur ek "Clear conversation" action. Ye
 * kya karti hai: pure presentation - active conversation ka title aur
 * callbacks props ke roop me leta hai, jo nahi diya gaya wo kuch render
 * nahi karta. Ye dashboard me kyu hai: AiAssistantPage/ChatWindow ko is
 * header markup ko duplicate karne se bachata hai, matching karte hue
 * ki PageHeader.tsx har doosre page ko kaise serve karta hai. Backend
 * se kaise connect hogi: N/A directly - AIStatus (andar render hota
 * hai) hi real backend call karta hai.
 */
import { IconButton, Stack, Tooltip, Typography } from '@mui/material'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import ForumOutlinedIcon from '@mui/icons-material/ForumOutlined'
import { AIStatus } from './AIStatus'

interface ChatHeaderProps {
  title: string
  onClearConversation: () => void
  clearDisabled: boolean
  /** Only rendered (and only on narrow viewports) when provided - opens the mobile conversations drawer. Desktop already shows the conversation list permanently, see AiAssistantPage.tsx. */
  onOpenConversations?: () => void
}

export function ChatHeader({ title, onClearConversation, clearDisabled, onOpenConversations }: ChatHeaderProps) {
  return (
    <Stack
      direction="row"
      alignItems="center"
      justifyContent="space-between"
      sx={{ px: 2, py: 1.5, borderBottom: '1px solid', borderColor: 'divider', minHeight: 64 }}
    >
      <Stack direction="row" alignItems="center" spacing={1} sx={{ minWidth: 0 }}>
        {onOpenConversations && (
          <IconButton
            size="small"
            onClick={onOpenConversations}
            aria-label="Open conversations"
            sx={{ display: { xs: 'inline-flex', md: 'none' } }}
          >
            <ForumOutlinedIcon fontSize="small" />
          </IconButton>
        )}
        <Typography variant="subtitle1" fontWeight={600} noWrap sx={{ maxWidth: { xs: 160, sm: 360 } }}>
          {title}
        </Typography>
      </Stack>
      <Stack direction="row" alignItems="center" spacing={2}>
        <AIStatus />
        <Tooltip title="Clear conversation">
          <span>
            <IconButton size="small" onClick={onClearConversation} disabled={clearDisabled} aria-label="Clear conversation">
              <DeleteOutlineIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
      </Stack>
    </Stack>
  )
}
