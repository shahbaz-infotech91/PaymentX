/**
 * ENGLISH: The standard title block every page renders at its top.
 * What it does: shows a page title, an optional one-line description,
 * and an optional actions slot (buttons) on the right, with an
 * optional breadcrumb trail above the title. Why it exists: one of
 * the 14 required reusable components - keeps every page's heading
 * visually and structurally identical. How it will communicate with
 * the backend: N/A - pure presentation.
 *
 * HINGLISH: Standard title block jo har page apne top par render
 * karta hai. Ye kya karti hai: ek page title, ek optional one-line
 * description, aur right side par ek optional actions slot (buttons)
 * dikhata hai, title ke upar ek optional breadcrumb trail ke saath.
 * Ye dashboard me kyu hai: 14 required reusable components me se ek
 * hai - har page ki heading ko visually aur structurally identical
 * rakhta hai. Backend se kaise connect hogi: N/A - pure presentation
 * hai.
 */
import { Box, Breadcrumbs, Link as MuiLink, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'

export interface Breadcrumb {
  label: string
  to?: string
}

interface PageHeaderProps {
  title: string
  description?: string
  breadcrumbs?: Breadcrumb[]
  actions?: ReactNode
}

export function PageHeader({ title, description, breadcrumbs, actions }: PageHeaderProps) {
  return (
    <Box>
      {breadcrumbs && breadcrumbs.length > 0 && (
        <Breadcrumbs sx={{ mb: 1 }} aria-label="breadcrumb">
          {breadcrumbs.map((crumb) =>
            crumb.to ? (
              <MuiLink key={crumb.label} component={RouterLink} to={crumb.to} underline="hover" color="inherit">
                {crumb.label}
              </MuiLink>
            ) : (
              <Typography key={crumb.label} color="text.primary">
                {crumb.label}
              </Typography>
            ),
          )}
        </Breadcrumbs>
      )}
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
        <Box>
          <Typography variant="h1" component="h1">
            {title}
          </Typography>
          {description && (
            <Typography variant="body1" color="text.secondary" sx={{ mt: 0.5 }}>
              {description}
            </Typography>
          )}
        </Box>
        {actions && <Box>{actions}</Box>}
      </Stack>
    </Box>
  )
}
