/**
 * ENGLISH: The "/ai-assistant" page - the required route wiring the AI
 * Chat Interface into the existing Control Center, reusing this app's
 * real shell (AppLayout/Header/Sidebar/Footer), theme, and component
 * library rather than a second React app. What it does: a thin page
 * shell (same role as every other page's default export) that wires
 * useAiChat.ts's real state into ConversationList + ChatWindow as a
 * permanent two-pane layout on desktop (md+) and a single-pane chat
 * view with a temporary Drawer for conversations on mobile/tablet -
 * the responsive behavior required by this phase's Step 3. Why it
 * exists: the required "/ai-assistant" route and Control Center
 * navigation entry (see utils/routes.ts's new 'AI' group). How it will
 * communicate with the backend: indirectly, entirely through
 * useAiChat.ts/useAiHealth.ts -> services/aiService.ts -> real
 * AiController endpoints.
 *
 * HINGLISH: "/ai-assistant" page - required route jo AI Chat Interface
 * ko existing Control Center me wire karta hai, is app ka real shell
 * (AppLayout/Header/Sidebar/Footer), theme, aur component library
 * reuse karte hue, ek dusra React app banane ke bajaye. Ye kya karti
 * hai: ek thin page shell (har doosre page ke default export jaisa hi
 * role) jo useAiChat.ts ke real state ko ConversationList + ChatWindow
 * me wire karta hai, desktop (md+) par ek permanent two-pane layout ke
 * roop me aur mobile/tablet par ek single-pane chat view ke roop me ek
 * temporary Drawer conversations ke liye - responsive behavior jo is
 * phase ka Step 3 required karta hai. Ye dashboard me kyu hai: required
 * "/ai-assistant" route aur Control Center navigation entry (utils/
 * routes.ts ka naya 'AI' group dekho). Backend se kaise connect hogi:
 * indirectly, poori tarah useAiChat.ts/useAiHealth.ts ke through ->
 * services/aiService.ts -> real AiController endpoints.
 */
import { useState } from 'react'
import { Box, Drawer, Paper } from '@mui/material'
import { PageHeader } from '../components/PageHeader'
import { ConversationList } from '../components/ai/ConversationList'
import { ChatWindow } from '../components/ai/ChatWindow'
import { useAiChat } from '../hooks/useAiChat'
import { HEADER_HEIGHT } from '../layouts/Header'
import { FOOTER_HEIGHT } from '../layouts/Footer'

// Fixed vertical chrome above/below this page (app header + footer) plus this
// page's own header block/padding - subtracted from 100vh so the two-pane
// Paper fills the real remaining space instead of growing the whole document
// (which would make ChatWindow's own internal scrolling pointless).
const RESERVED_VERTICAL_SPACE = HEADER_HEIGHT + FOOTER_HEIGHT + 180

export default function AiAssistantPage() {
  const { conversations, activeConversation, startNewConversation, selectConversation, clearConversation, sendMessage, retryMessage, isSending } =
    useAiChat()
  const [mobileConversationsOpen, setMobileConversationsOpen] = useState(false)

  function handleSelect(id: string) {
    selectConversation(id)
    setMobileConversationsOpen(false)
  }

  function handleNewConversation() {
    startNewConversation()
    setMobileConversationsOpen(false)
  }

  return (
    <Box sx={{ maxWidth: 1400, mx: 'auto', px: { xs: 2, md: 3 }, py: 3, display: 'flex', flexDirection: 'column', gap: 2 }}>
      <PageHeader
        title="AI Assistant"
        description="Ask PaymentX AI about a payment, a validation result, or an operational question."
      />

      <Paper
        variant="outlined"
        sx={{
          height: { xs: `calc(100vh - ${RESERVED_VERTICAL_SPACE + 40}px)`, md: `calc(100vh - ${RESERVED_VERTICAL_SPACE}px)` },
          minHeight: 420,
          borderRadius: 2,
          display: 'flex',
          overflow: 'hidden',
        }}
      >
        <Box sx={{ width: 280, flexShrink: 0, display: { xs: 'none', md: 'block' }, borderRight: '1px solid', borderColor: 'divider' }}>
          <ConversationList
            conversations={conversations}
            activeConversationId={activeConversation.id}
            onSelect={selectConversation}
            onNewConversation={startNewConversation}
          />
        </Box>

        <Box sx={{ flex: 1, minWidth: 0 }}>
          <ChatWindow
            conversation={activeConversation}
            isSending={isSending}
            onSend={sendMessage}
            onRetry={retryMessage}
            onClearConversation={() => clearConversation(activeConversation.id)}
            onOpenConversations={() => setMobileConversationsOpen(true)}
          />
        </Box>
      </Paper>

      <Drawer anchor="left" open={mobileConversationsOpen} onClose={() => setMobileConversationsOpen(false)}>
        <Box sx={{ width: 280, height: '100%' }}>
          <ConversationList
            conversations={conversations}
            activeConversationId={activeConversation.id}
            onSelect={handleSelect}
            onNewConversation={handleNewConversation}
          />
        </Box>
      </Drawer>
    </Box>
  )
}
