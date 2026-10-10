import { assertEquals, assertExists } from "@std/assert";
import { createApp, type Services } from "./app.ts";
import { Tokens } from "./auth.ts";
import { Gemini } from "./gemini.ts";
import type { AlertStore, RouteStore, StoredUser, UserStore } from "./stores.ts";

// La API entera sin base de datos: los almacenes se sustituyen por versiones en
// memoria, así se prueban los caminos, las sesiones y los errores.

function fakeUsers(): UserStore {
  const byId = new Map<string, StoredUser>();
  const users = {
    findById: (id: string) => Promise.resolve(byId.get(id) ?? null),
    findByEmail: (email: string) =>
      Promise.resolve([...byId.values()].find((u) => u.email === email.trim().toLowerCase()) ?? null),
    create: async (email: string, passwordHash: string, displayName: string) => {
      if (await users.findByEmail(email)) return null;
      const user: StoredUser = {
        id: crypto.randomUUID(),
        email: email.trim().toLowerCase(),
        passwordHash,
        displayName,
        createdAt: Date.now(),
        isAdmin: false,
      };
      byId.set(user.id, user);
      return user;
    },
    updateDisplayName: (id: string, displayName: string) => {
      const user = byId.get(id);
      if (!user) return Promise.resolve(null);
      user.displayName = displayName;
      return Promise.resolve(user);
    },
  };
  return users as unknown as UserStore;
}

function testApp() {
  const services: Services = {
    users: fakeUsers(),
    alerts: {} as AlertStore,
    routes: { all: () => Promise.resolve([]) } as unknown as RouteStore,
    feedback: {} as Services["feedback"],
    tokens: new Tokens("secreto-de-prueba-muy-largo"),
    gemini: new Gemini(""),
  };
  const app = createApp(services);
  return (path: string, init: RequestInit & { json?: unknown; token?: string } = {}) => {
    const headers = new Headers(init.headers);
    if (init.json !== undefined) headers.set("Content-Type", "application/json");
    if (init.token) headers.set("Authorization", `Bearer ${init.token}`);
    const body = init.json !== undefined ? JSON.stringify(init.json) : init.body;
    return app.request(`/server${path}`, { ...init, headers, body });
  };
}

Deno.test("el servidor responde en /health", async () => {
  const res = await testApp()("/health");
  assertEquals(res.status, 200);
  assertEquals(await res.json(), { status: "ok" });
});

Deno.test("registro, inicio de sesión y quién soy", async () => {
  const call = testApp();
  const alta = { email: " Ana@Correo.pe ", password: "micro-2026", displayName: "  Ana   María " };

  const registro = await call("/api/auth/register", { method: "POST", json: alta });
  assertEquals(registro.status, 201);
  const sesion = await registro.json();
  assertEquals(sesion.user.email, "ana@correo.pe");
  assertEquals(sesion.user.displayName, "Ana María");

  const repetido = await call("/api/auth/register", { method: "POST", json: alta });
  assertEquals(repetido.status, 409);
  assertEquals((await repetido.json()).error, "correo_en_uso");

  const login = await call("/api/auth/login", {
    method: "POST",
    json: { email: "ana@correo.pe", password: "micro-2026" },
  });
  assertEquals(login.status, 200);
  const { token } = await login.json();
  assertExists(token);

  const yo = await call("/api/me", { token });
  assertEquals(yo.status, 200);
  assertEquals(await yo.json(), sesion.user);

  const cambio = await call("/api/me", { method: "POST", token, json: { displayName: "Anita" } });
  assertEquals((await cambio.json()).displayName, "Anita");
});

Deno.test("una contraseña equivocada no entra y no dice si el correo existe", async () => {
  const res = await testApp()("/api/auth/login", {
    method: "POST",
    json: { email: "nadie@correo.pe", password: "lo-que-sea" },
  });
  assertEquals(res.status, 401);
  assertEquals((await res.json()).error, "credenciales_invalidas");
});

Deno.test("escribir sin sesión devuelve 401 con el formato de la app", async () => {
  const call = testApp();
  const sinToken = await call("/api/alerts", { method: "POST", json: {} });
  assertEquals(sinToken.status, 401);
  assertEquals(await sinToken.json(), { error: "no_autorizado", message: "Inicia sesión para continuar" });

  const tokenFalso = await call("/api/me", { token: "esto-no-es-un-token" });
  assertEquals(tokenFalso.status, 401);
});

Deno.test("una petición mal formada es un 400 con mensaje", async () => {
  const call = testApp();
  const res = await call("/api/auth/register", { method: "POST", json: { email: "a@b.pe" } });
  assertEquals(res.status, 400);
  assertEquals((await res.json()).error, "peticion_invalida");

  const alertas = await call("/api/alerts?lat=-8.11");
  assertEquals(alertas.status, 400);
  assertEquals((await alertas.json()).message, "Falta lon");
});

Deno.test("sin clave de Gemini el asistente avisa y la moderación deja pasar", async () => {
  const call = testApp();
  const registro = await call("/api/auth/register", {
    method: "POST",
    json: { email: "luis@correo.pe", password: "micro-2026", displayName: "Luis" },
  });
  const { token } = await registro.json();

  const asistente = await call("/api/assistant", { method: "POST", token, json: { question: "¿Cómo llego?" } });
  assertEquals(asistente.status, 503);
  assertEquals((await asistente.json()).error, "asistente_desactivado");

  const moderacion = await call("/api/moderation", { method: "POST", token, json: { text: "Hay tráfico" } });
  assertEquals(await moderacion.json(), { allowed: true });
});
