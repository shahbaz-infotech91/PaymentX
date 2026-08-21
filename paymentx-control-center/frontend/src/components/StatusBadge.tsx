/**
 * ENGLISH: A small colored chip showing a Status value. What it does:
 * maps UP/DOWN/DEGRADED/UNKNOWN to the semantic theme.palette.status.*
 * tokens (never a hardcoded color) and a readable label via
 * formatStatusLabel. Why it exists: one of the 14 required reusable
 * components - every page that lists services/batches/executions
 * needs the exact same status-to-color mapping, defined once here.
 * How it will communicate with the backend: N/A - purely renders a
 * Status value a parent component already obtained from somewhere
 * (Phase 2: a real API response).
 *
 * HINGLISH: Ek chhota colored chip jo ek Status value dikhata hai. Ye
 * kya karti hai: UP/DOWN/DEGRADED/UNKNOWN ko semantic
 * theme.palette.status.* tokens par map karta hai (kabhi hardcoded
 * color nahi) aur formatStatusLabel ke through ek readable label deta
 * hai. Ye dashboard me kyu hai: 14 required reusable components me se
 * ek hai - har page jo services/batches/executions list karta hai use
 * exactly wahi status-to-color mapping chahiye, yahan ek hi baar
 * define ki gayi hai. Backend se kaise connect hogi: N/A - ye sirf ek
 * Status value render karta hai jo parent component ne kahin se
 * already obtain kiya hai (Phase 2: ek real API response se).
 */
import { Chip } from '@mui/material'
import { useTheme } from '@mui/material/styles'
import type { Status } from '../types/common'
import { formatStatusLabel } from '../utils/formatters'

interface StatusBadgeProps {
  status: Status
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const theme = useTheme()
  const colorMap: Record<Status, string> = {
    UP: theme.palette.status.up,
    DOWN: theme.palette.status.down,
    DEGRADED: theme.palette.status.degraded,
    UNKNOWN: theme.palette.status.unknown,
  }
  const color = colorMap[status]

  return (
    <Chip
      size="small"
      label={formatStatusLabel(status)}
      sx={{
        bgcolor: `${color}1F`,
        color,
        fontWeight: 600,
        border: '1px solid',
        borderColor: `${color}55`,
      }}
    />
  )
}
