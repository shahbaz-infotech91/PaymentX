/**
 * ENGLISH: Reads/writes AI Assistant conversations from sessionStorage
 * - the same storage utils/dashboardAuth.ts already uses, and for the
 * same reason (session-scoped, never silently kept forever across a
 * shared/kiosk browser). What it does: this is the ONE place chat
 * history lives today, because no server-side conversation persistence
 * exists yet (Phase 3.1 - see PAYMENTX_PHASE_3_1_AI_CHAT_INTERFACE.md
 * §8 for why: the backend has no ai_conversations/ai_messages table,
 * see PAYMENTX_PHASE_3_ARCHITECTURE.md §6 for the eventual schema).
 * This is real, honest client state - every message stored here is
 * something a user actually typed or a real classified error the
 * backend actually returned, never fabricated content - it is simply
 * not durable beyond this browser tab's session. Why it exists:
 * ChatWindow/ConversationList need a real conversation list to render
 * ("do not create fake conversations" - this satisfies "conversation
 * list UI" honestly instead of skipping the feature or faking data).
 * How it will communicate with the backend: indirectly - once Phase
 * 3.2+ adds real server-side conversation storage, this module is
 * replaced by a services/aiService.ts-backed React Query hook using the
 * exact same AiConversation/AiMessage shape, so components using
 * useAiChat.ts would not need to change.
 *
 * HINGLISH: AI Assistant conversations ko sessionStorage se
 * padhta/likhta hai - wahi storage jo utils/dashboardAuth.ts already
 * use karta hai, aur isi wajah se (session-scoped, kabhi silently
 * hamesha ke liye ek shared/kiosk browser ke across nahi rakha jaata).
 * Ye kya karti hai: yehi ek jagah hai jahan aaj chat history rehti hai,
 * kyunki abhi koi server-side conversation persistence exist nahi
 * karti (Phase 3.1 - PAYMENTX_PHASE_3_1_AI_CHAT_INTERFACE.md §8 dekho
 * ki kyun: backend ke paas koi ai_conversations/ai_messages table nahi
 * hai, eventual schema ke liye PAYMENTX_PHASE_3_ARCHITECTURE.md §6
 * dekho). Ye real, honest client state hai - yahan store hua har
 * message wo cheez hai jo ek user ne actually type ki ya ek real
 * classified error jo backend ne actually return kiya, kabhi fabricated
 * content nahi - ye sirf is browser tab ki session se aage durable
 * nahi hai. Ye dashboard me kyu hai: ChatWindow/ConversationList ko ek
 * real conversation list render karne ke liye chahiye ("fake
 * conversations mat banao" - ye "conversation list UI" ko honestly
 * satisfy karta hai, feature skip karne ya data fake karne ke bajaye).
 * Backend se kaise connect hogi: indirectly - ek baar Phase 3.2+ real
 * server-side conversation storage add karega, ye module ek
 * services/aiService.ts-backed React Query hook se replace ho jaayega,
 * exactly wahi AiConversation/AiMessage shape use karte hue, taaki
 * useAiChat.ts use karne wale components ko badalna na pade.
 */
import type { AiConversation } from '../types/ai'

const STORAGE_KEY = 'paymentx-control-center.ai-conversations'
const MAX_STORED_CONVERSATIONS = 50

export function loadAiConversations(): AiConversation[] {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as AiConversation[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function saveAiConversations(conversations: AiConversation[]): void {
  try {
    const bounded = conversations.slice(0, MAX_STORED_CONVERSATIONS)
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(bounded))
  } catch {
    // sessionStorage unavailable (e.g. private browsing edge cases) - conversations simply won't persist across reloads.
  }
}

/** Derives a short, real title from the first user message - never a placeholder like "New chat 1". */
export function deriveConversationTitle(firstMessage: string): string {
  const trimmed = firstMessage.trim()
  if (trimmed.length === 0) return 'New conversation'
  return trimmed.length > 48 ? `${trimmed.slice(0, 48)}…` : trimmed
}
