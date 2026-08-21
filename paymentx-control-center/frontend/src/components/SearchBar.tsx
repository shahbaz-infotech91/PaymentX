/**
 * ENGLISH: A single-purpose search text field. What it does: a
 * controlled MUI TextField with a search icon adornment, calling
 * `onChange` on every keystroke - debouncing (if needed) is the
 * caller's concern, not baked in here, since not every future usage
 * will want the same delay. Why it exists: one of the 14 required
 * reusable components - every list/table page (Payments, Audit,
 * Logs, etc.) will need the same search-input look. How it will
 * communicate with the backend: N/A on its own - a future caller
 * feeds `value` into a real query's filter parameters.
 *
 * HINGLISH: Ek single-purpose search text field. Ye kya karti hai: ek
 * controlled MUI TextField hai search icon adornment ke saath, har
 * keystroke par `onChange` call karta hai - debouncing (agar chahiye)
 * caller ka concern hai, yahan baked in nahi hai, kyunki har future
 * usage same delay nahi chahega. Ye dashboard me kyu hai: 14 required
 * reusable components me se ek hai - har list/table page (Payments,
 * Audit, Logs, etc.) ko same search-input look chahiye hoga. Backend
 * se kaise connect hogi: khud se N/A - koi future caller `value` ko
 * ek real query ke filter parameters me feed karega.
 */
import { InputAdornment, TextField } from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'

interface SearchBarProps {
  value: string
  onChange: (value: string) => void
  placeholder?: string
}

export function SearchBar({ value, onChange, placeholder = 'Search…' }: SearchBarProps) {
  return (
    <TextField
      size="small"
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder={placeholder}
      sx={{ minWidth: 260 }}
      slotProps={{
        input: {
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon fontSize="small" />
            </InputAdornment>
          ),
        },
      }}
    />
  )
}
