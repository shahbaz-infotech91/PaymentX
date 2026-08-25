/**
 * ENGLISH: A generic, typed table renderer. What it does: takes an
 * array of rows and a column definition list (label + a render
 * function per row), and renders a real MUI Table - no assumptions
 * about what data it shows. Why it exists: one of the 14 required
 * reusable components - Phase 2's Payments/Audit/Notifications/
 * Reconciliation pages will all list real records; this is the one
 * table implementation they'll all share instead of five bespoke
 * tables. How it will communicate with the backend: N/A on its own -
 * a future caller passes `rows` from a real API response via
 * TanStack Query.
 *
 * HINGLISH: Ek generic, typed table renderer. Ye kya karti hai: rows
 * ka ek array aur ek column definition list (label + har row ke liye
 * ek render function) leta hai, aur ek real MUI Table render karta
 * hai - kis tarah ka data dikha raha hai iske baare me koi assumption
 * nahi. Ye dashboard me kyu hai: 14 required reusable components me
 * se ek hai - Phase 2 ke Payments/Audit/Notifications/Reconciliation
 * pages sab real records list karenge; ye ek hi table implementation
 * hai jo wo sab share karenge, paanch alag-alag tables ke bajaye.
 * Backend se kaise connect hogi: khud se N/A - koi future caller
 * `rows` ek real API response se TanStack Query ke through pass
 * karega.
 */
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
} from '@mui/material'
import type { ReactNode } from 'react'
import { EmptyState } from './EmptyState'

export interface DataTableColumn<T> {
  key: string
  label: string
  render: (row: T) => ReactNode
  align?: 'left' | 'right' | 'center'
}

interface DataTableProps<T> {
  rows: T[]
  columns: DataTableColumn<T>[]
  getRowKey: (row: T) => string
  emptyTitle?: string
  emptyMessage?: string
  /** Phase 4.7 addition - optional, backward compatible (every existing caller omits it and is
   *  unaffected). When provided, each row becomes clickable (Execution History's own detail
   *  Drawer uses this instead of a per-cell click handler). */
  onRowClick?: (row: T) => void
}

export function DataTable<T>({
  rows,
  columns,
  getRowKey,
  emptyTitle = 'No records',
  emptyMessage = 'There is nothing to display yet.',
  onRowClick,
}: DataTableProps<T>) {
  if (rows.length === 0) {
    return <EmptyState title={emptyTitle} message={emptyMessage} />
  }

  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small">
        <TableHead>
          <TableRow>
            {columns.map((column) => (
              <TableCell key={column.key} align={column.align ?? 'left'}>
                {column.label}
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow
              key={getRowKey(row)}
              hover
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              sx={onRowClick ? { cursor: 'pointer' } : undefined}
            >
              {columns.map((column) => (
                <TableCell key={column.key} align={column.align ?? 'left'}>
                  {column.render(row)}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
