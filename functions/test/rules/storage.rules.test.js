const fs = require("fs");
const path = require("path");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");
const { ref, uploadString, getMetadata } = require("firebase/storage");

const RULES = fs.readFileSync(path.join(__dirname, "../../../storage.rules"), "utf8");

let env;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-qless-rules",
    storage: { rules: RULES },
  });
});

afterAll(() => env.cleanup());

beforeEach(async () => {
  await env.clearStorage();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await uploadString(ref(ctx.storage(), "products/milk.jpg"), "img");
  });
});

test("anyone can read product images", async () => {
  const storage = env.unauthenticatedContext().storage();
  await assertSucceeds(getMetadata(ref(storage, "products/milk.jpg")));
});

test("shoppers cannot upload product images", async () => {
  const storage = env.authenticatedContext("shopper").storage();
  await assertFails(uploadString(ref(storage, "products/fake.jpg"), "img"));
});

test("admins can upload product images", async () => {
  const storage = env.authenticatedContext("admin", { admin: true }).storage();
  await assertSucceeds(uploadString(ref(storage, "products/new.jpg"), "img"));
});
