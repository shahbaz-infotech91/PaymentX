/**
 * ENGLISH: The one error indicator every async page/section in this
 * app uses when a request fails. What it does: shows an error icon,
 * the real error message (never a generic "something went wrong" that
 * hides what actually failed), and an optional retry button. Why it
 * exists: one of the 14 required reusable components - used by the
 * Footer's real health-check status and every Phase 2 data-fetching
 * page. How it will communicate with the backend: N/A on its own; the
 * `message` prop is meant to come from toApiError() (see
 * api/axiosClient.ts) after a real failed backend call.
 *
 * HINGLISH: Is app ka har async page/section jab ek request fail hoti
 * hai tab jo ek error indicator use karta hai. Ye kya karti hai: ek
 * error icon, real error message (kabhi generic "something went
 * wrong" nahi jo chhupa de ki actually kya fail hua), aur ek optional
 * retry button dikhata hai. Ye dashboard me kyu hai: 14 required
 * reusable components me se ek hai - Footer ke real health-check
 * status aur har Phase 2 data-fetching page isse use karta hai.
 * Backend se kaise connect hogi: khud se N/A; `message` prop
 * toApiError() se aana chahiye (api/axiosClient.ts dekho) ek real
 * fail hui backend call ke baad.
 */
import { Alert, AlertTitle, Button, Stack } from '@mui/material'

interface ErrorStateProps {
  title?: string
  message: string
  onRetry?: () => void
}

export function ErrorState({ title = 'Something went wrong', message, onRetry }: ErrorStateProps) {
  return (
    <Alert
      severity="error"
      action={
        onRetry && (
          <Button color="inherit" size="small" onClick={onRetry}>
            Retry
          </Button>
        )
      }
    >
      <Stack spacing={0.5}>
        <AlertTitle>{title}</AlertTitle>
        {message}
      </Stack>
    </Alert>
  )
}
