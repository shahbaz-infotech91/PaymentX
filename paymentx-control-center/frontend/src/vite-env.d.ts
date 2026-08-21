/// <reference types="vite/client" />

// ENGLISH: Type declarations for the VITE_* environment variables this
// app actually reads. What it does: gives TypeScript a real type for
// import.meta.env.VITE_API_BASE_URL etc. instead of `any`. Why it
// exists: without this, every import.meta.env.VITE_* access is
// untyped and typos in variable names would silently become
// `undefined` at runtime with zero compile-time warning - exactly
// the class of bug "no TypeScript errors" is meant to catch. How it
// will communicate with the backend: these are the literal variable
// names src/api/axiosClient.ts reads to find this backend.
//
// HINGLISH: VITE_* environment variables ke liye type declarations
// jo ye app actually padhta hai. Ye kya karti hai: TypeScript ko
// import.meta.env.VITE_API_BASE_URL etc. ke liye ek real type deta
// hai, `any` ke bajaye. Ye dashboard me kyu hai: iske bina, har
// import.meta.env.VITE_* access untyped hota, aur variable naam me
// typo silently runtime par `undefined` ban jata bina kisi compile-
// time warning ke - exactly wahi bug class jise "no TypeScript
// errors" pakadne ke liye hai. Backend se kaise connect hogi: ye
// literal variable names hain jo src/api/axiosClient.ts padhta hai
// is backend ko dhundhne ke liye.
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_API_TIMEOUT_MS: string
  readonly VITE_APP_ENVIRONMENT: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
