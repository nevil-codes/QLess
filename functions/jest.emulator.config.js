// Trigger tests need the Functions + Firestore emulators; run via `npm run test:emulator`.
module.exports = {
  testMatch: ["<rootDir>/test/emulator/**/*.test.js"],
  testTimeout: 30000,
  // Suites share one emulator database; expiry sweeps would otherwise race
  // with other suites' in-flight reservations.
  maxWorkers: 1,
};
