/**
 * ENGLISH: The one loading indicator every async page/section in this
 * app uses. What it does: centers a spinner with an optional message.
 * Why it exists: one of the 14 required reusable components - "pages
 * may initially contain proper empty/loading states" - this is that
 * loading state, used consistently instead of each page inventing its
 * own spinner markup. How it will communicate with the backend: N/A -
 * shown while a parent's real query (Phase 2) is in flight.
 *
 * HINGLISH: Is app ka har async page/section jo ek loading indicator
 * use karta hai. Ye kya karti hai: ek optional message ke saath ek
 * spinner center karta hai. Ye dashboard me kyu hai: 14 required
 * reusable components me se ek hai - "pages may initially contain
 * proper empty/loading states" - yehi wo loading state hai, consistently
 * use hoti hai, har page apna alag spinner markup invent karne ke
 * bajaye. Backend se kaise connect hogi: N/A - jab parent ki real
 * query (Phase 2) in flight ho tab dikhta hai.
 */
import { Stack, CircularProgress, Typography } from '@mui/material'

interface LoadingStateProps {
  message?: string
}

export function LoadingState({ message = 'Loading…' }: LoadingStateProps) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ py: 8 }}>
      <CircularProgress size={32} />
      <Typography variant="body2" color="text.secondary">
        {message}
      </Typography>
    </Stack>
  )
}
