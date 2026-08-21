// ENGLISH: Shared TypeScript types every feature/page/component in this
// app can import instead of redefining the same shapes. What it does:
// mirrors the backend's ApiResponse<T>/ErrorResponse envelope
// (see backend/src/main/java/.../dto/ApiResponse.java) plus small UI
// primitives (Status, TimeRange) reused across many components. Why it
// exists: keeps the response envelope's shape defined in exactly one
// place on the frontend, matching exactly one place on the backend -
// if the backend shape ever changes, this is the only file that needs
// updating. How it will communicate with the backend: ApiResponse<T>
// here is the TypeScript mirror of every JSON response this backend
// returns; src/api/axiosClient.ts unwraps responses into this shape.
//
// HINGLISH: Shared TypeScript types jo is app ka har feature/page/
// component import kar sakta hai, same shapes dobara define karne ke
// bajaye. Ye kya karti hai: backend ke ApiResponse<T>/ErrorResponse
// envelope ko mirror karta hai (backend/src/main/java/.../dto/
// ApiResponse.java dekho) plus chhote UI primitives (Status, TimeRange)
// jo kai components me reuse hote hain. Ye dashboard me kyu hai:
// response envelope ka shape frontend par exactly ek jagah define
// rakhta hai, backend par bhi exactly ek jagah ke saath match karta
// hai - agar backend ka shape kabhi badle, toh sirf yahi file update
// karni padegi. Backend se kaise connect hogi: yahan ApiResponse<T>
// har JSON response ka TypeScript mirror hai jo ye backend return
// karta hai; src/api/axiosClient.ts responses ko isi shape me unwrap
// karta hai.

export interface ApiErrorResponse {
  errorCode: string
  message: string
  path: string
}

export interface ApiResponse<T> {
  success: boolean
  data: T | null
  error: ApiErrorResponse | null
  timestamp: string
}

/** Generic health/connectivity status used across ServiceCard, StatusBadge, HealthIndicator. */
export type Status = 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN'

export interface TimeRange {
  label: string
  fromMinutesAgo: number
}
