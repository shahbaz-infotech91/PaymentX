/**
 * ENGLISH: The left-hand conversation list pane (desktop two-pane
 * layout) - real, client-side-stored conversations (see
 * utils/aiConversationStore.ts), never fabricated sample chats. What it
 * does: a "New Conversation" action plus a scrollable list of real
 * conversations with title + relative last-updated time, highlighting
 * the active one. Why it exists: this phase's Step 3/4 required
 * "Conversations" pane and "New Conversation"/"Conversation list UI"
 * features. How it will communicate with the backend: N/A directly -
 * purely renders the AiConversation[] useAiChat.ts already loaded from
 * sessionStorage.
 *
 * HINGLISH: Left-hand conversation list pane (desktop two-pane
 * layout) - real, client-side-stored conversations
 * (utils/aiConversationStore.ts dekho), kabhi fabricated sample chats
 * nahi. Ye kya karti hai: ek "New Conversation" action plus real
 * conversations ki ek scrollable list, title + relative last-updated
 * time ke saath, active wali ko highlight karte hue. Ye dashboard me
 * kyu hai: is phase ka Step 3/4 required "Conversations" pane aur "New
 * Conversation"/"Conversation list UI" features. Backend se kaise
 * connect hogi: N/A directly - sirf us AiConversation[] ko render
 * karta hai jo useAiChat.ts sessionStorage se already load kar chuka
 * hai.
 */
import { Box, Button, List, ListItemButton, ListItemText, Stack, Typography } from '@mui/material'
import AddCommentOutlinedIcon from '@mui/icons-material/AddCommentOutlined'
import ForumOutlinedIcon from '@mui/icons-material/ForumOutlined'
import type { AiConversation } from '../../types/ai'
import { formatTimestamp } from '../../utils/formatters'

interface ConversationListProps {
  conversations: AiConversation[]
  activeConversationId: string
  onSelect: (id: string) => void
  onNewConversation: () => void
}

export function ConversationList({ conversations, activeConversationId, onSelect, onNewConversation }: ConversationListProps) {
  return (
    <Stack sx={{ height: '100%' }}>
      <Box sx={{ p: 1.5 }}>
        <Button fullWidth variant="outlined" startIcon={<AddCommentOutlinedIcon />} onClick={onNewConversation}>
          New Conversation
        </Button>
      </Box>
      <List dense disablePadding sx={{ overflowY: 'auto', flex: 1 }}>
        {conversations.map((conversation) => {
          const lastMessage = conversation.messages[conversation.messages.length - 1]
          return (
            <ListItemButton
              key={conversation.id}
              selected={conversation.id === activeConversationId}
              onClick={() => onSelect(conversation.id)}
              sx={{ mx: 1, mb: 0.5, borderRadius: 1.5, alignItems: 'flex-start' }}
            >
              <ForumOutlinedIcon fontSize="small" sx={{ mt: 0.4, mr: 1.5, opacity: 0.7, flexShrink: 0 }} />
              <ListItemText
                primary={conversation.title}
                primaryTypographyProps={{ noWrap: true, fontWeight: conversation.id === activeConversationId ? 600 : 400 }}
                secondary={
                  lastMessage
                    ? formatTimestamp(conversation.updatedAt)
                    : `Started ${formatTimestamp(conversation.createdAt)}`
                }
                secondaryTypographyProps={{ noWrap: true, variant: 'caption' }}
              />
            </ListItemButton>
          )
        })}
        {conversations.length === 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ px: 2, py: 2 }}>
            No conversations yet.
          </Typography>
        )}
      </List>
    </Stack>
  )
}
