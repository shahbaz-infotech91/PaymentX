/**
 * ENGLISH: The "/services" page listing every real PaymentX service.
 * What it does: calls useServicesHealth (real, parallel /actuator/
 * health probes against all 9 PaymentX services via the backend) and
 * renders one ServiceCard per real result with its real status - "UP"
 * only when the backend actually got a 200 with status "UP" back,
 * "DOWN"/"UNREACHABLE" mapped honestly, never guessed. Why it exists:
 * required route "/services", now wired to the real Phase 2 backend
 * instead of Phase 1's hardcoded UNKNOWN placeholder. How it will
 * communicate with the backend: via useServicesHealth ->
 * servicesService.ts -> GET /api/v1/services.
 *
 * HINGLISH: "/services" page jo har real PaymentX service list karta
 * hai. Ye kya karti hai: useServicesHealth call karta hai (backend ke
 * through saare 9 PaymentX services ke against real, parallel
 * /actuator/health probes) aur har real result ke liye ek ServiceCard
 * uske real status ke saath render karta hai - "UP" sirf tabhi jab
 * backend ko actually ek 200 status "UP" ke saath wapas mile,
 * "DOWN"/"UNREACHABLE" honestly map kiye gaye, kabhi guess nahi kiye
 * gaye. Ye dashboard me kyu hai: required route "/services", ab Phase
 * 1 ke hardcoded UNKNOWN placeholder ki jagah real Phase 2 backend se
 * wired hai. Backend se kaise connect hogi: useServicesHealth ->
 * servicesService.ts -> GET /api/v1/services ke through.
 */
import { Grid2 as Grid } from '@mui/material'
import { PageContainer } from '../components/PageContainer'
import { PageHeader } from '../components/PageHeader'
import { ServiceCard } from '../components/ServiceCard'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/ErrorState'
import { useServicesHealth } from '../hooks/useServicesHealth'
import { toApiError } from '../api/axiosClient'
import type { Status } from '../types/common'

function toStatus(raw: string | null): Status {
  if (raw === 'UP') return 'UP'
  if (raw === 'DOWN' || raw === 'UNREACHABLE') return 'DOWN'
  if (raw === null) return 'UNKNOWN'
  return 'DEGRADED'
}

export default function ServicesPage() {
  const { data, isLoading, isError, error, refetch } = useServicesHealth()

  return (
    <PageContainer>
      <PageHeader
        title="Services"
        description="The 9 PaymentX business services, with real live Actuator health checks."
      />
      {isLoading && <LoadingState message="Checking service health…" />}
      {isError && <ErrorState message={toApiError(error).message} onRetry={() => refetch()} />}
      {data && (
        <Grid container spacing={2}>
          {data.map((service) => (
            <Grid key={service.serviceSlug} size={{ xs: 12, sm: 6, md: 4 }}>
              <ServiceCard
                name={service.serviceName}
                port={Number(service.baseUrl.split(':').pop())}
                status={toStatus(service.status)}
              />
            </Grid>
          ))}
        </Grid>
      )}
    </PageContainer>
  )
}
