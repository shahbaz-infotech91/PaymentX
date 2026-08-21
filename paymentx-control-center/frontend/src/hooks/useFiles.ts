/**
 * ENGLISH: React Query hook wrapping filesService.ts's real file
 * listing call.
 *
 * HINGLISH: filesService.ts ki real file listing call ko wrap karne
 * wala React Query hook.
 */
import { useQuery } from '@tanstack/react-query'
import { fetchFiles } from '../services/filesService'

export function useFiles() {
  return useQuery({ queryKey: ['files'], queryFn: fetchFiles })
}
