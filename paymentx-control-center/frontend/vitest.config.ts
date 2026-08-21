// ENGLISH: The Vitest configuration for this frontend's Phase 3.1 test
// suite. What it does: runs tests in a jsdom environment (so
// components can actually render/query the DOM) and loads
// src/testSetup.ts (jest-dom matchers) before every test file. Why it
// exists: this repository had zero frontend test infrastructure before
// Phase 3.1 - this phase's explicit "Add appropriate tests" requirement
// (component rendering, chat input, loading/error states) needed a real
// runner, and Vitest is the natural choice since Vite already builds
// this project (zero extra bundler config, unlike introducing Jest).
// How it will communicate with the backend: N/A - test-only
// configuration, no runtime backend calls.
//
// HINGLISH: Is frontend ki Phase 3.1 test suite ke liye Vitest
// configuration. Ye kya karti hai: tests ko ek jsdom environment me
// chalata hai (taaki components actually DOM render/query kar sakein)
// aur har test file se pehle src/testSetup.ts (jest-dom matchers) load
// karta hai. Ye dashboard me kyu hai: Phase 3.1 se pehle is repository
// me zero frontend test infrastructure tha - is phase ka explicit "Add
// appropriate tests" requirement (component rendering, chat input,
// loading/error states) ko ek real runner chahiye tha, aur Vitest
// natural choice hai kyunki Vite already is project ko build karta hai
// (zero extra bundler config, Jest introduce karne ke ulat). Backend se
// kaise connect hogi: N/A - test-only configuration, koi runtime
// backend calls nahi.
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/testSetup.ts'],
    globals: true,
  },
})
