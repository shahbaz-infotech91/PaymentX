/**
 * ENGLISH: React Query hooks wrapping every zipkinService.ts call.
 * Trace lookup is only enabled once a non-blank Trace ID is provided -
 * there is no "browse all traces" query here since Zipkin's real API
 * requires either a service name or an explicit trace ID, and this
 * page's job is Trace-ID lookup specifically.
 *
 * HINGLISH: Har zipkinService.ts call ko wrap karne wale React Query
 * hooks. Trace lookup sirf tabhi enabled hoti hai jab ek non-blank
 * Trace ID diya gaya ho - yahan koi "browse all traces" query nahi hai
 * kyunki Zipkin ke real API ko ya toh ek service name ya ek explicit
 * trace ID chahiye, aur is page ka kaam specifically Trace-ID lookup
 * hai.
 */
import { useQuery } from '@tanstack/react-query'
import { fetchZipkinServices, fetchZipkinTrace } from '../services/zipkinService'

export function useZipkinServices() {
  return useQuery({ queryKey: ['zipkin-services'], queryFn: fetchZipkinServices })
}

export function useZipkinTrace(traceId: string) {
  return useQuery({
    queryKey: ['zipkin-trace', traceId],
    queryFn: () => fetchZipkinTrace(traceId),
    enabled: false,
  })
}
