const { initializeApp } = require("firebase-admin/app");
const { setGlobalOptions } = require("firebase-functions/v2");

initializeApp();

// Firestore is in eur3; triggers must run in a matching European region.
setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

const triggers = require("./recs/triggers");
const callables = require("./recs/callables");
const reservations = require("./reservations/callables");

exports.onUserEventCreated = triggers.onUserEventCreated;
exports.onReservationUpdated = triggers.onReservationUpdated;

exports.getRecommendations = callables.getRecommendations;
exports.getSimilarProducts = callables.getSimilarProducts;
exports.getPeopleAlsoBuy = callables.getPeopleAlsoBuy;

exports.createReservation = reservations.createReservation;
