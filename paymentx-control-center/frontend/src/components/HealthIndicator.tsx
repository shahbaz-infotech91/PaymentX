/**
 * ENGLISH: A compact dot + label indicator (not a full StatusBadge
 * chip) for tight spaces like the Header/Footer status area. What it
 * does: renders a small colored dot using the same semantic
 * theme.palette.status.* tokens as StatusBadge, plus a label. Why it
 * exists: one of the 14 required reusable components - StatusBadge is
 * too visually heavy for a persistent top-bar indicator; this is the
 * lightweight sibling for that specific spot. How it will communicate
 * with the backend: N/A on its own; the AppLayout instance of this
 * component is driven by the real useHealthCheck hook's live data.
 *
 * HINGLISH: Ek compact dot + label indicator (poora StatusBadge chip
 * nahi) tight spaces ke liye jaise Header/Footer status area. Ye kya
 * karti hai: StatusBadge jaise hi semantic theme.palette.status.*
 * tokens use karke ek chhota colored dot render karta hai, plus ek
 * label. Ye dashboard me kyu hai: 14 required reusable components me
 * se ek hai - ek persistent top-bar indicator ke liye StatusBadge
 * visually bahut heavy hai; ye us specific jagah ke liye lightweight
 * sibling hai. Backend se kaise connect hogi: khud se N/A; AppLayout
 * me is component ka instance real useHealthCheck hook ke live data
 * se driven hai.
 */
import { Box, Stack, Typography } from '@mui/material'
import { useTheme } from '@mui/material/styles'
import type { Status } from '../types/common'
import { formatStatusLabel } from '../utils/formatters'

interface HealthIndicatorProps {
  status: Status
  label?: string
}

export function HealthIndicator({ status, label }: HealthIndicatorProps) {
  const theme = useTheme()
  const colorMap: Record<Status, string> = {
    UP: theme.palette.status.up,
    DOWN: theme.palette.status.down,
    DEGRADED: theme.palette.status.degraded,
    UNKNOWN: theme.palette.status.unknown,
  }

  return (
    <Stack direction="row" spacing={1} alignItems="center">
      <Box
        sx={{
          width: 8,
          height: 8,
          borderRadius: '50%',
          bgcolor: colorMap[status],
          boxShadow: status === 'UP' ? `0 0 6px ${colorMap[status]}` : 'none',
        }}
      />
      <Typography variant="body2" color="text.secondary">
        {label ?? formatStatusLabel(status)}
      </Typography>
    </Stack>
  )
}
