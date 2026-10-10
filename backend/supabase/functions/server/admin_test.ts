import { assertEquals } from "@std/assert";
import { createApp, type Services } from "./app.ts";
import { Tokens } from "./auth.ts";
import { Gemini } from "./gemini.ts";
import type { FeedbackDto, RouteDto, SubmitRouteRequest } from "./models.ts";
import {
  FEEDBACK_DAILY_LIMIT,
  type FeedbackStore,
  type RouteStore,
  type StoredUser,
  type UserStore,
} from "./stores.ts";

const routeBody = {
  name: "California",
  letter: "A",
  origin: "Centro",
  destination: "Huanchaco",
  fare: 2,
  durationMinutes: 40,
  schedule: "05:00–22:00",
  vehicleName: "Micro California",
  imageUrl: "https://example.com/micro.jpg",
  description: "Micro verde",
  bidirectional: false,
  stops: [{ name: "Centro", latitude: -8.11, longitude: -79.03 }, {
    name: "Huanchaco",
    latitude: -8.08,
    longitude: -79.12,
  }],
};

async function fixture() {
  const users = new Map<string, StoredUser>(["admin", "user"].map((id) => [id, {
    id,
    email: `${id}@correo.pe`,
    passwordHash: "",
    displayName: id,
    createdAt: 0,
    isAdmin: id === "admin",
  }]));
  const routes = new Map<string, RouteDto>();
  const feedback: (FeedbackDto & { userId: string })[] = [];
  const tokens = new Tokens("secreto-prueba-administracion");
  let uploads = 0;
  const services: Services = {
    users: { findById: (id: string) => Promise.resolve(users.get(id) ?? null) } as UserStore,
    routes: {
      adminAll: () => Promise.resolve([...routes.values()]),
      adminSave: (id: string | null, _authorId: string, req: SubmitRouteRequest, status: string) => {
        if (id && !routes.has(id)) return Promise.resolve(null);
        const route = {
          ...req,
          id: id ?? "ruta-test",
          status,
          accentIndex: 0,
          createdByUser: false,
          createdByMe: true,
        } as RouteDto;
        routes.set(route.id, route);
        return Promise.resolve(route);
      },
      delete: (id: string) => Promise.resolve(routes.delete(id)),
    } as unknown as RouteStore,
    alerts: {} as Services["alerts"],
    feedback: {
      add: (userId: string, subject: string, message: string, appVersion: string, device: string) => {
        if (feedback.filter((item) => item.userId === userId).length >= FEEDBACK_DAILY_LIMIT) {
          return Promise.resolve(null);
        }
        const id = `sugerencia-${feedback.length + 1}`;
        feedback.push({
          id,
          userId,
          subject,
          message,
          appVersion,
          device,
          createdAt: 0,
          userName: users.get(userId)?.displayName ?? null,
          userEmail: users.get(userId)?.email ?? null,
        });
        return Promise.resolve(id);
      },
      all: () => Promise.resolve(feedback.map(({ userId: _userId, ...item }) => item)),
      delete: (id: string) => {
        const index = feedback.findIndex((item) => item.id === id);
        if (index >= 0) feedback.splice(index, 1);
        return Promise.resolve(index >= 0);
      },
    } as unknown as FeedbackStore,
    tokens,
    gemini: new Gemini(""),
    images: {
      upload: () => {
        uploads++;
        return Promise.resolve("https://example.com/subida.png");
      },
    },
  };
  const app = createApp(services);
  const admin = await tokens.issue("admin", "Admin");
  const user = await tokens.issue("user", "Usuario");
  function call(path: string, method = "GET", token?: string, body?: unknown) {
    return app.request(`/server${path}`, {
      method,
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        "Content-Type": "application/json",
      },
      body: body && method !== "GET" ? JSON.stringify(body) : undefined,
    });
  }
  return { call, app, admin, user, uploads: () => uploads };
}

Deno.test("administración rechaza anónimos y usuarios sin permisos en todos los caminos", async () => {
  const { call, user } = await fixture();
  for (
    const [path, method] of [
      ["/api/admin/me", "GET"],
      ["/api/admin/routes", "GET"],
      ["/api/admin/routes", "POST"],
      ["/api/admin/routes/ruta", "PUT"],
      ["/api/admin/routes/ruta", "DELETE"],
      ["/api/admin/images", "POST"],
      ["/api/admin/feedback", "GET"],
      ["/api/admin/feedback/sugerencia-1", "DELETE"],
    ]
  ) {
    assertEquals((await call(path, method)).status, 401);
    assertEquals((await call(path, method, user, routeBody)).status, 403);
  }
});

Deno.test("administrador crea, edita, oculta y elimina rutas con foto y micro", async () => {
  const { call, admin } = await fixture();
  assertEquals((await call("/api/admin/me", "GET", admin)).status, 200);
  const created = await call("/api/admin/routes", "POST", admin, routeBody);
  assertEquals(created.status, 201);
  const route = await created.json();
  assertEquals(route.vehicleName, "Micro California");
  assertEquals(route.bidirectional, false);
  const updated = await call(`/api/admin/routes/${route.id}`, "PUT", admin, {
    ...routeBody,
    status: "REJECTED",
    fare: 3,
  });
  assertEquals((await updated.json()).status, "REJECTED");
  const catalog = await (await call("/api/admin/routes", "GET", admin)).json();
  assertEquals(catalog.routes.length, 1);
  assertEquals(catalog.routes[0].fare, 3);
  assertEquals((await call(`/api/admin/routes/${route.id}`, "DELETE", admin)).status, 204);
  assertEquals((await call(`/api/admin/routes/${route.id}`, "PUT", admin, routeBody)).status, 404);
});

Deno.test("el buzón guarda sugerencias con sesión y el administrador las lee y las borra", async () => {
  const { call, admin, user } = await fixture();
  const sugerencia = { subject: "Rutas", message: "  Falta la ruta a Moche  ", appVersion: "1.0", device: "Pixel 8" };

  assertEquals((await call("/api/feedback", "POST", undefined, sugerencia)).status, 401);
  assertEquals((await call("/api/feedback", "POST", user, { ...sugerencia, message: "   " })).status, 400);
  assertEquals((await call("/api/feedback", "POST", user, { ...sugerencia, message: "x".repeat(1001) })).status, 400);
  assertEquals((await call("/api/feedback", "POST", user, { message: "Sin asunto" })).status, 400);

  const sent = await call("/api/feedback", "POST", user, sugerencia);
  assertEquals(sent.status, 201);
  const { id } = await sent.json();

  const list = await (await call("/api/admin/feedback", "GET", admin)).json();
  assertEquals(list.feedback.length, 1);
  assertEquals(list.feedback[0].message, "Falta la ruta a Moche");
  assertEquals(list.feedback[0].userEmail, "user@correo.pe");

  assertEquals((await call(`/api/admin/feedback/${id}`, "DELETE", admin)).status, 204);
  assertEquals((await call(`/api/admin/feedback/${id}`, "DELETE", admin)).status, 404);
});

Deno.test("una cuenta no puede llenar el buzón en un día", async () => {
  const { call, user } = await fixture();
  const sugerencia = { subject: "General", message: "Hola" };
  for (let i = 0; i < FEEDBACK_DAILY_LIMIT; i++) {
    assertEquals((await call("/api/feedback", "POST", user, sugerencia)).status, 201);
  }
  assertEquals((await call("/api/feedback", "POST", user, sugerencia)).status, 429);
});

Deno.test("validación de rutas rechaza coordenadas, estados e imágenes inseguras", async () => {
  const { call, admin } = await fixture();
  for (
    const patch of [
      { imageUrl: "javascript:alert(1)" },
      { imageUrl: "http://example.com/a.png" },
      { status: "INVENTADO" },
      { stops: [{ name: "Mal", latitude: 91, longitude: 0 }, routeBody.stops[1]] },
      { fare: -1 },
      { durationMinutes: 1.5 },
      { stops: [] },
    ]
  ) {
    assertEquals((await call("/api/admin/routes", "POST", admin, { ...routeBody, ...patch })).status, 400);
  }
});

Deno.test("preflight CORS permite la web sin sesión", async () => {
  const { app } = await fixture();
  const response = await app.request("/server/api/admin/routes", {
    method: "OPTIONS",
    headers: { Origin: "https://panel.example.com" },
  });
  assertEquals(response.status, 204);
  assertEquals(response.headers.get("Access-Control-Allow-Origin"), "*");
});

Deno.test("subida de fotos exige formato y firma válidos", async () => {
  const { app, admin, uploads } = await fixture();
  const headers = { Authorization: `Bearer ${admin}`, "Content-Type": "image/png" };
  assertEquals((await app.request("/server/api/admin/images", { method: "POST", headers, body: "falso" })).status, 400);
  const png = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0]);
  const uploaded = await app.request("/server/api/admin/images", { method: "POST", headers, body: png });
  assertEquals(uploaded.status, 201);
  assertEquals((await uploaded.json()).url, "https://example.com/subida.png");
  assertEquals(uploads(), 1);
});
