/**
 * ENGLISH: The consistent content wrapper every page in this app
 * renders inside. What it does: applies uniform max-width, padding,
 * and vertical spacing so no page has to reinvent its own layout
 * shell. Why it exists: one of the 14 required reusable components -
 * without it, 18 pages would each hand-roll slightly different
 * spacing. How it will communicate with the backend: N/A - pure
 * layout, no data.
 *
 * HINGLISH: Is app ka har page jis consistent content wrapper ke
 * andar render hota hai. Ye kya karti hai: uniform max-width, padding,
 * aur vertical spacing apply karta hai taaki kisi bhi page ko apna
 * alag layout shell dobara banane ki zaroorat na pade. Ye dashboard
 * me kyu hai: 14 required reusable components me se ek hai - iske
 * bina, 18 pages har ek thoda alag spacing hand-roll karte. Backend
 * se kaise connect hogi: N/A - pure layout hai, koi data nahi.
 */
import { Box, type SxProps, type Theme } from '@mui/material'
import type { ReactNode } from 'react'

interface PageContainerProps {
  children: ReactNode
  sx?: SxProps<Theme>
}

export function PageContainer({ children, sx }: PageContainerProps) {
  return (
    <Box
      sx={{
        maxWidth: 1400,
        mx: 'auto',
        px: { xs: 2, md: 3 },
        py: 3,
        display: 'flex',
        flexDirection: 'column',
        gap: 3,
        ...sx,
      }}
    >
      {children}
    </Box>
  )
}
