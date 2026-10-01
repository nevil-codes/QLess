const fs = require("fs");
const path = require("path");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, addDoc, collection, getDocs, query, where,
} = require("firebase/firestore");

const RULES = fs.readFileSync(path.join(__dirname, "../../../firestore.rules"), "utf8");

let env;
const shopper = () => env.authenticatedContext("shopper").firestore();
const other = () => env.authenticatedContext("other").firestore();
const admin = () => env.authenticatedContext("admin", { admin: true }).firestore();
const anon = () => env.unauthenticatedContext().firestore();

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-qless-rules",
    firestore: { rules: RULES },
  });
});

afterAll(() => env.cleanup());

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "products/p1"), { name: "Milk" });
    await setDoc(doc(db, "stores/s1"), { name: "FreshHub" });
    await setDoc(doc(db, "config/app"), { minVersion: 1 });
    await setDoc(doc(db, "users/shopper"), { firstName: "Sam" });
    await setDoc(doc(db, "users/other"), { firstName: "Olu" });
    await setDoc(doc(db, "reservations/r1"), { userId: "shopper", status: "reserved" });
    await setDoc(doc(db, "orders/o1"), { userId: "shopper", status: "open" });
    await setDoc(doc(db, "user_events/e1"), { userId: "shopper", eventType: "view" });
    await setDoc(doc(db, "notifications/n1"), { userId: "shopper", text: "hi" });
  });
});

describe("catalog (products, stores, config)", () => {
  for (const path of ["products/p1", "stores/s1", "config/app"]) {
    test(`${path}: anyone can read`, async () => {
      await assertSucceeds(getDoc(doc(anon(), path)));
    });

    test(`${path}: shopper cannot write`, async () => {
      await assertFails(updateDoc(doc(shopper(), path), { name: "x" }));
      await assertFails(deleteDoc(doc(shopper(), path)));
    });

    test(`${path}: admin can write`, async () => {
      await assertSucceeds(updateDoc(doc(admin(), path), { name: "x" }));
    });
  }

  test("shopper cannot create products", async () => {
    await assertFails(addDoc(collection(shopper(), "products"), { name: "Fake" }));
  });

  test("admin claim set to false is not admin", async () => {
    const notAdmin = env.authenticatedContext("x", { admin: false }).firestore();
    await assertFails(updateDoc(doc(notAdmin, "products/p1"), { name: "x" }));
  });
});

describe("users", () => {
  test("owner can read and update own profile", async () => {
    await assertSucceeds(getDoc(doc(shopper(), "users/shopper")));
    await assertSucceeds(updateDoc(doc(shopper(), "users/shopper"), { firstName: "S" }));
  });

  test("users cannot read each other", async () => {
    await assertFails(getDoc(doc(other(), "users/shopper")));
  });

  test("admin can read any profile", async () => {
    await assertSucceeds(getDoc(doc(admin(), "users/shopper")));
    await assertSucceeds(getDocs(collection(admin(), "users")));
  });

  test("owner cannot set noShowCount", async () => {
    await assertFails(updateDoc(doc(shopper(), "users/shopper"), { noShowCount: 0 }));
    await assertFails(setDoc(doc(env.authenticatedContext("new").firestore(), "users/new"), {
      firstName: "N", noShowCount: 0,
    }));
  });

  test("new user can create own profile", async () => {
    const db = env.authenticatedContext("new").firestore();
    await assertSucceeds(setDoc(doc(db, "users/new"), { firstName: "N" }));
  });
});

describe("reservations", () => {
  test("owner can read own, others cannot", async () => {
    await assertSucceeds(getDoc(doc(shopper(), "reservations/r1")));
    await assertFails(getDoc(doc(other(), "reservations/r1")));
  });

  test("owner can list own reservations with a userId filter", async () => {
    const q = query(collection(shopper(), "reservations"), where("userId", "==", "shopper"));
    await assertSucceeds(getDocs(q));
  });

  test("shopper cannot list all reservations", async () => {
    await assertFails(getDocs(collection(shopper(), "reservations")));
  });

  test("admin can list and update all reservations", async () => {
    await assertSucceeds(getDocs(collection(admin(), "reservations")));
    await assertSucceeds(updateDoc(doc(admin(), "reservations/r1"), { status: "picked_up" }));
  });

  test("owner can create and update own, but not reassign it", async () => {
    await assertSucceeds(setDoc(doc(shopper(), "reservations/r2"), { userId: "shopper" }));
    await assertSucceeds(updateDoc(doc(shopper(), "reservations/r1"), { status: "cancelled" }));
    await assertFails(updateDoc(doc(shopper(), "reservations/r1"), { userId: "other" }));
  });

  test("cannot create a reservation for someone else", async () => {
    await assertFails(setDoc(doc(other(), "reservations/r3"), { userId: "shopper" }));
  });

  test("other user cannot update or delete", async () => {
    await assertFails(updateDoc(doc(other(), "reservations/r1"), { status: "cancelled" }));
    await assertFails(deleteDoc(doc(other(), "reservations/r1")));
  });
});

describe("orders", () => {
  test("owner reads own; only admin updates", async () => {
    await assertSucceeds(getDoc(doc(shopper(), "orders/o1")));
    await assertFails(getDoc(doc(other(), "orders/o1")));
    await assertFails(updateDoc(doc(shopper(), "orders/o1"), { status: "completed" }));
    await assertSucceeds(updateDoc(doc(admin(), "orders/o1"), { status: "completed" }));
  });
});

describe("user_events", () => {
  test("users log their own events only", async () => {
    const ev = { userId: "shopper", eventType: "view" };
    await assertSucceeds(addDoc(collection(shopper(), "user_events"), ev));
    await assertFails(addDoc(collection(shopper(), "user_events"), { ...ev, userId: "other" }));
  });

  test("clients can log view, search and add_to_cart but not purchase", async () => {
    for (const eventType of ["view", "search", "add_to_cart"]) {
      await assertSucceeds(addDoc(collection(shopper(), "user_events"), { userId: "shopper", eventType }));
    }
    await assertFails(addDoc(collection(shopper(), "user_events"), { userId: "shopper", eventType: "purchase" }));
    await assertFails(addDoc(collection(shopper(), "user_events"), { userId: "shopper" }));
  });

  test("events are append-only and admin-read", async () => {
    await assertFails(getDoc(doc(shopper(), "user_events/e1")));
    await assertFails(updateDoc(doc(shopper(), "user_events/e1"), { eventType: "x" }));
    await assertFails(deleteDoc(doc(shopper(), "user_events/e1")));
    await assertSucceeds(getDocs(collection(admin(), "user_events")));
  });
});

describe("user_profiles", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "user_profiles/shopper"), { categoryPrefs: { Dairy: 1 } });
    });
  });

  test("owner and admin can read; nobody else", async () => {
    await assertSucceeds(getDoc(doc(shopper(), "user_profiles/shopper")));
    await assertSucceeds(getDoc(doc(admin(), "user_profiles/shopper")));
    await assertFails(getDoc(doc(other(), "user_profiles/shopper")));
  });

  test("clients cannot write profiles, even their own", async () => {
    await assertFails(setDoc(doc(shopper(), "user_profiles/shopper"), { categoryPrefs: { X: 99 } }));
    await assertFails(updateDoc(doc(shopper(), "user_profiles/shopper"), { avgPrice: 1 }));
  });
});

describe("notifications", () => {
  test("only admins create; recipient reads", async () => {
    await assertFails(addDoc(collection(other(), "notifications"), { userId: "shopper" }));
    await assertSucceeds(addDoc(collection(admin(), "notifications"), { userId: "shopper" }));
    await assertSucceeds(getDoc(doc(shopper(), "notifications/n1")));
    await assertFails(getDoc(doc(other(), "notifications/n1")));
  });
});
