/**
 * ENGLISH: The Redux Toolkit store configuration. What it does:
 * combines every slice (currently just theme) into one store, and
 * exports the RootState/AppDispatch types every component's typed
 * hooks (useAppSelector/useAppDispatch) rely on. Why it exists: a
 * single, centrally-configured store is required even with just one
 * slice - this is where a Phase-2 slice would be added if a genuine
 * cross-cutting client-state need arises (most Phase 2 data will stay
 * in TanStack Query, not here). How it will communicate with the
 * backend: N/A - this store holds no server data, only client UI
 * preference state.
 *
 * HINGLISH: Redux Toolkit store configuration. Ye kya karti hai: har
 * slice (abhi sirf theme) ko ek store me combine karta hai, aur
 * RootState/AppDispatch types export karta hai jin par har component
 * ke typed hooks (useAppSelector/useAppDispatch) depend karte hain.
 * Ye dashboard me kyu hai: ek hi slice ke saath bhi ek single,
 * centrally-configured store chahiye - yahin par Phase 2 ka koi slice
 * add hoga agar genuinely cross-cutting client-state ki zaroorat aaye
 * (zyadatar Phase 2 data yahan nahi, TanStack Query me rahega).
 * Backend se kaise connect hogi: N/A - is store me koi server data
 * nahi hai, sirf client UI preference state hai.
 */
import { configureStore } from '@reduxjs/toolkit'
import themeReducer from '../features/theme/themeSlice'

export const store = configureStore({
  reducer: {
    theme: themeReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
