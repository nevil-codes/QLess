const {
  ABANDONED_CHECKOUT_MS, isAbandonedCheckout, planCancel, planExpiry, toCents,
} = require("../../reservations/lifecycle");
const { ReservationError } = require("../../reservations/plan");

const NOW = Date.UTC(2026, 9, 2, 12);
const pickup = { userId: "u1", status: "reserved", paymentMethod: "pickup", total: 9.99, pickupDeadline: NOW - 1 };
const card = { userId: "u1", status: "reserved", paymentMethod: "card", total: 10.98, pickupDeadline: NOW - 1 };

describe("toCents", () => {
  test("avoids floating point drift", () => {
    expect(toCents(10.98)).toBe(1098);
    expect(toCents(0.1 + 0.2)).toBe(30);
    expect(toCents(undefined)).toBe(0);
  });
});

describe("planExpiry", () => {
  test("pickup: expired with a strike, nothing refunded", () => {
    expect(planExpiry(pickup, NOW)).toEqual({
      update: { status: "expired", expiredAt: NOW, noShow: true }, refundCents: 0, noShow: true,
    });
  });

  test("card: expired and refunded minus 10%", () => {
    expect(planExpiry(card, NOW)).toEqual({
      update: { status: "expired", expiredAt: NOW, paymentStatus: "refund_pending" },
      refundCents: 988,
      finalPaymentStatus: "partially_refunded",
      noShow: false,
    });
  });

  test("leaves reservations that aren't due or aren't reserved", () => {
    expect(planExpiry({ ...pickup, pickupDeadline: NOW + 1 }, NOW)).toBeNull();
    for (const status of ["pending_payment", "picked_up", "expired", "cancelled"]) {
      expect(planExpiry({ ...pickup, status }, NOW)).toBeNull();
    }
  });
});

describe("planCancel", () => {
  const live = (r) => ({ ...r, pickupDeadline: NOW + 1000 });

  test("pickup: cancelled without a strike or refund", () => {
    expect(planCancel(live(pickup), "u1", NOW)).toEqual({
      update: { status: "cancelled", cancelledAt: NOW }, refundCents: 0,
    });
  });

  test("card: full refund including the service fee", () => {
    expect(planCancel(live(card), "u1", NOW)).toEqual({
      update: { status: "cancelled", cancelledAt: NOW, paymentStatus: "refund_pending" },
      refundCents: 1098,
      finalPaymentStatus: "refunded",
    });
  });

  test.each([
    ["someone else's reservation", live(pickup), "u2", "not-found"],
    ["missing reservation", null, "u1", "not-found"],
    ["already picked up", { ...live(pickup), status: "picked_up" }, "u1", "failed-precondition"],
    ["already cancelled", { ...live(card), status: "cancelled" }, "u1", "failed-precondition"],
    ["after the deadline", pickup, "u1", "failed-precondition"],
  ])("rejects %s", (_, reservation, uid, code) => {
    expect(() => planCancel(reservation, uid, NOW)).toThrow(ReservationError);
    try { planCancel(reservation, uid, NOW); } catch (err) { expect(err.code).toBe(code); }
  });
});

describe("isAbandonedCheckout", () => {
  test("pending payments older than 30 minutes", () => {
    const pending = { status: "pending_payment", createdAt: NOW - ABANDONED_CHECKOUT_MS };
    expect(isAbandonedCheckout(pending, NOW)).toBe(true);
    expect(isAbandonedCheckout({ ...pending, createdAt: NOW - 60000 }, NOW)).toBe(false);
    expect(isAbandonedCheckout({ ...pending, status: "reserved" }, NOW)).toBe(false);
  });
});
