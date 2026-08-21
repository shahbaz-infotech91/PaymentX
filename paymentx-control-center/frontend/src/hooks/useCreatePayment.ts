/** React Query mutation hook wrapping createPaymentService.ts's createPayment() call. */
import { useMutation } from '@tanstack/react-query'
import { createPayment } from '../services/createPaymentService'

export function useCreatePayment() {
  return useMutation({ mutationFn: createPayment })
}
