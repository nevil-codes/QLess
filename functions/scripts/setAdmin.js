#!/usr/bin/env node
// Grant or revoke the `admin` custom claim for a user.
//
// Usage (from functions/), with credentials from either:
//   gcloud auth application-default login
// or a service-account key (Firebase Console > Project settings >
// Service accounts > Generate new private key), kept outside the repo:
//   export GOOGLE_APPLICATION_CREDENTIALS=~/keys/qless-admin.json
//
//   node scripts/setAdmin.js admin@qless.com
//   node scripts/setAdmin.js admin@qless.com --revoke
//
// The user must sign out and back in (or wait up to an hour) before the
// new claim shows up in their ID token.

const { initializeApp, applicationDefault } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");

const PROJECT_ID = "qless-1ccb9";

async function main() {
  const [email, flag] = process.argv.slice(2);
  if (!email || (flag && flag !== "--revoke")) {
    console.error("Usage: node scripts/setAdmin.js <email> [--revoke]");
    process.exit(1);
  }

  initializeApp({ credential: applicationDefault(), projectId: PROJECT_ID });
  const auth = getAuth();

  const user = await auth.getUserByEmail(email);
  const claims = { ...(user.customClaims || {}) };
  if (flag === "--revoke") {
    delete claims.admin;
  } else {
    claims.admin = true;
  }
  await auth.setCustomUserClaims(user.uid, claims);

  console.log(`${flag === "--revoke" ? "Revoked" : "Granted"} admin for ${email} (${user.uid})`);
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
