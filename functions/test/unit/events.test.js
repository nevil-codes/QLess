const { sanitizeEvent } = require("../../recs/events");

const NOW = Date.UTC(2026, 5, 1);

test("keeps a valid event", () => {
  const e = sanitizeEvent({
    userId: "u1", eventType: "add_to_cart", productId: "p1",
    category: " Dairy ", brand: "Acme", price: 1.99, timestamp: NOW - 1000,
  }, NOW);
  expect(e).toEqual({
    userId: "u1", eventType: "add_to_cart", productId: "p1",
    category: "Dairy", brand: "Acme", price: 1.99, timestamp: NOW - 1000,
  });
});

test("rejects unknown event types and missing users", () => {
  expect(sanitizeEvent({ userId: "u1", eventType: "hack" }, NOW)).toBeNull();
  expect(sanitizeEvent({ eventType: "view" }, NOW)).toBeNull();
  expect(sanitizeEvent({ userId: "u1", eventType: "toString" }, NOW)).toBeNull();
  expect(sanitizeEvent(null, NOW)).toBeNull();
});

test("zeroes implausible prices", () => {
  for (const price of [-5, "abc", 1e9, NaN, undefined]) {
    expect(sanitizeEvent({ userId: "u", eventType: "view", price }, NOW).price).toBe(0);
  }
});

test("replaces implausible timestamps with now", () => {
  const future = sanitizeEvent({ userId: "u", eventType: "view", timestamp: NOW + 3600e3 }, NOW);
  const ancient = sanitizeEvent({ userId: "u", eventType: "view", timestamp: 0 }, NOW);
  expect(future.timestamp).toBe(NOW);
  expect(ancient.timestamp).toBe(NOW);
});

test("truncates long text fields", () => {
  const e = sanitizeEvent({ userId: "u", eventType: "view", category: "x".repeat(500) }, NOW);
  expect(e.category).toHaveLength(100);
});
