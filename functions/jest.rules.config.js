// Rules tests need the Firestore/Storage emulators; run via `npm run test:rules`.
module.exports = {
  testMatch: ["<rootDir>/test/rules/**/*.test.js"],
  testTimeout: 20000,
};
