/**
 * ENGLISH: The header's real operational alerts indicator (Phase 3).
 * What it does: polls GET /api/v1/alerts every 25s via useAlerts, and
 * shows a real badge count (colored red if any CRITICAL alert is
 * present, amber if only WARNING) - the badge is genuinely hidden
 * when the real list is empty, never a placeholder "0". Clicking it
 * opens a real list of every currently-true alert with its real
 * severity, category, message (which embeds the real number that
 * triggered it), and detection time. Why it exists: this IS the Phase
 * 3 Alerts requirement, placed in global chrome (rather than a
 * dedicated route) so it's visible from anywhere in the dashboard.
 *
 * HINGLISH: Header ka real operational alerts indicator (Phase 3). Ye
 * kya karti hai: useAlerts ke through har 25s me GET /api/v1/alerts
 * poll karta hai, aur ek real badge count dikhata hai (red agar koi
 * CRITICAL alert present ho, amber agar sirf WARNING) - jab real list
 * empty ho tab badge genuinely hidden hota hai, kabhi ek placeholder
 * "0" nahi. Ise click karne se har currently-true alert ki ek real
 * list khulti hai, uske real severity, category, message (jisme wo
 * real number embed hota hai jisne use trigger kiya) aur detection
 * time ke saath. Ye dashboard me kyu hai: yehi Phase 3 Alerts
 * requirement HAI, global chrome me rakha gaya (ek dedicated route ke
 * bajaye) taaki ye dashboard me kahin se bhi visible ho.
 */
import { useState } from 'react'
import {
  Badge,
  Box,
  Divider,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Popover,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material'
import NotificationsOutlinedIcon from '@mui/icons-material/NotificationsOutlined'
import { StatusBadge } from './StatusBadge'
import { EmptyState } from './EmptyState'
import { useAlerts } from '../hooks/useAlerts'
import { formatTimestamp } from '../utils/formatters'

export function AlertsBell() {
  const { data } = useAlerts()
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null)

  const alerts = data ?? []
  const hasCritical = alerts.some((a) => a.severity === 'CRITICAL')
  const badgeColor = hasCritical ? 'error' : alerts.length > 0 ? 'warning' : 'default'

  return (
    <>
      <Tooltip title="Operational alerts">
        <IconButton onClick={(e) => setAnchorEl(e.currentTarget)} aria-label="Alerts">
          <Badge badgeContent={alerts.length} color={badgeColor === 'default' ? undefined : badgeColor}>
            <NotificationsOutlinedIcon />
          </Badge>
        </IconButton>
      </Tooltip>
      <Popover
        open={anchorEl !== null}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <Box sx={{ width: 380, maxHeight: 480, overflowY: 'auto' }}>
          <Typography variant="subtitle2" sx={{ px: 2, py: 1.5, fontWeight: 700 }}>
            Operational Alerts
          </Typography>
          <Divider />
          {alerts.length === 0 && (
            <Box sx={{ p: 2 }}>
              <EmptyState title="All clear" message="No real alert conditions are currently detected." />
            </Box>
          )}
          <List disablePadding>
            {alerts.map((alert) => (
              <ListItem key={alert.id} divider alignItems="flex-start">
                <ListItemText
                  primary={
                    <Stack direction="row" spacing={1} alignItems="center">
                      <StatusBadge status={alert.severity === 'CRITICAL' ? 'DOWN' : 'DEGRADED'} />
                      <Typography variant="body2" fontWeight={600}>
                        {alert.title}
                      </Typography>
                    </Stack>
                  }
                  secondary={
                    <>
                      <Typography variant="caption" color="text.secondary" component="div">
                        {alert.message}
                      </Typography>
                      <Typography variant="caption" color="text.disabled" component="div">
                        {alert.category} · {formatTimestamp(alert.detectedAt)}
                      </Typography>
                    </>
                  }
                />
              </ListItem>
            ))}
          </List>
        </Box>
      </Popover>
    </>
  )
}
