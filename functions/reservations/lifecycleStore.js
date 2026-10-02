// Ending reservations: expiry, cancellation, abandoned checkouts, refunds.
//
// Each ending commits status, stock release and any missed-pickup strike in
// one transaction first. Refunds go to Stripe afterwards with an idempotency
// key per reservation; if Stripe fails, the reservation stays
// "refund_pending" and retryRefunds picks it up on the next run.

const { FieldValue } = require("firebase-admin/firestore");

const { ReservationError, releaseStock } = require("./plan");
const {
  planCancel, planExpiry, isAbandonedCheckout, ABANDONED_CHECKOUT_MS,
} = require("./lifecycle");
const { cancelPending } = require("./store");

const BATCH_LIMIT = 200;

// Runs `plan(reservation)` in a transaction and applies it. Returns
// { reservation, outcome } or null when the plan says there's nothing to do.
async function applyEnding(db, reservationId, plan) {
  const ref = db.doc(`reservations/${reservationId}`);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const reservation = snap.exists ? snap.data() : null;
    const outcome = plan(reservation);
    if (!outcome) return null;

    const productIds = [...new Set((reservation.items || []).map((i) => i.productId))];
    const productSnaps = productIds.length
      ? await tx.getAll(...productIds.map((id) => db.doc(`products/${id}`)))
      : [];
    const products = Object.fromEntries(productSnaps.filter((s) => s.exists).map((s) => [s.id, s.data()]));

    const update = { ...outcome.update };
    if (outcome.refundCents > 0) {
      update.refundCents = outcome.refundCents;
      update.refundTargetStatus = outcome.finalPaymentStatus;
    }
    tx.update(ref, update);
    for (const [productId, prices] of Object.entries(releaseStock(reservation, products))) {
      tx.update(db.doc(`products/${productId}`), { prices });
    }
    if (outcome.noShow) {
      tx.set(db.doc(`users/${reservation.userId}`), { noShowCount: FieldValue.increment(1) }, { merge: true });
    }
    return { reservation: { ...reservation, ...update }, outcome };
  });
}

// Issues the refund recorded on a reservation in "refund_pending".
// Returns true when Stripe accepted it.
async function refund(db, stripe, reservationId, reservation) {
  if (reservation.paymentStatus !== "refund_pending" || !(reservation.refundCents > 0)) return false;
  if (!/^pi_[A-Za-z0-9]+$/.test(reservation.paymentIntentId || "")) {
    // Nothing real to refund (e.g. a pre-Stripe simulated payment): stop
    // retrying instead of failing every sweep.
    await db.doc(`reservations/${reservationId}`).update({ paymentStatus: "not_refundable" });
    return false;
  }
  try {
    const result = await stripe.refunds.create(
      { payment_intent: reservation.paymentIntentId, amount: reservation.refundCents },
      { idempotencyKey: `refund-${reservationId}` },
    );
    await db.doc(`reservations/${reservationId}`).update({
      paymentStatus: reservation.refundTargetStatus,
      refundId: result.id,
      refundedAt: Date.now(),
    });
    return true;
  } catch (err) {
    // console.error reaches Cloud Logging; see store.js for why not logger.
    console.error("Refund failed; will retry", { reservationId, error: err.message });
    return false;
  }
}

// Expires every reserved reservation whose pickup window has ended.
async function expireDue(db, { stripe, now = Date.now() } = {}) {
  const due = await db.collection("reservations")
    .where("status", "==", "reserved")
    .where("pickupDeadline", "<=", now)
    .limit(BATCH_LIMIT)
    .get();

  const result = { expired: 0, strikes: 0, refunded: 0 };
  for (const doc of due.docs) {
    const ended = await applyEnding(db, doc.id, (r) => (r ? planExpiry(r, now) : null));
    if (!ended) continue;
    result.expired += 1;
    if (ended.outcome.noShow) result.strikes += 1;
    if (await refund(db, stripe, doc.id, ended.reservation)) result.refunded += 1;
  }
  return result;
}

// Card checkouts nobody finished (sheet left open, app killed): cancel the
// PaymentIntent and give the stock back. A payment that did go through is
// left for the webhook to mark as paid.
async function cancelAbandoned(db, { stripe, now = Date.now() } = {}) {
  const stale = await db.collection("reservations")
    .where("status", "==", "pending_payment")
    .where("createdAt", "<=", now - ABANDONED_CHECKOUT_MS)
    .limit(BATCH_LIMIT)
    .get();

  const byIntent = {};
  for (const doc of stale.docs) {
    const r = doc.data();
    if (!isAbandonedCheckout(r, now)) continue;
    (byIntent[r.paymentIntentId || ""] = byIntent[r.paymentIntentId || ""] || []).push(doc.id);
  }

  let cancelled = 0;
  for (const [paymentIntentId, ids] of Object.entries(byIntent)) {
    if (paymentIntentId) {
      try {
        const intent = await stripe.paymentIntents.retrieve(paymentIntentId);
        if (intent.status === "succeeded") continue;
        if (intent.status !== "canceled") await stripe.paymentIntents.cancel(paymentIntentId);
      } catch (err) {
        console.error("Could not cancel abandoned PaymentIntent", { paymentIntentId, error: err.message });
        continue;
      }
    }
    cancelled += await cancelPending(db, ids, now);
  }
  return { cancelled };
}

// Retries refunds that failed on an earlier run.
async function retryRefunds(db, { stripe } = {}) {
  const pending = await db.collection("reservations")
    .where("paymentStatus", "==", "refund_pending")
    .limit(BATCH_LIMIT)
    .get();
  let refunded = 0;
  for (const doc of pending.docs) {
    if (await refund(db, stripe, doc.id, doc.data())) refunded += 1;
  }
  return { refunded };
}

// Owner cancels before the deadline.
async function cancelReservation(db, uid, data, { stripe, now = Date.now() } = {}) {
  const reservationId = data && data.reservationId;
  if (typeof reservationId !== "string" || !/^RES-[\w-]+$/.test(reservationId)) {
    throw new ReservationError("invalid-argument", "reservationId is required");
  }
  const ended = await applyEnding(db, reservationId, (r) => planCancel(r, uid, now));
  const refunded = await refund(db, stripe, reservationId, ended.reservation);
  return {
    status: "cancelled",
    refundCents: ended.outcome.refundCents,
    refundStatus: ended.outcome.refundCents > 0 ? (refunded ? "refunded" : "pending") : "none",
  };
}

module.exports = { cancelAbandoned, cancelReservation, expireDue, retryRefunds };
