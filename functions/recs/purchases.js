// Turning a picked-up reservation into purchase signals.

const MAX_BASKET = 100;

// Must be usable as a Firestore document id and map key.
function validProductId(id) {
  return typeof id === "string" && /^[^/.]{1,200}$/.test(id);
}

function isNewPickup(before, after) {
  return Boolean(after) && after.status === "picked_up" && (!before || before.status !== "picked_up");
}

function basketProductIds(reservation) {
  const ids = (reservation.items || [])
    .map((item) => item && item.productId)
    .filter(validProductId);
  return [...new Set(ids)].slice(0, MAX_BASKET);
}

// For each product, the other products it was bought with: { a: [b, c], ... }.
// Written as one doc per product so a basket of n items is n writes.
function coPurchaseUpdates(productIds) {
  const updates = {};
  if (productIds.length < 2) return updates;
  for (const id of productIds) {
    updates[id] = productIds.filter((other) => other !== id);
  }
  return updates;
}

// One purchase event per distinct product, enriched with the catalog's
// category and brand (reservation items only carry name and price).
function purchaseEvents(reservation, productsById, timestamp) {
  const prices = new Map();
  for (const item of reservation.items || []) {
    if (!item || !validProductId(item.productId)) continue;
    if (!prices.has(item.productId)) prices.set(item.productId, Number(item.price) || 0);
  }

  return [...prices].slice(0, MAX_BASKET).map(([productId, price]) => {
    const product = productsById[productId] || {};
    return {
      userId: reservation.userId,
      eventType: "purchase",
      productId,
      category: product.category || "",
      brand: product.brand || "",
      price,
      storeId: reservation.storeId || "",
      timestamp,
      source: "pickup",
    };
  });
}

module.exports = { isNewPickup, basketProductIds, coPurchaseUpdates, purchaseEvents };
