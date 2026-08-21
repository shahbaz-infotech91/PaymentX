/**
 * ENGLISH: The one hook every AI Assistant component uses to read and
 * mutate chat state - components never call sendAiChatMessage or touch
 * sessionStorage directly. What it does: owns the real client-side
 * conversation list (see utils/aiConversationStore.ts for why it is
 * client-side only in Phase 3.1), and wraps services/aiService.ts's
 * real POST /api/v1/ai/chat call in a React Query mutation so
 * loading/error state is never hand-rolled. sendMessage NEVER writes a
 * fabricated assistant reply - every assistant-role AiMessage this hook
 * produces is either the real backend response.content (once a real one
 * ever exists, Phase 3.2+) or ends in status 'ERROR' with a real,
 * classified AiClassifiedError (see services/aiService.ts's
 * classifyAiError) - there is no code path here that invents chat
 * content. Why it exists: this phase's Step 11 "prefer React Query for
 * server state... local chat UI state can use React state/hooks... do
 * not over-engineer state management" - conversations/messages are a
 * hybrid (locally persisted, not server-fetched) so plain useState +
 * sessionStorage is the right amount of machinery, while the actual
 * network call goes through useMutation like every other real
 * backend-calling action in this app (see useE2EFlow.ts's
 * useStartE2ERun for the same pattern). How it will communicate with
 * the backend: via services/aiService.ts's sendAiChatMessage - real
 * HTTP to AiController.
 *
 * HINGLISH: Ye ek hi hook hai jise har AI Assistant component chat
 * state padhne aur badalne ke liye use karta hai - components kabhi
 * seedhe sendAiChatMessage call nahi karte ya sessionStorage ko haath
 * nahi lagate. Ye kya karti hai: real client-side conversation list ka
 * malik hai (utils/aiConversationStore.ts dekho ki Phase 3.1 me ye
 * client-side only kyun hai), aur services/aiService.ts ki real POST
 * /api/v1/ai/chat call ko ek React Query mutation me wrap karta hai
 * taaki loading/error state kabhi hand-rolled na ho. sendMessage KABHI
 * ek fabricated assistant reply nahi likhta - is hook ka har
 * assistant-role AiMessage ya toh real backend response.content hai
 * (ek baar ek real content exist kare, Phase 3.2+), ya status 'ERROR'
 * par khatam hota hai ek real, classified AiClassifiedError ke saath
 * (services/aiService.ts ka classifyAiError dekho) - yahan koi code
 * path nahi hai jo chat content invent kare. Ye dashboard me kyu hai:
 * is phase ka Step 11 "server state ke liye React Query prefer karo...
 * local chat UI state React state/hooks use kar sakta hai... state
 * management ko over-engineer mat karo" - conversations/messages ek
 * hybrid hain (locally persisted, server-fetched nahi) isliye plain
 * useState + sessionStorage sahi amount of machinery hai, jabki actual
 * network call useMutation ke through jaati hai is app ki har doosri
 * real backend-calling action ki tarah (useE2EFlow.ts ka
 * useStartE2ERun dekho same pattern ke liye). Backend se kaise connect
 * hogi: services/aiService.ts ke sendAiChatMessage ke through - real
 * HTTP AiController ko.
 */
import { useCallback, useEffect, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { classifyAiError, sendAiChatMessage } from '../services/aiService'
import { deriveConversationTitle, loadAiConversations, saveAiConversations } from '../utils/aiConversationStore'
import type { AiChatResponse, AiConversation, AiMessage } from '../types/ai'

function createId(): string {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `id-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function createConversation(): AiConversation {
  const now = new Date().toISOString()
  return { id: createId(), title: 'New conversation', createdAt: now, updatedAt: now, messages: [] }
}

export function useAiChat() {
  const [conversations, setConversations] = useState<AiConversation[]>(() => {
    const loaded = loadAiConversations()
    return loaded.length > 0 ? loaded : [createConversation()]
  })
  const [activeConversationId, setActiveConversationId] = useState<string>(() => conversations[0].id)

  useEffect(() => {
    saveAiConversations(conversations)
  }, [conversations])

  const activeConversation = conversations.find((c) => c.id === activeConversationId) ?? conversations[0]

  const mutation = useMutation({ mutationFn: sendAiChatMessage })

  const startNewConversation = useCallback(() => {
    const conversation = createConversation()
    setConversations((prev) => [conversation, ...prev])
    setActiveConversationId(conversation.id)
  }, [])

  const selectConversation = useCallback((id: string) => {
    setActiveConversationId(id)
  }, [])

  const clearConversation = useCallback((id: string) => {
    setConversations((prev) =>
      prev.map((c) => (c.id === id ? { ...c, messages: [], title: 'New conversation', updatedAt: new Date().toISOString() } : c)),
    )
  }, [])

  const applyAssistantOutcome = useCallback(
    (conversationId: string, assistantMessageId: string, outcome: { response: AiChatResponse } | { error: unknown }) => {
      setConversations((prev) =>
        prev.map((c) => {
          if (c.id !== conversationId) return c
          return {
            ...c,
            updatedAt: new Date().toISOString(),
            messages: c.messages.map((m): AiMessage => {
              if (m.id !== assistantMessageId) return m
              if ('response' in outcome) {
                return { ...m, content: outcome.response.content, status: 'SENT', timestamp: outcome.response.timestamp, error: undefined }
              }
              return { ...m, status: 'ERROR', error: classifyAiError(outcome.error) }
            }),
          }
        }),
      )
    },
    [],
  )

  const sendMessage = useCallback(
    (text: string) => {
      const trimmed = text.trim()
      if (trimmed.length === 0 || mutation.isPending) return

      const conversationId = activeConversation.id
      const userMessage: AiMessage = { id: createId(), role: 'USER', content: trimmed, timestamp: new Date().toISOString(), status: 'SENT' }
      const assistantMessageId = createId()
      const pendingMessage: AiMessage = { id: assistantMessageId, role: 'ASSISTANT', content: '', timestamp: new Date().toISOString(), status: 'SENDING' }

      setConversations((prev) =>
        prev.map((c) => {
          if (c.id !== conversationId) return c
          const isFirstMessage = c.messages.length === 0
          return {
            ...c,
            title: isFirstMessage ? deriveConversationTitle(trimmed) : c.title,
            updatedAt: new Date().toISOString(),
            messages: [...c.messages, userMessage, pendingMessage],
          }
        }),
      )

      mutation.mutate(
        { conversationId, message: trimmed },
        {
          onSuccess: (response) => applyAssistantOutcome(conversationId, assistantMessageId, { response }),
          onError: (error) => applyAssistantOutcome(conversationId, assistantMessageId, { error }),
        },
      )
    },
    [activeConversation, applyAssistantOutcome, mutation],
  )

  /** Re-sends the user message that precedes a failed assistant message, replacing that same bubble in place. */
  const retryMessage = useCallback(
    (assistantMessageId: string) => {
      const conversationId = activeConversation.id
      const index = activeConversation.messages.findIndex((m) => m.id === assistantMessageId)
      if (index <= 0 || mutation.isPending) return
      const userMessage = activeConversation.messages[index - 1]
      if (userMessage.role !== 'USER') return

      setConversations((prev) =>
        prev.map((c) =>
          c.id !== conversationId
            ? c
            : { ...c, messages: c.messages.map((m) => (m.id === assistantMessageId ? { ...m, status: 'SENDING', error: undefined } : m)) },
        ),
      )

      mutation.mutate(
        { conversationId, message: userMessage.content },
        {
          onSuccess: (response) => applyAssistantOutcome(conversationId, assistantMessageId, { response }),
          onError: (error) => applyAssistantOutcome(conversationId, assistantMessageId, { error }),
        },
      )
    },
    [activeConversation, applyAssistantOutcome, mutation],
  )

  return {
    conversations,
    activeConversation,
    startNewConversation,
    selectConversation,
    clearConversation,
    sendMessage,
    retryMessage,
    isSending: mutation.isPending,
  }
}
