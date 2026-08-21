/**
 * ENGLISH: Typed wrappers around react-redux's useSelector/useDispatch.
 * What it does: bakes in RootState/AppDispatch so every call site gets
 * full autocomplete and type-checking instead of `unknown`. Why it
 * exists: standard Redux Toolkit + TypeScript convention - avoids every
 * component repeating `useSelector<RootState>`. How it will
 * communicate with the backend: N/A - pure Redux plumbing.
 *
 * HINGLISH: react-redux ke useSelector/useDispatch ke around typed
 * wrappers. Ye kya karti hai: RootState/AppDispatch bake in karta hai
 * taaki har call site ko full autocomplete aur type-checking mile,
 * `unknown` ke bajaye. Ye dashboard me kyu hai: standard Redux Toolkit
 * + TypeScript convention hai - har component ko `useSelector<
 * RootState>` repeat karne se bachata hai. Backend se kaise connect
 * hogi: N/A - pure Redux plumbing hai.
 */
import { useDispatch, useSelector, type TypedUseSelectorHook } from 'react-redux'
import type { RootState, AppDispatch } from './store'

export const useAppDispatch: () => AppDispatch = useDispatch
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector
