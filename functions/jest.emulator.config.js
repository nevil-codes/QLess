// Trigger tests need the Functions + Firestore emulators; run via `npm run test:emulator`.
module.exports = {
  testMatch: ["<rootDir>/test/emulator/**/*.test.js"],
  testTimeout: 30000,
};
