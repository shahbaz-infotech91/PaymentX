/**
 * ENGLISH: A card summarizing one PaymentX service (name, port,
 * status). What it does: pure presentation around StatusBadge - no
 * data-fetching. Why it exists: one of the 14 required reusable
 * components - the /services page (Phase 2, once the backend has a
 * real client to query each service's /actuator/health) will render
 * one of these per PaymentX service. How it will communicate with the
 * backend: N/A on its own; a future ServicesPage will fetch real
 * status per service and pass it in as `status`.
 *
 * HINGLISH: Ek card jo ek PaymentX service (naam, port, status)
 * summarize karta hai. Ye kya karti hai: StatusBadge ke around pure
 * presentation hai - koi data-fetching nahi. Ye dashboard me kyu hai:
 * 14 required reusable components me se ek hai - /services page
 * (Phase 2, jab backend ke paas har service ka /actuator/health query
 * karne ke liye real client ho) inme se ek har PaymentX service ke
 * liye render karega. Backend se kaise connect hogi: khud se N/A; ek
 * future ServicesPage har service ka real status fetch karke `status`
 * ke roop me pass karega.
 */
import { Card, CardContent, Stack, Typography } from '@mui/material'
import type { Status } from '../types/common'
import { StatusBadge } from './StatusBadge'

interface ServiceCardProps {
  name: string
  port: number
  status: Status
}

export function ServiceCard({ name, port, status }: ServiceCardProps) {
  return (
    <Card>
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Stack spacing={0.5}>
            <Typography variant="subtitle1" fontWeight={600}>
              {name}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Port {port}
            </Typography>
          </Stack>
          <StatusBadge status={status} />
        </Stack>
      </CardContent>
    </Card>
  )
}
