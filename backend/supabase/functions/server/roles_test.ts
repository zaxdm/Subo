import { assert, assertEquals } from "@std/assert";
import { createApp, type Services } from "./app.ts";
import { hashPassword, Tokens } from "./auth.ts";
import { Gemini } from "./gemini.ts";
import type { StoredUser, UserStore } from "./stores.ts";

// Quién puede qué: clientes, administradores y el administrador principal.
// Los almacenes se sustituyen por versiones en memoria.

async function fixture() {
  const users = new Map<string, StoredUser>();
  const add = (id: string, patch: Partial<StoredUser> = {}) =>
    users.set(id, {
      id,
      email: `${id}@correo.pe`,
      passwordHash: null,
      displayName: id,
      createdAt: users.size,
      isAdmin: false,
      isSuperAdmin: false,
      ...patch,
    });
  add("principal", { isAdmin: true, isSuperAdmin: true });
  add("admin", { isAdmin: true });
  add("cliente", { passwordHash: await hashPassword("micro-2026") });

  const byEmail = (email: string) => [...users.values()].find((u) => u.email === email.trim().toLowerCase()) ?? null;
  const store = {
    findById: (id: string) => Promise.resolve(users.get(id) ?? null),
    findByEmail: (email: string) => Promise.resolve(byEmail(email)),
    create: (email: string, passwordHash: string, displayName: string) => {
      if (byEmail(email)) return Promise.resolve(null);
      add(`nuevo-${users.size}`, { email: email.trim().toLowerCase(), passwordHash, displayName });
      return Promise.resolve(byEmail(email));
    },
    admins: () => Promise.resolve([...users.values()].filter((u) => u.isAdmin)),
    setAdmin: (id: string, admin: boolean) => {
      const user = users.get(id);
      if (!user || user.isSuperAdmin) return Promise.resolve(null);
      user.isAdmin = admin;
      return Promise.resolve(user);
    },
    createAdmin: (email: string, passwordHash: string, displayName: string) => {
      if (byEmail(email)) return Promise.resolve(null);
      add(`admin-${users.size}`, { email: email.trim().toLowerCase(), passwordHash, displayName, isAdmin: true });
      return Promise.resolve(byEmail(email));
    },
  };

  const tokens = new Tokens("secreto-prueba-roles-muy-largo");
  const app = createApp({
    users: store as unknown as UserStore,
    tokens,
    gemini: new Gemini(""),
  } as unknown as Services);

  const token = (id: string) => tokens.issue(id, id);
  async function call(path: string, method = "GET", as?: string, body?: unknown) {
    return app.request(`/server${path}`, {
      method,
      headers: {
        ...(as ? { Authorization: `Bearer ${await token(as)}` } : {}),
        "Content-Type": "application/json",
      },
      body: body && method !== "GET" ? JSON.stringify(body) : undefined,
    });
  }
  return { call, users };
}

Deno.test("la sesión dice el rol de la cuenta", async () => {
  const { call } = await fixture();
  assertEquals((await (await call("/api/me", "GET", "cliente")).json()).role, "client");
  assertEquals((await (await call("/api/me", "GET", "admin")).json()).role, "admin");
  assertEquals((await (await call("/api/me", "GET", "principal")).json()).role, "super_admin");

  const login = await call("/api/auth/login", "POST", undefined, {
    email: "cliente@correo.pe",
    password: "micro-2026",
  });
  assertEquals((await login.json()).user.role, "client");
});

Deno.test("el registro público solo crea clientes, aunque pida otra cosa", async () => {
  const { call, users } = await fixture();
  const res = await call("/api/auth/register", "POST", undefined, {
    email: "listo@correo.pe",
    password: "micro-2026",
    displayName: "Listo",
    role: "super_admin",
    isAdmin: true,
    is_admin: true,
  });
  assertEquals(res.status, 201);
  assertEquals((await res.json()).user.role, "client");
  const created = [...users.values()].find((u) => u.email === "listo@correo.pe")!;
  assertEquals(created.isAdmin, false);
  assertEquals(created.isSuperAdmin, false);
});

Deno.test("un cliente no entra al panel con sus credenciales", async () => {
  const { call } = await fixture();
  for (const path of ["/api/admin/me", "/api/admin/admins", "/api/admin/routes"]) {
    assertEquals((await call(path, "GET", "cliente")).status, 403);
    assertEquals((await call(path)).status, 401);
  }
});

Deno.test("solo el administrador principal gestiona administradores", async () => {
  const { call } = await fixture();
  // Un administrador normal entra al panel, pero no reparte accesos.
  assertEquals((await call("/api/admin/me", "GET", "admin")).status, 200);
  assertEquals((await call("/api/admin/admins", "GET", "admin")).status, 403);
  assertEquals((await call("/api/admin/admins", "POST", "admin", { email: "cliente@correo.pe" })).status, 403);
  assertEquals((await call("/api/admin/admins/cliente", "DELETE", "admin")).status, 403);

  const list = await (await call("/api/admin/admins", "GET", "principal")).json();
  assertEquals(list.admins.map((a: { role: string }) => a.role).sort(), ["admin", "super_admin"]);
  // Nunca se cuentan contraseñas ni vínculos.
  assert(!("passwordHash" in list.admins[0]) && !("googleSubject" in list.admins[0]));
});

Deno.test("el principal convierte una cuenta en administradora y le quita el acceso", async () => {
  const { call, users } = await fixture();
  const promoted = await call("/api/admin/admins", "POST", "principal", { email: " Cliente@Correo.pe " });
  assertEquals(promoted.status, 200);
  assertEquals((await promoted.json()).role, "admin");
  assertEquals((await call("/api/admin/me", "GET", "cliente")).status, 200);

  assertEquals((await call("/api/admin/admins", "POST", "principal", { email: "cliente@correo.pe" })).status, 409);

  assertEquals((await call("/api/admin/admins/cliente", "DELETE", "principal")).status, 204);
  assertEquals(users.get("cliente")!.isAdmin, false);
  assertEquals((await call("/api/admin/me", "GET", "cliente")).status, 403);
});

Deno.test("el principal crea una cuenta administradora nueva", async () => {
  const { call } = await fixture();
  // Sin nombre ni contraseña no se puede crear: se explica qué falta.
  const missing = await call("/api/admin/admins", "POST", "principal", { email: "nueva@correo.pe" });
  assertEquals(missing.status, 404);
  assertEquals((await missing.json()).error, "cuenta_no_existe");

  const created = await call("/api/admin/admins", "POST", "principal", {
    email: "nueva@correo.pe",
    displayName: "  Nueva   Admin ",
    password: "micro-2026",
  });
  assertEquals(created.status, 201);
  const user = await created.json();
  assertEquals(user.role, "admin");
  assertEquals(user.displayName, "Nueva Admin");

  const login = await call("/api/auth/login", "POST", undefined, { email: "nueva@correo.pe", password: "micro-2026" });
  assertEquals((await login.json()).user.role, "admin");
});

Deno.test("al administrador principal nadie le quita el acceso", async () => {
  const { call, users } = await fixture();
  assertEquals((await call("/api/admin/admins/principal", "DELETE", "principal")).status, 409);
  assertEquals(users.get("principal")!.isAdmin, true);
  assertEquals((await call("/api/admin/admins/no-existe", "DELETE", "principal")).status, 409);
});
