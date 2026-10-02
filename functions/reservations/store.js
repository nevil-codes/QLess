// Writes reservations in one transaction so stock checks, stock holds and
// the reservation docs can't interleave with another checkout.

const { parseRequest, pickupCode, planPickupReservations } = require("./plan");

const CODE_ATTEMPTS = 10;

async function uniquePickupCode(tx, db, storeId, random) {
  for (let i = 0; i < CODE_ATTEMPTS; i++) {
    const code = pickupCode(random);
    const clash = await tx.get(db.collection("reservations")
      .where("storeId", "==", storeId)
      .where("pickupCode", "==", code)
      .where("status", "==", "reserved")
      .limit(1));
    if (clash.empty) return code;
  }
  throw new Error(`Could not find a free pickup code for store ${storeId}`);
}

async function createPickupReservations(db, uid, data, { now = Date.now(), random = Math.random } = {}) {
  const lines = parseRequest(data);
  const productIds = [...new Set(lines.map((l) => l.productId))];
  const storeIds = [...new Set(lines.map((l) => l.storeId))];

  return db.runTransaction(async (tx) => {
    const [userSnap, ...rest] = await tx.getAll(
      db.doc(`users/${uid}`),
      ...productIds.map((id) => db.doc(`products/${id}`)),
      ...storeIds.map((id) => db.doc(`stores/${id}`)),
    );
    const productSnaps = rest.slice(0, productIds.length);
    const storeSnaps = rest.slice(productIds.length);
    const byId = (snaps) => Object.fromEntries(snaps.filter((s) => s.exists).map((s) => [s.id, s.data()]));

    const codes = {};
    const ids = {};
    for (const storeId of storeIds) {
      codes[storeId] = await uniquePickupCode(tx, db, storeId, random);
      ids[storeId] = `RES-${now}-${db.collection("reservations").doc().id.slice(0, 6)}`;
    }

    const { reservations, newPrices } = planPickupReservations({
      uid,
      lines,
      products: byId(productSnaps),
      stores: byId(storeSnaps),
      user: userSnap.exists ? userSnap.data() : null,
      codes,
      ids,
      now,
    });

    for (const [productId, prices] of Object.entries(newPrices)) {
      tx.update(db.doc(`products/${productId}`), { prices });
    }
    for (const reservation of reservations) {
      tx.create(db.doc(`reservations/${reservation.reservationId}`), reservation);
    }

    return {
      reservations: reservations.map((r) => ({
        reservationId: r.reservationId,
        storeId: r.storeId,
        storeName: r.storeName,
        pickupCode: r.pickupCode,
        pickupDeadline: r.pickupDeadline,
        total: r.total,
      })),
    };
  });
}

module.exports = { createPickupReservations };
