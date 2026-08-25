/**
 * ENGLISH: The single React Router route table. What it does: maps
 * every path from utils/routes.ts (the 18 required routes) to its real
 * page component, lazy-loaded via React.lazy so the initial bundle only
 * loads the page actually being viewed, all wrapped inside AppLayout.
 * Why it exists: one place where "route path -> page component" is
 * decided. Deliberately uses an EXPLICIT map (PAGE_COMPONENTS below),
 * not a derive-the-filename-from-the-path-string trick - a real bug
 * was found and fixed during Phase 1 build verification where that
 * kind of derivation silently produced "RabbitmqPage"/"E2ePage" for
 * routes /rabbitmq and /e2e (actual files: RabbitMqPage.tsx,
 * E2EPage.tsx) - a mismatch `tsc`/`vite build` cannot catch because
 * it's a runtime string lookup, only a manual per-route check found
 * it. An explicit map turns "wrong path string" into a single,
 * obvious lookup, and requirePageLoader() below fails loudly (at
 * router-construction time, immediately on app load) if ROUTES and
 * PAGE_COMPONENTS ever drift, instead of only breaking the one link a
 * user happens to click. How it will communicate with the backend:
 * N/A - pure client-side routing; individual pages make their own
 * backend calls once mounted.
 *
 * HINGLISH: Ek hi React Router route table. Ye kya karti hai:
 * utils/routes.ts ke har path (18 required routes) ko uske real page
 * component se map karta hai, React.lazy se lazy-loaded, taaki initial
 * bundle sirf wahi page load kare jo actually dekha ja raha hai, sab
 * AppLayout ke andar wrapped. Ye dashboard me kyu hai: ek hi jagah
 * jahan "route path -> page component" decide hota hai. Jaan-bujhkar
 * ek EXPLICIT map (neeche PAGE_COMPONENTS) use karta hai, path-string
 * se filename derive karne wali trick nahi - Phase 1 build
 * verification ke dauraan ek real bug mila aur fix kiya gaya jahan
 * wo derivation silently /rabbitmq aur /e2e routes ke liye
 * "RabbitmqPage"/"E2ePage" produce kar rahi thi (actual files:
 * RabbitMqPage.tsx, E2EPage.tsx) - ek mismatch jise `tsc`/`vite build`
 * pakad nahi sakta kyunki ye ek runtime string lookup hai, sirf ek
 * manual per-route check ne ise pakda. Ek explicit map "galat path
 * string" ko ek single, obvious lookup bana deta hai, aur neeche
 * requirePageLoader() loudly fail hota hai (router-construction time
 * par, app load hote hi) agar ROUTES aur PAGE_COMPONENTS kabhi drift
 * ho jayein, sirf us ek link ko todne ke bajaye jise user click kare.
 * Backend se kaise connect hogi:
 * N/A - pure client-side routing hai; individual pages mount hone ke
 * baad apni khud ki backend calls karte hain.
 */
import { lazy, Suspense, type ComponentType, type LazyExoticComponent } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingState } from '../components/LoadingState'
import { ROUTES, type RouteDefinition } from '../utils/routes'
import NotFoundPage from '../pages/NotFoundPage'

const PAGE_COMPONENTS: Record<RouteDefinition['path'], () => Promise<{ default: ComponentType }>> = {
  '/': () => import('../pages/DashboardPage'),
  '/services': () => import('../pages/ServicesPage'),
  '/payments': () => import('../pages/PaymentsPage'),
  '/create-payment': () => import('../pages/CreatePaymentPage'),
  '/payment-flow': () => import('../pages/PaymentFlowPage'),
  '/kafka': () => import('../pages/KafkaPage'),
  '/rabbitmq': () => import('../pages/RabbitMqPage'),
  '/redis': () => import('../pages/RedisPage'),
  '/database': () => import('../pages/DatabasePage'),
  '/audit': () => import('../pages/AuditPage'),
  '/notifications': () => import('../pages/NotificationsPage'),
  '/reconciliation': () => import('../pages/ReconciliationPage'),
  '/reporting': () => import('../pages/ReportingPage'),
  '/logs': () => import('../pages/LogsPage'),
  '/traces': () => import('../pages/TracesPage'),
  '/metrics': () => import('../pages/MetricsPage'),
  '/files': () => import('../pages/FilesPage'),
  '/api-tester': () => import('../pages/ApiTesterPage'),
  '/e2e': () => import('../pages/E2EPage'),
  '/settings': () => import('../pages/SettingsPage'),
  '/ai-assistant': () => import('../pages/AiAssistantPage'),
  '/ai-metrics': () => import('../pages/AiMetricsPage'),
  '/ai-agents': () => import('../pages/AiAgentsPage'),
  '/ai-agents/execute': () => import('../pages/AiAgentExecutePage'),
  '/ai-agents/history': () => import('../pages/AiAgentHistoryPage'),
}

/**
 * Looks up a route's page loader with a clear, immediate error if
 * ROUTES (utils/routes.ts) and PAGE_COMPONENTS above ever drift -
 * `RouteDefinition.path` is typed as `string`, not a literal union, so
 * TypeScript's own checking can't catch a missing entry here; this
 * runtime guard is the real safety net instead, and it fails loudly
 * at router-construction time (immediately on app load) rather than
 * only when a user happens to click the one broken link.
 */
function requirePageLoader(path: string) {
  const loader = PAGE_COMPONENTS[path]
  if (!loader) {
    throw new Error(`No PAGE_COMPONENTS entry for route "${path}" - add one in router.tsx.`)
  }
  return loader
}

function withSuspense(Component: LazyExoticComponent<ComponentType>) {
  return (
    <Suspense fallback={<LoadingState message="Loading page…" />}>
      <Component />
    </Suspense>
  )
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      ...ROUTES.map((route) => ({
        path: route.path === '/' ? undefined : route.path.slice(1),
        index: route.path === '/',
        element: withSuspense(lazy(requirePageLoader(route.path))),
      })),
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
