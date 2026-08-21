/**
 * ENGLISH: A small, real pagination control - previous/next buttons
 * plus a "showing X-Y of Z" label computed from the real
 * PageResponse<T> totalElements the backend returned (never an
 * estimate). What it does: Next is disabled once the real total is
 * exhausted; Previous is disabled on page 0 - both computed from real
 * numbers, not guessed. Why it exists: added in Phase 2 because 6
 * different Postgres-backed pages (Payments, Audit, Notifications,
 * Reconciliation, Reporting, Participants/Routing) all need the exact
 * same real pagination behavior against the backend's real LIMIT/
 * OFFSET-bounded endpoints - one shared control instead of 6 copies.
 * How it will communicate with the backend: N/A on its own - the
 * parent page owns the page/size state and re-runs its real React
 * Query hook when this control changes it.
 *
 * HINGLISH: Ek chhota, real pagination control - previous/next buttons
 * plus ek "showing X-Y of Z" label jo backend ke return kiye gaye real
 * PageResponse<T> totalElements se compute hota hai (kabhi estimate
 * nahi). Ye kya karti hai: Next tab disable hota hai jab real total
 * khatam ho jaye; Previous page 0 par disable hota hai - dono real
 * numbers se compute kiye gaye, guess nahi kiye gaye. Ye dashboard me
 * kyu hai: Phase 2 me add kiya gaya kyunki 6 alag Postgres-backed
 * pages (Payments, Audit, Notifications, Reconciliation, Reporting,
 * Participants/Routing) sabko exactly wahi real pagination behaviour
 * chahiye backend ke real LIMIT/OFFSET-bounded endpoints ke against -
 * 6 copies ke bajaye ek shared control. Backend se kaise connect
 * hogi: khud se N/A - parent page page/size state own karta hai aur
 * jab ye control use badalta hai toh apna real React Query hook
 * dobara chalata hai.
 */
import { Stack, Button, Typography } from '@mui/material'

interface PagerProps {
  page: number
  size: number
  totalElements: number
  onPageChange: (page: number) => void
}

export function Pager({ page, size, totalElements, onPageChange }: PagerProps) {
  const from = totalElements === 0 ? 0 : page * size + 1
  const to = Math.min((page + 1) * size, totalElements)
  const hasNext = to < totalElements

  return (
    <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mt: 1 }}>
      <Typography variant="caption" color="text.secondary">
        Showing {from}-{to} of {totalElements}
      </Typography>
      <Stack direction="row" spacing={1}>
        <Button size="small" variant="outlined" disabled={page === 0} onClick={() => onPageChange(page - 1)}>
          Previous
        </Button>
        <Button size="small" variant="outlined" disabled={!hasNext} onClick={() => onPageChange(page + 1)}>
          Next
        </Button>
      </Stack>
    </Stack>
  )
}
