/**
 * ENGLISH: The catch-all 404 page. What it does: shown when the URL
 * doesn't match any route in utils/routes.ts, with a link back to the
 * dashboard. Why it exists: React Router needs an explicit fallback
 * route or an unmatched URL renders nothing at all - this keeps that
 * failure mode from being a blank white screen. How it will
 * communicate with the backend: N/A.
 *
 * HINGLISH: Catch-all 404 page. Ye kya karti hai: jab URL
 * utils/routes.ts ke kisi bhi route se match nahi karta, tab dikhta
 * hai, dashboard par wapas jaane ke ek link ke saath. Ye dashboard me
 * kyu hai: React Router ko ek explicit fallback route chahiye, warna
 * ek unmatched URL kuch bhi render nahi karta - ye us failure mode ko
 * ek blank white screen banne se rokta hai. Backend se kaise connect
 * hogi: N/A.
 */
import { Button, Stack, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { PageContainer } from '../components/PageContainer'

export default function NotFoundPage() {
  return (
    <PageContainer>
      <Stack alignItems="center" spacing={2} sx={{ py: 10 }}>
        <Typography variant="h1">404</Typography>
        <Typography variant="body1" color="text.secondary">
          This page does not exist.
        </Typography>
        <Button component={RouterLink} to="/" variant="contained">
          Back to Dashboard
        </Button>
      </Stack>
    </PageContainer>
  )
}
