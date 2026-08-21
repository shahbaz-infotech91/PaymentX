/**
 * ENGLISH: The right-hand chat pane - header, scrollable message list
 * (auto-scrolling to the newest message), composer, and the real empty
 * state for a brand-new conversation. What it does: composes
 * ChatHeader/ChatMessage/ChatInput around useAiChat.ts's real state;
 * the empty state reuses the shared EmptyState component (see
 * components/EmptyState.tsx) rather than inventing a second "nothing
 * here yet" pattern, matching the "no fabricated business data" design
 * language every other page in this app already follows. Why it
 * exists: this phase's Step 3 required chat layout - one place
 * assembling it so AiAssistantPage.tsx stays a thin page shell like
 * every other page in this app. How it will communicate with the
 * backend: N/A directly - every prop here traces back to useAiChat.ts,
 * which is the real boundary to services/aiService.ts.
 *
 * HINGLISH: Right-hand chat pane - header, scrollable message list
 * (naye message par auto-scroll), composer, aur ek bilkul nayi
 * conversation ke liye real empty state. Ye kya karti hai:
 * ChatHeader/ChatMessage/ChatInput ko useAiChat.ts ke real state ke
 * around compose karta hai; empty state shared EmptyState component
 * reuse karta hai (components/EmptyState.tsx dekho) ek dusra "yahan
 * kuch nahi" pattern invent karne ke bajaye, matching karte hue us "no
 * fabricated business data" design language ko jo is app ka har doosra
 * page already follow karta hai. Ye dashboard me kyu hai: is phase ka
 * Step 3 required chat layout - ek jagah ise assemble karna taaki
 * AiAssistantPage.tsx is app ke har doosre page jaisa ek thin page
 * shell rahe. Backend se kaise connect hogi: N/A directly - yahan har
 * prop useAiChat.ts tak wapas jaata hai, jo services/aiService.ts ka
 * real boundary hai.
 */
import { useEffect, useRef } from 'react'
import { Box, Stack } from '@mui/material'
import ForumOutlinedIcon from '@mui/icons-material/ForumOutlined'
import { EmptyState } from '../EmptyState'
import { ChatHeader } from './ChatHeader'
import { ChatMessage } from './ChatMessage'
import { ChatInput } from './ChatInput'
import type { AiConversation } from '../../types/ai'

interface ChatWindowProps {
  conversation: AiConversation
  isSending: boolean
  onSend: (message: string) => void
  onRetry: (messageId: string) => void
  onClearConversation: () => void
  onOpenConversations?: () => void
}

export function ChatWindow({ conversation, isSending, onSend, onRetry, onClearConversation, onOpenConversations }: ChatWindowProps) {
  const scrollAnchorRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    scrollAnchorRef.current?.scrollIntoView({ block: 'end' })
  }, [conversation.messages.length, conversation.id])

  return (
    <Stack sx={{ height: '100%' }}>
      <ChatHeader
        title={conversation.title}
        onClearConversation={onClearConversation}
        clearDisabled={conversation.messages.length === 0}
        onOpenConversations={onOpenConversations}
      />

      <Box sx={{ flex: 1, overflowY: 'auto', py: 1 }}>
        {conversation.messages.length === 0 ? (
          <EmptyState
            icon={<ForumOutlinedIcon sx={{ fontSize: 40, opacity: 0.6 }} />}
            title="Start a conversation"
            message="Ask PaymentX AI about a payment, a validation result, or an operational question. Responses depend on AI services that are still being configured (see the status above)."
          />
        ) : (
          <>
            {conversation.messages.map((message) => (
              <ChatMessage key={message.id} message={message} onRetry={onRetry} />
            ))}
            <div ref={scrollAnchorRef} />
          </>
        )}
      </Box>

      <ChatInput onSend={onSend} disabled={isSending} />
    </Stack>
  )
}
