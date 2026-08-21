/**
 * ENGLISH: The header's real Global Search box (Phase 3) - Payment
 * Reference, Correlation ID, Trace ID, Participant, and Settlement
 * Reference, all in one input. What it does: debounces the typed
 * query by 300ms (so fast typing doesn't fire a request per
 * keystroke), calls useGlobalSearch once 2+ real characters are
 * typed, and renders the real results grouped by type with a real
 * subtitle from the matched row. Selecting a result navigates to the
 * page that actually shows it - a payment to its real Payment Flow, a
 * participant to Transaction Monitor filtered by that participant, a
 * settlement file to the Reconciliation page's Settlement Files tab.
 * Why it exists: this IS the Phase 3 Global Search requirement.
 *
 * HINGLISH: Header ka real Global Search box (Phase 3) - Payment
 * Reference, Correlation ID, Trace ID, Participant, aur Settlement
 * Reference, sab ek hi input me. Ye kya karti hai: typed query ko
 * 300ms debounce karta hai (taaki fast typing har keystroke par ek
 * request fire na kare), 2+ real characters type hone par
 * useGlobalSearch call karta hai, aur real results ko type ke hisaab
 * se group karke, matched row se ek real subtitle ke saath render
 * karta hai. Ek result select karne se us page par navigate hota hai
 * jo use actually dikhata hai - ek payment uske real Payment Flow par,
 * ek participant Transaction Monitor par us participant se filtered,
 * ek settlement file Reconciliation page ke Settlement Files tab par.
 * Ye dashboard me kyu hai: yehi Phase 3 Global Search requirement HAI.
 */
import { useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { Autocomplete, CircularProgress, InputAdornment, ListItemText, TextField } from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined'
import AccountBalanceOutlinedIcon from '@mui/icons-material/AccountBalanceOutlined'
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined'
import { useGlobalSearch } from '../hooks/useGlobalSearch'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import type { SearchResult } from '../services/searchService'

const TYPE_ICON: Record<SearchResult['type'], ReactNode> = {
  PAYMENT: <PaymentsOutlinedIcon fontSize="small" />,
  PARTICIPANT: <AccountBalanceOutlinedIcon fontSize="small" />,
  SETTLEMENT_FILE: <DescriptionOutlinedIcon fontSize="small" />,
}

function routeFor(result: SearchResult): string {
  switch (result.type) {
    case 'PAYMENT':
      return `/payment-flow?reference=${encodeURIComponent(result.key)}`
    case 'PARTICIPANT':
      return `/payments?participantId=${encodeURIComponent(result.key)}`
    case 'SETTLEMENT_FILE':
      return `/reconciliation?tab=settlement`
  }
}

export function GlobalSearchBox() {
  const navigate = useNavigate()
  const [inputValue, setInputValue] = useState('')
  const debounced = useDebouncedValue(inputValue, 300)

  const { data, isFetching } = useGlobalSearch(debounced)

  return (
    <Autocomplete<SearchResult, false, false, true>
      sx={{ width: 320 }}
      size="small"
      freeSolo
      forcePopupIcon={false}
      filterOptions={(options) => options}
      options={data ?? []}
      loading={isFetching}
      inputValue={inputValue}
      onInputChange={(_event, value) => setInputValue(value)}
      getOptionLabel={(option) => (typeof option === 'string' ? option : option.title)}
      groupBy={(option) => option.type}
      onChange={(_event, value) => {
        if (value && typeof value !== 'string') {
          navigate(routeFor(value))
          setInputValue('')
        }
      }}
      renderOption={(props, option) => (
        <li {...props} key={`${option.type}-${option.key}`}>
          {TYPE_ICON[option.type]}
          <ListItemText sx={{ ml: 1 }} primary={option.title} secondary={option.subtitle} />
        </li>
      )}
      renderInput={(params) => (
        <TextField
          {...params}
          placeholder="Search payments, participants, settlements…"
          slotProps={{
            input: {
              ...params.InputProps,
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
              endAdornment: (
                <>
                  {isFetching && <CircularProgress size={14} />}
                  {params.InputProps.endAdornment}
                </>
              ),
            },
          }}
        />
      )}
    />
  )
}
