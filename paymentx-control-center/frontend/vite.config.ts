// ENGLISH: The Vite build/dev-server configuration for the Control
// Center frontend. What it does: registers the React plugin (JSX/Fast
// Refresh support) and sets the dev server to port 5173 - the exact
// origin the backend's CorsConfig already allows by default. Why it
// exists: every Vite project needs this file; port/plugin choices here
// are not arbitrary, they're the other half of the CORS contract
// defined in backend/src/main/java/.../config/CorsConfig.java. How it
// will communicate with the backend: indirectly - this is what makes
// `npm run dev` serve the app at the origin the backend trusts.
//
// HINGLISH: Control Center frontend ki Vite build/dev-server
// configuration. Ye kya karti hai: React plugin (JSX/Fast Refresh
// support) register karti hai aur dev server ko port 5173 par set
// karti hai - exactly wahi origin jo backend ka CorsConfig already
// default se allow karta hai. Ye dashboard me kyu hai: har Vite
// project ko ye file chahiye; yahan port/plugin choices arbitrary
// nahi hain, ye backend/src/main/java/.../config/CorsConfig.java me
// define hue CORS contract ka doosra half hain. Backend se kaise
// connect hogi: indirectly - yehi wajah hai ki `npm run dev` app ko
// usi origin par serve karta hai jise backend trust karta hai.
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
  build: {
    rollupOptions: {
      output: {
        // ENGLISH: Phase 6 performance hardening - splits the heaviest, rarely-changing vendor libraries into
        // their own cacheable chunks instead of one ~700KB bundle every route load pulls in full. Recharts
        // (only used by Metrics) already stays out of the initial load via router.tsx's per-route React.lazy;
        // this split targets the libraries every page loads (React, MUI, React Query/Axios) so a browser that
        // has already cached them doesn't re-download them on every deploy that only touches page code.
        // HINGLISH: Phase 6 performance hardening - sabse bhaari, rarely-changing vendor libraries ko apne
        // alag cacheable chunks me split karta hai, ek ~700KB bundle ke bajaye jo har route load pura khींchta
        // hai. Recharts (sirf Metrics use karta hai) already router.tsx ke per-route React.lazy se initial
        // load se bahar rehta hai; ye split un libraries ko target karta hai jo har page load karta hai
        // (React, MUI, React Query/Axios) taaki ek browser jisne unhe already cache kar liya hai, har deploy
        // par unhe dobara download na kare jo sirf page code touch karta hai.
        manualChunks: {
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
          'vendor-mui': ['@mui/material', '@mui/icons-material'],
          'vendor-data': ['@tanstack/react-query', 'axios'],
        },
      },
    },
  },
})
