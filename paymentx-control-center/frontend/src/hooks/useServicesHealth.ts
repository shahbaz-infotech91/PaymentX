/**
 * ENGLISH: React Query hook wrapping fetchAllServicesHealth. What it
 * does: polls the real, aggregated 9-service health check every 30s -
 * same cadence as useHealthCheck, so the Services page and the
 * dashboard's own health indicator never look out of sync with each
 * other. Why it exists: keeps ServicesPage free of axios calls.
 *
 * HINGLISH: fetchAllServicesHealth ko wrap karne wala React Query
 * hook. Ye kya karti hai: real, aggregated 9-service health check ko
 * har 30s me poll karta hai - useHealthCheck jaisi hi cadence, taaki
 * Services page aur dashboard ka apna health indicator kabhi ek
 * doosre se out of sync na dikhein. Ye dashboard me kyu hai:
 * ServicesPage ko axios calls se free rakhta hai.
 */
import { useQuery } from '@tanstack/react-query'
import { fetchAllServicesHealth } from '../services/servicesService'

export function useServicesHealth() {
  return useQuery({
    queryKey: ['services-health'],
    queryFn: fetchAllServicesHealth,
    refetchInterval: 30_000,
  })
}
