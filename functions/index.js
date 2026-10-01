const { initializeApp } = require("firebase-admin/app");
const { setGlobalOptions } = require("firebase-functions/v2");

initializeApp();

// Firestore is in eur3; triggers must run in a matching European region.
setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

const triggers = require("./recs/triggers");

exports.onUserEventCreated = triggers.onUserEventCreated;
exports.onReservationUpdated = triggers.onReservationUpdated;
