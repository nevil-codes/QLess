const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { getFirestore } = require("firebase-admin/firestore");

const api = require("./api");

function callable(handler) {
  return onCall(async (request) => {
    try {
      return await handler(getFirestore(), request);
    } catch (err) {
      if (err instanceof api.InvalidArgument) throw new HttpsError("invalid-argument", err.message);
      throw err;
    }
  });
}

// Signed-out users get the popularity-based cold-start list.
exports.getRecommendations = callable((db, request) =>
  api.getRecommendations(db, request.auth ? request.auth.uid : null, request.data));

exports.getSimilarProducts = callable((db, request) =>
  api.getSimilarProducts(db, request.data));

exports.getPeopleAlsoBuy = callable((db, request) =>
  api.getPeopleAlsoBuy(db, request.data));
