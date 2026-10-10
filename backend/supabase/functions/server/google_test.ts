import { assertEquals, assertExists } from "@std/assert";
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from "jose";
import { type GoogleIdentity, GoogleTokens } from "./google.ts";
import { createApp, type Services } from "./app.ts";
import { hashPassword, passwordMatches, Tokens } from "./auth.ts";
import type { StoredUser, UserStore } from "./stores.ts";
import { Gemini } from "./gemini.ts";

const clientId = "ruta-norte.apps.googleusercontent.com";
const keys = await generateKeyPair("RS256");
const jwk = await exportJWK(keys.publicKey);
const verifier = new GoogleTokens([clientId], createLocalJWKSet({ keys: [{ ...jwk, kid: "test", alg: "RS256" }] }));
const testNonce = "n".repeat(64);
const oldPasswordHash = await hashPassword("mi-password-2026");

function token(overrides: Record<string, unknown> = {}) {
  const now = Math.floor(Date.now() / 1000);
  return new SignJWT({
    sub: "google-user",
    email: "ana@gmail.com",
    email_verified: true,
    name: "  Ana   María  ",
    nonce: testNonce,
    iss: "https://accounts.google.com",
    aud: clientId,
    iat: now,
    exp: now + 3600,
    ...overrides,
  }).setProtectedHeader({ alg: "RS256", kid: "test" }).sign(keys.privateKey);
}

Deno.test("Google verifica firma, audiencia, emisor, caducidad, correo y nonce", async () => {
  const identity = await verifier.verify(await token(), testNonce);
  assertEquals(identity, { subject: "google-user", email: "ana@gmail.com", displayName: "Ana María" });
  for (
    const patch of [
      { aud: "otra-app" },
      { iss: "https://attacker.example" },
      { exp: 1 },
      { nonce: "otro-nonce" },
      { email_verified: false },
      { sub: "" },
      { email: "sin-correo" },
    ]
  ) {
    assertEquals(await verifier.verify(await token(patch), testNonce), null);
  }
  const forgedKeys = await generateKeyPair("RS256");
  const forged = await new SignJWT({}).setProtectedHeader({ alg: "RS256", kid: "test" }).sign(forgedKeys.privateKey);
  assertEquals(await verifier.verify(forged, testNonce), null);
  assertEquals(await verifier.verify("no-es-un-token", testNonce), null);
});

Deno.test("Google exige exp incluso en un token firmado", async () => {
  const signed = await new SignJWT({
    sub: "google-user",
    email: "ana@gmail.com",
    email_verified: true,
    nonce: testNonce,
    iss: "https://accounts.google.com",
    aud: clientId,
    iat: Math.floor(Date.now() / 1000),
  })
    .setProtectedHeader({ alg: "RS256", kid: "test" }).sign(keys.privateKey);
  assertEquals(await verifier.verify(signed, testNonce), null);
  assertEquals(await new GoogleTokens([]).verify(await token(), testNonce), null);
  assertEquals(await passwordMatches("cualquier-password", null), false);
});

function fixture(existing?: StoredUser, enabled = true) {
  const users = new Map<string, StoredUser>();
  if (existing) users.set(existing.id, existing);
  const challenges = new Set<string>();
  const store = {
    findById: (id: string) => Promise.resolve(users.get(id) ?? null),
    findByEmail: (email: string) => Promise.resolve([...users.values()].find((u) => u.email === email) ?? null),
    findByGoogleSubject: (subject: string) =>
      Promise.resolve([...users.values()].find((u) => u.googleSubject === subject) ?? null),
    googleChallenge: () => {
      const nonce = crypto.randomUUID().replaceAll("-", "").repeat(2);
      challenges.add(nonce);
      return Promise.resolve(nonce);
    },
    consumeGoogleChallenge: (nonce: string) => Promise.resolve(challenges.delete(nonce)),
    createGoogle: (identity: GoogleIdentity) => {
      const user: StoredUser = {
        id: "new-user",
        email: identity.email,
        passwordHash: null,
        displayName: identity.displayName,
        googleSubject: identity.subject,
        createdAt: 0,
        isAdmin: false,
      };
      users.set(user.id, user);
      return Promise.resolve(user);
    },
    linkGoogle: (id: string, subject: string) => {
      const user = users.get(id)!;
      user.googleSubject = subject;
      return Promise.resolve(user);
    },
  };
  const services: Services = {
    users: store as unknown as UserStore,
    alerts: {} as Services["alerts"],
    routes: {} as Services["routes"],
    feedback: {} as Services["feedback"],
    tokens: new Tokens("secreto-interno-prueba-google"),
    gemini: new Gemini(""),
    google: enabled ? verifier : new GoogleTokens([]),
  };
  const app = createApp(services);
  const call = (path: string, body = {}) =>
    app.request(`/server${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  return { call, users };
}

Deno.test("Google desactivado rechaza acceso y desafíos sin alterar las cuentas", async () => {
  const { call, users } = fixture(undefined, false);
  assertEquals((await call("/api/auth/google/challenge")).status, 503);
  assertEquals((await call("/api/auth/google")).status, 503);
  assertEquals(users.size, 0);
});

Deno.test("Google crea usuario sin password ni permisos y no acepta reusar el intento", async () => {
  const { call, users } = fixture();
  const challenge = await call("/api/auth/google/challenge");
  assertEquals(challenge.headers.get("Cache-Control"), "no-store");
  const { nonce } = await challenge.json();
  const idToken = await token({ nonce });
  const login = await call("/api/auth/google", { idToken, nonce });
  assertEquals(login.status, 200);
  const result = await login.json();
  assertExists(result.token);
  assertEquals(result.user.displayName, "Ana María");
  assertEquals(users.get(result.user.id)?.passwordHash, null);
  assertEquals(users.get(result.user.id)?.isAdmin, false);
  assertEquals((await call("/api/auth/google", { idToken, nonce })).status, 401);
  const next = await (await call("/api/auth/google/challenge")).json();
  const again = await call("/api/auth/google", { nonce: next.nonce, idToken: await token({ nonce: next.nonce }) });
  assertEquals((await again.json()).user.id, result.user.id);
  assertEquals(users.size, 1);
});

Deno.test("Google no vincula una cuenta existente sin comprobar su password", async () => {
  const existing: StoredUser = {
    id: "local-admin",
    email: "ana@gmail.com",
    displayName: "Nombre elegido",
    passwordHash: oldPasswordHash,
    isAdmin: true,
    createdAt: 0,
  };
  const { call, users } = fixture(existing);
  for (const existingPassword of [undefined, "equivocada", "mi-password-2026"]) {
    const { nonce } = await (await call("/api/auth/google/challenge")).json();
    const res = await call("/api/auth/google", { nonce, idToken: await token({ nonce }), existingPassword });
    assertEquals(res.status, existingPassword === "mi-password-2026" ? 200 : 409);
    if (res.status === 200) {
      const data = await res.json();
      assertEquals(data.user.id, "local-admin");
      assertEquals(data.user.displayName, "Nombre elegido");
    } else assertEquals(users.get("local-admin")?.googleSubject, undefined);
  }
  assertEquals(users.size, 1);
  assertEquals(users.get("local-admin")?.isAdmin, true);
});

Deno.test("Google rechaza tokens sin un desafío emitido por el servidor", async () => {
  const { call, users } = fixture();
  assertEquals((await call("/api/auth/google", { nonce: testNonce, idToken: await token() })).status, 401);
  assertEquals(users.size, 0);
});
