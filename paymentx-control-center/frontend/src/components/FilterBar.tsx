/**
 * ENGLISH: A horizontal row layout for filter controls (dropdowns,
 * SearchBar, TimeRangeSelector, etc.). What it does: just a Stack with
 * consistent spacing/wrapping - it holds whatever filter controls a
 * page passes as children, it doesn't know what they are. Why it
 * exists: one of the 14 required reusable components - keeps filter
 * rows visually consistent across every list page without each page
 * re-deciding gap/alignment. How it will communicate with the backend:
 * N/A - pure layout.
 *
 * HINGLISH: Filter controls (dropdowns, SearchBar, TimeRangeSelector,
 * etc.) ke liye ek horizontal row layout. Ye kya karti hai: bas ek
 * Stack hai consistent spacing/wrapping ke saath - jo bhi filter
 * controls ek page children ke roop me pass kare use hold karta hai,
 * ye nahi jaanta wo kya hain. Ye dashboard me kyu hai: 14 required
 * reusable components me se ek hai - har list page me filter rows ko
 * visually consistent rakhta hai, bina har page ke gap/alignment
 * dobara decide kiye. Backend se kaise connect hogi: N/A - pure
 * layout hai.
 */
import { Stack } from '@mui/material'
import type { ReactNode } from 'react'

interface FilterBarProps {
  children: ReactNode
}

export function FilterBar({ children }: FilterBarProps) {
  return (
    <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap" useFlexGap>
      {children}
    </Stack>
  )
}
