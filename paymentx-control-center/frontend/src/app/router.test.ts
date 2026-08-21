/**
 * ENGLISH: Proves the real routing wiring for the new "/ai-assistant"
 * route - this phase's Step 2/18 required "routing" test, and a real
 * regression guard against the exact "route exists in ROUTES but not
 * in the router" drift bug router.tsx's own javadoc describes. What it
 * verifies: utils/routes.ts declares /ai-assistant in the 'AI' group,
 * and the real `router` this app renders has a matching child route
 * for it - not just that the two lists look right in isolation.
 *
 * HINGLISH: Naye "/ai-assistant" route ke liye real routing wiring
 * prove karta hai - is phase ka Step 2/18 required "routing" test, aur
 * exactly wahi "route ROUTES me hai lekin router me nahi" drift bug ke
 * against ek real regression guard jise router.tsx ka apna javadoc
 * describe karta hai. Ye kya verify karta hai: utils/routes.ts
 * /ai-assistant ko 'AI' group me declare karta hai, aur is app ka real
 * `router` jo render karta hai uska ek matching child route hai - sirf
 * ye nahi ki dono lists isolation me sahi dikhti hain.
 */
import { describe, expect, it } from 'vitest'
import { ROUTES } from '../utils/routes'
import { router } from './router'

describe('AI Assistant routing', () => {
  it('is declared in ROUTES with the AI group', () => {
    const route = ROUTES.find((r) => r.path === '/ai-assistant')
    expect(route).toBeDefined()
    expect(route?.group).toBe('AI')
    expect(route?.label).toBe('AI Assistant')
  })

  it('has a matching child route in the real router', () => {
    const rootRoute = router.routes[0]
    const child = rootRoute.children?.find((c) => c.path === 'ai-assistant')
    expect(child).toBeDefined()
  })

  it('does not break any existing route - every ROUTES entry still has a router child', () => {
    const rootRoute = router.routes[0]
    const childPaths = new Set(rootRoute.children?.map((c) => (c.index ? '/' : `/${c.path}`)))
    for (const route of ROUTES) {
      expect(childPaths.has(route.path)).toBe(true)
    }
  })
})
