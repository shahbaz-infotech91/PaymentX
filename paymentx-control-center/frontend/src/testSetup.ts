// ENGLISH: Loaded before every Vitest test file (see vitest.config.ts).
// What it does: registers @testing-library/jest-dom's matchers
// (toBeInTheDocument, toBeDisabled, etc.) globally. Why it exists:
// every *.test.tsx file in this app needs these matchers available
// without re-importing them - one place to add global test setup as
// the suite grows. How it will communicate with the backend: N/A.
//
// HINGLISH: Har Vitest test file se pehle load hota hai
// (vitest.config.ts dekho). Ye kya karti hai:
// @testing-library/jest-dom ke matchers (toBeInTheDocument,
// toBeDisabled, etc.) globally register karta hai. Ye dashboard me kyu
// hai: is app ki har *.test.tsx file ko ye matchers chahiye bina dobara
// import kiye - ek jagah global test setup add karne ke liye jab suite
// badhti hai. Backend se kaise connect hogi: N/A.
import '@testing-library/jest-dom/vitest'

// jsdom does not implement layout/scrolling APIs (see
// https://github.com/jsdom/jsdom/issues/1695) - ChatWindow.tsx calls the
// real scrollIntoView() on every new message (real auto-scroll behavior,
// not test-only code), so it needs a harmless no-op stand-in here rather
// than every test file re-defining one.
if (typeof Element !== 'undefined' && !Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {}
}
