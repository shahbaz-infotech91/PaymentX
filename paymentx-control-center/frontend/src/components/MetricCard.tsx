/**
 * ENGLISH: A single KPI tile - label, value, optional icon and trend.
 * What it does: renders whatever value/label a parent passes in; it
 * has no data-fetching or fake-number logic of its own. Why it exists:
 * one of the 14 required reusable components - Phase 2's dashboard/
 * metrics pages will populate many of these from real Prometheus
 * queries; Phase 1 defines the shape without inventing sample numbers
 * ("do not create fake metrics"). How it will communicate with the
 * backend: N/A on its own - a future caller supplies `value` from a
 * real API response.
 *
 * HINGLISH: Ek single KPI tile - label, value, optional icon aur
 * trend. Ye kya karti hai: parent jo bhi value/label pass kare wahi
 * render karta hai; iska apna koi data-fetching ya fake-number logic
 * nahi hai. Ye dashboard me kyu hai: 14 required reusable components
 * me se ek hai - Phase 2 ke dashboard/metrics pages inme se kai ko
 * real Prometheus queries se populate karenge; Phase 1 sample numbers
 * invent kiye bina shape define karta hai ("do not create fake
 * metrics"). Backend se kaise connect hogi: khud se N/A - koi future
 * caller `value` ek real API response se supply karega.
 */
import { Card, CardContent, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'

interface MetricCardProps {
  label: string
  value: ReactNode
  icon?: ReactNode
  helperText?: string
}

export function MetricCard({ label, value, icon, helperText }: MetricCardProps) {
  return (
    <Card>
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
          <Stack spacing={0.5}>
            <Typography variant="body2" color="text.secondary">
              {label}
            </Typography>
            <Typography variant="h4" component="div" fontWeight={700}>
              {value}
            </Typography>
            {helperText && (
              <Typography variant="caption" color="text.secondary">
                {helperText}
              </Typography>
            )}
          </Stack>
          {icon && <Stack sx={{ color: 'primary.main', opacity: 0.8 }}>{icon}</Stack>}
        </Stack>
      </CardContent>
    </Card>
  )
}
