// What happens to a reservation when it runs out of time or the shopper
// cancels it. Pure: the store module applies the result in a transaction
// and issues any Stripe refund afterwards.

const { ReservationError } = require("./plan");

const EXPIRY_REFUND_RATE = 0.9; // prepaid but not collected: 10% fee kept
const ABANDONED_CHECKOUT_MS = 30 * 60 * 1000;

function toCents(amount) {
  return Math.round((Number(amount) || 0) * 100);
}

// Only payments that actually went through Stripe can be refunded.
// Reservations from before Stripe was integrated carry ids like
// "pi_simulated_<ts>" and never took real money.
function isRealPayment(reservation) {
  const id = reservation.paymentIntentId;
  return reservation.paymentStatus === "paid" && typeof id === "string" && /^pi_[A-Za-z0-9]+$/.test(id);
}

// Reserved past its deadline.
//   pickup: expired, counts as a missed pickup, nothing to refund
//   card:   expired, refunded minus the 10% fee
// Reservations from before payment methods existed (no paymentMethod) or
// with simulated payments just expire: no strike, nothing to refund.
function planExpiry(reservation, now) {
  if (reservation.status !== "reserved" || !(reservation.pickupDeadline <= now)) return null;
  if (reservation.paymentMethod === "card" && isRealPayment(reservation)) {
    return {
      update: { status: "expired", expiredAt: now, paymentStatus: "refund_pending" },
      refundCents: Math.round(toCents(reservation.total) * EXPIRY_REFUND_RATE),
      finalPaymentStatus: "partially_refunded",
      noShow: false,
    };
  }
  if (reservation.paymentMethod === "pickup") {
    return {
      update: { status: "expired", expiredAt: now, noShow: true },
      refundCents: 0,
      noShow: true,
    };
  }
  return { update: { status: "expired", expiredAt: now }, refundCents: 0, noShow: false };
}

// The shopper cancels before the deadline: no strike, prepaid is refunded
// in full (including the service fee).
function planCancel(reservation, uid, now) {
  if (!reservation || reservation.userId !== uid) {
    throw new ReservationError("not-found", "No such reservation.");
  }
  if (reservation.status !== "reserved") {
    throw new ReservationError("failed-precondition", "This reservation can no longer be cancelled.",
      { reason: "not-active", status: reservation.status });
  }
  if (reservation.pickupDeadline <= now) {
    throw new ReservationError("failed-precondition", "The pickup window has already ended.",
      { reason: "expired" });
  }
  if (reservation.paymentMethod === "card" && isRealPayment(reservation)) {
    return {
      update: { status: "cancelled", cancelledAt: now, paymentStatus: "refund_pending" },
      refundCents: toCents(reservation.total),
      finalPaymentStatus: "refunded",
    };
  }
  return { update: { status: "cancelled", cancelledAt: now }, refundCents: 0 };
}

function isAbandonedCheckout(reservation, now) {
  return reservation.status === "pending_payment" && reservation.createdAt <= now - ABANDONED_CHECKOUT_MS;
}

module.exports = {
  ABANDONED_CHECKOUT_MS,
  EXPIRY_REFUND_RATE,
  isAbandonedCheckout,
  isRealPayment,
  planCancel,
  planExpiry,
  toCents,
};
