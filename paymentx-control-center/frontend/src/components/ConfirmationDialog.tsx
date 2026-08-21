/**
 * ENGLISH: A reusable confirm/cancel modal for destructive or
 * consequential actions. What it does: a controlled MUI Dialog with a
 * title, message, and Confirm/Cancel buttons - the caller owns the
 * open/close state and what happens on confirm. Why it exists: one of
 * the 14 required reusable components - Phase 2 actions like
 * "reprocess batch", "resolve mismatch", or "retry notification" (all
 * real mutating operations in the existing PaymentX services) should
 * never fire without an explicit confirmation step; this is that one
 * shared step. How it will communicate with the backend: N/A on its
 * own - `onConfirm` is where a future caller would trigger the real
 * mutation.
 *
 * HINGLISH: Destructive ya consequential actions ke liye ek reusable
 * confirm/cancel modal. Ye kya karti hai: ek controlled MUI Dialog
 * hai title, message, aur Confirm/Cancel buttons ke saath - open/close
 * state aur confirm par kya hota hai ye caller ke paas hota hai. Ye
 * dashboard me kyu hai: 14 required reusable components me se ek hai
 * - Phase 2 ke actions jaise "reprocess batch", "resolve mismatch",
 * ya "retry notification" (existing PaymentX services me sab real
 * mutating operations hain) kabhi bina explicit confirmation step ke
 * fire nahi hone chahiye; ye wahi ek shared step hai. Backend se kaise
 * connect hogi: khud se N/A - `onConfirm` wo jagah hai jahan koi
 * future caller real mutation trigger karega.
 */
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material'

interface ConfirmationDialogProps {
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  destructive?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmationDialog({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  destructive = false,
  onConfirm,
  onCancel,
}: ConfirmationDialogProps) {
  return (
    <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{message}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} color="inherit">
          Cancel
        </Button>
        <Button onClick={onConfirm} color={destructive ? 'error' : 'primary'} variant="contained">
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
