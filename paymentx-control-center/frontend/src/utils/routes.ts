/**
 * ENGLISH: The single source of truth for every route path in this
 * app, plus the metadata (label, icon name, description) the Sidebar
 * and router both need. What it does: exports one typed array so
 * adding a page means editing exactly one place instead of keeping
 * router.tsx and Sidebar.tsx's route lists manually in sync. Why it
 * exists: every one of the 18 required routes is listed here exactly
 * once - this is what prevents the classic "route exists in the router
 * but not in the nav, or vice versa" drift bug. How it will
 * communicate with the backend: N/A - pure frontend routing metadata.
 *
 * HINGLISH: Is app ke har route path ke liye ek hi source of truth,
 * plus wo metadata (label, icon name, description) jo Sidebar aur
 * router dono ko chahiye. Ye kya karti hai: ek typed array export
 * karta hai taaki naya page add karne ka matlab ho sirf ek jagah edit
 * karna, router.tsx aur Sidebar.tsx ki route lists ko manually sync
 * rakhne ke bajaye. Ye dashboard me kyu hai: required 18 routes me se
 * har ek yahan exactly ek baar listed hai - yehi wo cheez hai jo
 * classic "route router me hai lekin nav me nahi, ya ulta" drift bug
 * ko rokti hai. Backend se kaise connect hogi: N/A - pure frontend
 * routing metadata hai.
 */
import DashboardOutlinedIcon from '@mui/icons-material/DashboardOutlined'
import DnsOutlinedIcon from '@mui/icons-material/DnsOutlined'
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined'
import AddCardOutlinedIcon from '@mui/icons-material/AddCardOutlined'
import AccountTreeOutlinedIcon from '@mui/icons-material/AccountTreeOutlined'
import ForumOutlinedIcon from '@mui/icons-material/ForumOutlined'
import SwapHorizOutlinedIcon from '@mui/icons-material/SwapHorizOutlined'
import MemoryOutlinedIcon from '@mui/icons-material/MemoryOutlined'
import StorageOutlinedIcon from '@mui/icons-material/StorageOutlined'
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined'
import NotificationsOutlinedIcon from '@mui/icons-material/NotificationsOutlined'
import BalanceOutlinedIcon from '@mui/icons-material/BalanceOutlined'
import SummarizeOutlinedIcon from '@mui/icons-material/SummarizeOutlined'
import ArticleOutlinedIcon from '@mui/icons-material/ArticleOutlined'
import RouteOutlinedIcon from '@mui/icons-material/RouteOutlined'
import InsightsOutlinedIcon from '@mui/icons-material/InsightsOutlined'
import TerminalOutlinedIcon from '@mui/icons-material/TerminalOutlined'
import FolderOutlinedIcon from '@mui/icons-material/FolderOutlined'
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutline'
import SettingsOutlinedIcon from '@mui/icons-material/SettingsOutlined'
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined'
import QueryStatsOutlinedIcon from '@mui/icons-material/QueryStatsOutlined'
import type { SvgIconComponent } from '@mui/icons-material'

export interface RouteDefinition {
  path: string
  label: string
  description: string
  icon: SvgIconComponent
  /** Groups related routes for the Sidebar's section headers. */
  group: 'Overview' | 'Payments' | 'Infrastructure' | 'Operations' | 'Tools' | 'AI'
}

export const ROUTES: RouteDefinition[] = [
  { path: '/', label: 'Dashboard', description: 'Platform overview at a glance.', icon: DashboardOutlinedIcon, group: 'Overview' },
  { path: '/services', label: 'Services', description: 'All PaymentX service instances and their health.', icon: DnsOutlinedIcon, group: 'Overview' },

  { path: '/payments', label: 'Payments', description: 'Payment records and their lifecycle status.', icon: PaymentsOutlinedIcon, group: 'Payments' },
  { path: '/create-payment', label: 'Create Payment', description: 'Submit a real payment through the existing validation entry point, with a chosen scheme/network.', icon: AddCardOutlinedIcon, group: 'Payments' },
  { path: '/payment-flow', label: 'Payment Flow', description: 'Visualize a payment moving through the platform.', icon: AccountTreeOutlinedIcon, group: 'Payments' },
  { path: '/reconciliation', label: 'Reconciliation', description: 'Settlement batches and mismatch records.', icon: BalanceOutlinedIcon, group: 'Payments' },
  { path: '/reporting', label: 'Reporting', description: 'Generated business reports.', icon: SummarizeOutlinedIcon, group: 'Payments' },

  { path: '/kafka', label: 'Kafka', description: 'Topics, consumer groups, and offsets.', icon: ForumOutlinedIcon, group: 'Infrastructure' },
  { path: '/rabbitmq', label: 'RabbitMQ', description: 'Exchanges, queues, and bindings.', icon: SwapHorizOutlinedIcon, group: 'Infrastructure' },
  { path: '/redis', label: 'Redis', description: 'Cache keys and usage by category.', icon: MemoryOutlinedIcon, group: 'Infrastructure' },
  { path: '/database', label: 'Database', description: 'PostgreSQL schemas and migration status.', icon: StorageOutlinedIcon, group: 'Infrastructure' },

  { path: '/audit', label: 'Audit', description: 'Audit event trail across services.', icon: FactCheckOutlinedIcon, group: 'Operations' },
  { path: '/notifications', label: 'Notifications', description: 'Notification delivery history.', icon: NotificationsOutlinedIcon, group: 'Operations' },
  { path: '/logs', label: 'Logs', description: 'Live and historical service logs.', icon: ArticleOutlinedIcon, group: 'Operations' },
  { path: '/traces', label: 'Traces', description: 'Distributed traces via Zipkin.', icon: RouteOutlinedIcon, group: 'Operations' },
  { path: '/metrics', label: 'Metrics', description: 'Prometheus-backed platform metrics.', icon: InsightsOutlinedIcon, group: 'Operations' },
  { path: '/files', label: 'Files', description: 'Generated report files, browsable and downloadable.', icon: FolderOutlinedIcon, group: 'Operations' },

  { path: '/api-tester', label: 'API Tester', description: 'Send ad-hoc requests to PaymentX services.', icon: TerminalOutlinedIcon, group: 'Tools' },
  { path: '/e2e', label: 'E2E', description: 'Trigger and observe an end-to-end payment run.', icon: PlayCircleOutlineIcon, group: 'Tools' },
  { path: '/settings', label: 'Settings', description: 'Dashboard preferences and environment configuration.', icon: SettingsOutlinedIcon, group: 'Tools' },

  // Phase 3.1 - see PAYMENTX_PHASE_3_1_AI_CHAT_INTERFACE.md. Its own group (not folded into
  // Tools) because it is a distinct platform capability, not an operator utility.
  { path: '/ai-assistant', label: 'AI Assistant', description: 'Ask PaymentX AI about payments, validations, and operations.', icon: SmartToyOutlinedIcon, group: 'AI' },

  // Phase 3.10.3 - see PAYMENTX_PHASE_3_10_3_AI_METRICS_UI_IMPLEMENTATION.md. Same 'AI' group as
  // AI Assistant above - a distinct AI Platform capability, not an operator utility (Tools) or the
  // generic infra/business catalog (Operations' existing /metrics page).
  { path: '/ai-metrics', label: 'AI Metrics', description: 'Operational metrics for the AI Platform (LLM, RAG, MCP, Agent, Embedding, Vector, Prompt).', icon: QueryStatsOutlinedIcon, group: 'AI' },
]

export const ROUTE_GROUPS: RouteDefinition['group'][] = ['Overview', 'Payments', 'Infrastructure', 'Operations', 'Tools', 'AI']
