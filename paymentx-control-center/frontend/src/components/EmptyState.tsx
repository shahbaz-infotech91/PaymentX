/**
 * ENGLISH: What every one of the 18 pages actually renders in Phase 1.
 * What it does: shows an icon, a title, and an honest explanatory
 * message - no fabricated numbers, no placeholder charts pretending to
 * be real data. Why it exists: one of the 14 required reusable
 * components, and the direct mechanism behind "do not use fake
 * business data / do not create fake metrics" - every Phase 1 page
 * composes this instead of inventing sample data to look finished.
 * How it will communicate with the backend: N/A - this is what renders
 * specifically because there is no real backend data to show yet.
 *
 * HINGLISH: Phase 1 me 18 pages me se har ek actually yehi render
 * karta hai. Ye kya karti hai: ek icon, ek title, aur ek honest
 * explanatory message dikhata hai - koi fabricated numbers nahi, koi
 * placeholder charts jo real data hone ka dikhava karein nahi. Ye
 * dashboard me kyu hai: 14 required reusable components me se ek hai,
 * aur "do not use fake business data / do not create fake metrics" ke
 * peeche ka direct mechanism hai - har Phase 1 page finished dikhne ke
 * liye sample data invent karne ke bajaye isi ko compose karta hai.
 * Backend se kaise connect hogi: N/A - ye specifically isliye render
 * hota hai kyunki abhi dikhane layak koi real backend data nahi hai.
 */
import { Stack, Typography } from '@mui/material'
import InboxOutlinedIcon from '@mui/icons-material/InboxOutlined'
import type { ReactNode } from 'react'

interface EmptyStateProps {
  title: string
  message: string
  icon?: ReactNode
}

export function EmptyState({ title, message, icon }: EmptyStateProps) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={1.5} sx={{ py: 8, color: 'text.secondary' }}>
      {icon ?? <InboxOutlinedIcon sx={{ fontSize: 40, opacity: 0.6 }} />}
      <Typography variant="subtitle1" fontWeight={600} color="text.primary">
        {title}
      </Typography>
      <Typography variant="body2" sx={{ maxWidth: 440, textAlign: 'center' }}>
        {message}
      </Typography>
    </Stack>
  )
}
