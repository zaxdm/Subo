import { type Context, Hono } from "hono";
import type { ContentfulStatusCode } from "hono/utils/http-status";
import * as AlertPolicy from "./alert_policy.ts";
import {
  cleanDisplayName,
  displayNameProblem,
  hashPassword,
  isPasswordValid,
  PASSWORD_MIN_LENGTH,
  passwordMatches,
  type Tokens,
} from "./auth.ts";
import { ASSISTANT_SCHEMA, assistantSystem, type Gemini, MODERATION_SCHEMA, moderationSystem } from "./gemini.ts";
import { isValidCoordinate } from "./geo.ts";
import type { CoordinateDto, SessionResponse, StopDto, SubmitRouteRequest, UserDto } from "./models.ts";
import type { AlertStore, FeedbackStore, RouteStore, StoredUser, UserStore } from "./stores.ts";
import type { ImageStorage } from "./images.ts";
import type { GoogleVerifier } from "./google.ts";

/** Todo lo que el servidor necesita para atender peticiones. */
export interface Services {
  google?: GoogleVerifier;
  images?: ImageStorage;
  users: UserStore;
  alerts: AlertStore;
  routes: RouteStore;
  feedback: FeedbackStore;
  tokens: Tokens;
  gemini: Gemini;
}

/** Petición mal formada: el mensaje sí se le muestra al usuario. */
export class BadRequest extends Error {}

/** Respuesta de error con el formato que espera la app: `{error, message}`. */
class ApiError extends Error {
  constructor(readonly status: ContentfulStatusCode, readonly code: string, message: string) {
    super(message);
  }
}

const unauthorized = () => new ApiError(401, "no_autorizado", "Inicia sesión para continuar");

type Env = { Variables: { user: StoredUser | null } };

/**
 * Monta el servidor. Supabase entrega las peticiones con el nombre de la
 * función delante (`/server/api/...`), así que la app apunta su BACKEND_URL a
 * `https://<proyecto>.supabase.co/functions/v1/server` y usa los mismos caminos
 * que con el servidor Ktor.
 */
export function createApp(services: Services, basePath = "/server") {
  const app = new Hono<Env>().basePath(basePath);

  app.onError((error, c) => {
    if (error instanceof ApiError) return c.json({ error: error.code, message: error.message }, error.status);
    if (error instanceof BadRequest) return c.json({ error: "peticion_invalida", message: error.message }, 400);
    // El detalle va al registro de la función, no al usuario.
    console.error(`Fallo atendiendo ${c.req.method} ${c.req.path}`, error);
    return c.json({ error: "error_servidor", message: "Algo falló en el servidor" }, 500);
  });

  app.notFound((c) => c.json({ error: "no_encontrada", message: "Ese camino no existe" }, 404));

  // Si viene sesión se identifica al usuario; si no, sigue como anónimo. Cada
  // camino decide después si la exige (requireUser) o solo la aprovecha.
  app.use("*", async (c, next) => {
    c.set("user", await userFromHeader(services, c.req.header("Authorization")));
    await next();
  });

  // La web usa el mismo JWT que Android. Sin cookies ni credenciales implícitas.
  app.use("*", async (c, next) => {
    c.header("Access-Control-Allow-Origin", "*");
    c.header("Access-Control-Allow-Headers", "Authorization, Content-Type");
    c.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
    if (c.req.method === "OPTIONS") return c.body(null, 204);
    await next();
  });

  app.get("/api/admin/me", (c) => c.json(userDto(requireAdmin(c))));

  // --- Administradores: solo el principal decide quién más lo es ---

  app.get("/api/admin/admins", async (c) => {
    requireSuperAdmin(c);
    return c.json({ admins: (await services.users.admins()).map(userDto) });
  });

  // Con una cuenta que ya existe basta el correo; si no existe, se crea con el
  // nombre y la contraseña que ponga el principal.
  app.post("/api/admin/admins", async (c) => {
    requireSuperAdmin(c);
    const body = await readBody(c);
    const email = requireString(body, "email").trim();
    if (!email.includes("@") || email.length < 5) throw new BadRequest("Escribe un correo válido");

    const existing = await services.users.findByEmail(email);
    if (existing) {
      if (existing.isAdmin) throw new ApiError(409, "ya_es_administrador", "Esa cuenta ya es administradora");
      const promoted = await services.users.setAdmin(existing.id, true);
      if (!promoted) throw new ApiError(409, "no_se_pudo", "No se pudo cambiar esa cuenta. Inténtalo de nuevo");
      return c.json(userDto(promoted));
    }

    const displayName = optionalString(body, "displayName") ?? "";
    const password = optionalString(body, "password") ?? "";
    const nameProblem = displayNameProblem(displayName);
    if (nameProblem || !isPasswordValid(password)) {
      throw new ApiError(
        404,
        "cuenta_no_existe",
        `Ese correo no tiene cuenta. Para crearla, escribe un nombre y una contraseña de al menos ${PASSWORD_MIN_LENGTH} caracteres`,
      );
    }
    const created = await services.users.createAdmin(
      email,
      await hashPassword(password),
      cleanDisplayName(displayName),
    );
    if (!created) throw new ApiError(409, "correo_en_uso", "Ese correo ya tiene una cuenta");
    return c.json(userDto(created), 201);
  });

  // Quitar el acceso no borra la cuenta: vuelve a ser cliente.
  app.delete("/api/admin/admins/:id", async (c) => {
    requireSuperAdmin(c);
    const demoted = await services.users.setAdmin(c.req.param("id"), false);
    if (!demoted) throw new ApiError(409, "no_se_puede_quitar", "A esa cuenta no se le puede quitar el acceso");
    return c.body(null, 204);
  });

  app.get("/api/admin/routes", async (c) => {
    requireAdmin(c);
    return c.json({ routes: await services.routes.adminAll() });
  });

  app.post("/api/admin/routes", async (c) => {
    const user = requireAdmin(c);
    const body = await readBody(c);
    return c.json(await services.routes.adminSave(null, user.id, parseRoute(body), routeStatus(body)), 201);
  });

  app.put("/api/admin/routes/:id", async (c) => {
    const user = requireAdmin(c);
    const body = await readBody(c);
    const route = await services.routes.adminSave(c.req.param("id"), user.id, parseRoute(body), routeStatus(body));
    if (!route) throw new ApiError(404, "no_encontrada", "Esa ruta no existe");
    return c.json(route);
  });

  app.delete("/api/admin/routes/:id", async (c) => {
    const user = requireAdmin(c);
    if (!await services.routes.delete(c.req.param("id"), user.id, true)) {
      throw new ApiError(404, "no_encontrada", "Esa ruta no existe");
    }
    return c.body(null, 204);
  });

  app.post("/api/admin/images", async (c) => {
    requireAdmin(c);
    const contentType = c.req.header("Content-Type")?.split(";")[0];
    if (!contentType || !["image/jpeg", "image/png", "image/webp"].includes(contentType)) {
      throw new BadRequest("Usa una imagen JPG, PNG o WebP");
    }
    if (Number(c.req.header("Content-Length")) > 5_242_880) throw new BadRequest("La imagen supera 5 MB");
    const reader = c.req.raw.body?.getReader();
    if (!reader) throw new BadRequest("Falta la imagen");
    const chunks: Uint8Array[] = [];
    let size = 0;
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > 5_242_880) {
        await reader.cancel();
        throw new BadRequest("La imagen supera 5 MB");
      }
      chunks.push(value);
    }
    const bytes = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) {
      bytes.set(chunk, offset);
      offset += chunk.length;
    }
    const valid = contentType === "image/jpeg"
      ? bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255
      : contentType === "image/png"
      ? [137, 80, 78, 71, 13, 10, 26, 10].every((b, i) => bytes[i] === b)
      : new TextDecoder().decode(bytes.slice(0, 4)) === "RIFF" &&
        new TextDecoder().decode(bytes.slice(8, 12)) === "WEBP";
    if (!valid) throw new BadRequest("El archivo no corresponde a una imagen válida");
    if (!services.images) {
      throw new ApiError(503, "storage_desactivado", "No está configurado el almacenamiento de imágenes");
    }
    return c.json({ url: await services.images.upload(bytes, contentType) }, 201);
  });

  app.get("/api/admin/feedback", async (c) => {
    requireAdmin(c);
    return c.json({ feedback: await services.feedback.all() });
  });

  app.delete("/api/admin/feedback/:id", async (c) => {
    requireAdmin(c);
    if (!await services.feedback.delete(c.req.param("id"))) {
      throw new ApiError(404, "no_encontrada", "Esa sugerencia ya no existe");
    }
    return c.body(null, 204);
  });

  // --- Buzón de sugerencias: escribir pide sesión, para que nadie lo llene de
  // basura sin dar la cara ---

  app.post("/api/feedback", async (c) => {
    const user = requireUser(c);
    const body = await readBody(c);
    const subject = requireString(body, "subject").trim().slice(0, 60);
    const message = requireString(body, "message").trim();
    if (subject === "") throw new BadRequest("Elige un asunto");
    if (message === "") throw new BadRequest("Escribe tu mensaje");
    if (message.length > 1000) throw new BadRequest("El mensaje admite hasta 1000 caracteres");

    const id = await services.feedback.add(
      user.id,
      subject,
      message,
      optionalString(body, "appVersion")?.trim().slice(0, 40) ?? "",
      optionalString(body, "device")?.trim().slice(0, 120) ?? "",
    );
    if (!id) throw new ApiError(429, "demasiadas_sugerencias", "Ya enviaste varias sugerencias hoy. Inténtalo mañana");
    return c.json({ id }, 201);
  });

  app.get("/health", (c) => c.json({ status: "ok" }));

  // --- Cuentas ---

  app.post("/api/auth/google/challenge", async (c) => {
    if (!services.google?.isEnabled) {
      throw new ApiError(503, "google_desactivado", "El acceso con Google estará disponible pronto");
    }
    c.header("Cache-Control", "no-store");
    return c.json({ nonce: await services.users.googleChallenge() });
  });

  app.post("/api/auth/google", async (c) => {
    if (!services.google?.isEnabled) {
      throw new ApiError(503, "google_desactivado", "El acceso con Google estará disponible pronto");
    }
    const body = await readBody(c);
    const idToken = requireString(body, "idToken");
    const nonce = requireString(body, "nonce");
    const identity = await services.google.verify(idToken, nonce);
    if (!identity || !await services.users.consumeGoogleChallenge(nonce)) {
      throw new ApiError(401, "google_invalido", "No se pudo verificar tu acceso con Google. Inténtalo de nuevo");
    }
    let user = await services.users.findByGoogleSubject(identity.subject);
    if (!user) {
      const existing = await services.users.findByEmail(identity.email);
      if (existing) {
        // Comprobar ambos accesos impide apropiarse de una cuenta por compartir correo.
        const password = optionalString(body, "existingPassword") ?? "";
        if (existing.googleSubject || !await passwordMatches(password, existing.passwordHash)) {
          throw new ApiError(
            409,
            "google_vinculacion_requerida",
            "Este correo ya tiene una cuenta. Escribe su contraseña y vuelve a continuar con Google para vincularla",
          );
        }
        user = await services.users.linkGoogle(existing.id, identity.subject);
      } else {
        user = await services.users.createGoogle(identity);
      }
    }
    if (!user) throw new ApiError(409, "google_conflicto", "No se pudo vincular esa cuenta. Inténtalo de nuevo");
    c.header("Cache-Control", "no-store");
    return c.json(await session(services, user));
  });

  app.post("/api/auth/register", async (c) => {
    const body = await readBody(c);
    const email = requireString(body, "email").trim();
    const password = requireString(body, "password");
    const displayName = requireString(body, "displayName");
    if (!email.includes("@") || email.length < 5) throw new BadRequest("Escribe un correo válido");
    const nameProblem = displayNameProblem(displayName);
    if (nameProblem) throw new BadRequest(nameProblem);
    if (!isPasswordValid(password)) {
      throw new BadRequest(`La contraseña necesita al menos ${PASSWORD_MIN_LENGTH} caracteres`);
    }

    const user = await services.users.create(email, await hashPassword(password), cleanDisplayName(displayName));
    if (!user) throw new ApiError(409, "correo_en_uso", "Ese correo ya tiene una cuenta");
    return c.json(await session(services, user), 201);
  });

  app.post("/api/auth/login", async (c) => {
    const body = await readBody(c);
    const user = await services.users.findByEmail(requireString(body, "email"));
    // El mismo mensaje en los dos casos: así no se puede averiguar qué correos
    // están registrados.
    if (!user || !(await passwordMatches(requireString(body, "password"), user.passwordHash))) {
      throw new ApiError(401, "credenciales_invalidas", "Correo o contraseña incorrectos");
    }
    return c.json(await session(services, user));
  });

  app.get("/api/me", (c) => c.json(userDto(requireUser(c))));

  // Cambiar el nombre visible. El token lleva el id, no el nombre, así que la
  // sesión abierta sigue valiendo después del cambio.
  app.post("/api/me", async (c) => {
    const user = requireUser(c);
    const displayName = requireString(await readBody(c), "displayName");
    const nameProblem = displayNameProblem(displayName);
    if (nameProblem) throw new BadRequest(nameProblem);

    const updated = await services.users.updateDisplayName(user.id, cleanDisplayName(displayName));
    if (!updated) throw new BadRequest("Sesión no válida");
    return c.json(userDto(updated));
  });

  // --- Alertas: leerlas es público, publicar y votar pide sesión ---

  app.get("/api/alerts", async (c) => {
    const latitude = queryNumber(c, "lat");
    const longitude = queryNumber(c, "lon");
    if (latitude === null) throw new BadRequest("Falta lat");
    if (longitude === null) throw new BadRequest("Falta lon");
    if (!isValidCoordinate(latitude, longitude)) throw new BadRequest("Coordenadas fuera de rango");
    const radius = Math.min(
      AlertPolicy.MAX_RADIUS_METERS,
      Math.max(1, queryNumber(c, "radius") ?? AlertPolicy.DEFAULT_RADIUS_METERS),
    );

    // Si viene sesión se marcan las alertas propias y el voto de cada una.
    const alerts = await services.alerts.near(latitude, longitude, radius, c.get("user")?.id ?? null);
    return c.json({ alerts });
  });

  app.post("/api/alerts", async (c) => {
    const user = requireUser(c);
    const body = await readBody(c);
    const type = requireString(body, "type");
    const latitude = requireNumber(body, "latitude");
    const longitude = requireNumber(body, "longitude");
    if (!AlertPolicy.isKnownType(type)) throw new BadRequest("Tipo de alerta desconocido");
    if (!isValidCoordinate(latitude, longitude)) throw new BadRequest("Coordenadas fuera de rango");

    const alert = await services.alerts.publish(
      user.id,
      type,
      latitude,
      longitude,
      optionalString(body, "placeName")?.trim().slice(0, 120) ?? null,
      optionalString(body, "comment"),
    );
    return c.json(alert, 201);
  });

  app.post("/api/alerts/:id/vote", async (c) => {
    const user = requireUser(c);
    const vote = requireString(await readBody(c), "vote");
    if (!AlertPolicy.VOTES.has(vote)) throw new BadRequest("Voto desconocido");

    const updated = await services.alerts.vote(c.req.param("id"), user.id, vote);
    if (!updated) throw new ApiError(404, "no_encontrada", "Esa alerta ya no existe");
    return c.json(updated);
  });

  // --- Rutas: leer el catálogo es público; grabar pide sesión y solo el autor
  // (o un administrador) puede borrar ---

  app.get("/api/routes", async (c) => {
    return c.json({ routes: await services.routes.all(c.get("user")?.id ?? null) });
  });

  app.get("/api/routes/:id", async (c) => {
    const route = await services.routes.byId(c.req.param("id"), c.get("user")?.id ?? null);
    if (!route) throw new ApiError(404, "no_encontrada", "Esa ruta no existe");
    return c.json(route);
  });

  app.post("/api/routes", async (c) => {
    const user = requireUser(c);
    const request = parseRoute(await readBody(c));
    return c.json(await services.routes.submit(user.id, request), 201);
  });

  app.delete("/api/routes/:id", async (c) => {
    const user = requireUser(c);
    const deleted = await services.routes.delete(c.req.param("id"), user.id, user.isAdmin);
    if (!deleted) throw new ApiError(403, "no_permitido", "Esa ruta no es tuya");
    return c.body(null, 204);
  });

  // --- Asistente y moderación: la clave de Gemini se queda aquí ---

  app.post("/api/assistant", async (c) => {
    requireUser(c);
    const body = await readBody(c);
    const question = requireString(body, "question");
    const language = optionalString(body, "language") ?? "es";
    if (question.trim().length === 0) throw new BadRequest("Escribe una pregunta");
    if (!services.gemini.isEnabled) {
      throw new ApiError(503, "asistente_desactivado", "El asistente no está configurado en el servidor");
    }

    const routes = await services.routes.all(null);
    const answer = await services.gemini.generateJson(
      assistantSystem(routes, language),
      question.slice(0, 500),
      ASSISTANT_SCHEMA,
    );
    if (!answer) throw new ApiError(503, "asistente_no_disponible", "El asistente no está disponible ahora mismo");

    const text = typeof answer.respuesta === "string" ? answer.respuesta : "";
    // El id que devuelve el modelo se comprueba contra el catálogo: así la app
    // nunca recibe una ruta inventada.
    const routeId = routes.find((route) => route.id === answer.rutaId)?.id;
    // Sin ruta se omite el campo: la app lo lee con optString, que convertiría
    // un null en el texto "null".
    return c.json(routeId ? { answer: text, routeId } : { answer: text });
  });

  app.post("/api/moderation", async (c) => {
    requireUser(c);
    const body = await readBody(c);
    const text = requireString(body, "text");
    const language = optionalString(body, "language") ?? "es";
    // Sin Gemini configurado no se bloquea nada: la app tiene su propio filtro
    // de groserías, que funciona sin conexión.
    if (text.trim().length === 0 || !services.gemini.isEnabled) return c.json({ allowed: true });

    const verdict = await services.gemini.generateJson(
      moderationSystem(language),
      `Texto a revisar:\n${text.slice(0, 500)}`,
      MODERATION_SCHEMA,
      { temperature: 0 },
    );
    const allowed = typeof verdict?.aceptable === "boolean" ? verdict.aceptable : true;
    const reason = typeof verdict?.motivo === "string" ? verdict.motivo.trim() : "";
    // Un rechazo sin motivo no se le puede explicar a nadie: se deja pasar.
    return c.json(!allowed && reason ? { allowed: false, reason } : { allowed: true });
  });

  return app;
}

// --- Sesión ---

async function userFromHeader(services: Services, header: string | undefined): Promise<StoredUser | null> {
  const token = header?.match(/^Bearer\s+(.+)$/i)?.[1];
  if (!token) return null;
  const id = await services.tokens.userIdOf(token);
  return id ? await services.users.findById(id) : null;
}

/** El usuario de la sesión actual; responde 401 si no hay sesión válida. */
function requireUser(c: Context<Env>): StoredUser {
  const user = c.get("user");
  if (!user) throw unauthorized();
  return user;
}

function requireAdmin(c: Context<Env>): StoredUser {
  const user = requireUser(c);
  if (!user.isAdmin) throw new ApiError(403, "solo_administrador", "Tu cuenta no tiene acceso de administrador");
  return user;
}

function requireSuperAdmin(c: Context<Env>): StoredUser {
  const user = requireAdmin(c);
  if (!user.isSuperAdmin) {
    throw new ApiError(403, "solo_administrador_principal", "Solo el administrador principal puede hacer esto");
  }
  return user;
}

/** Lo que se cuenta de una cuenta; nunca su contraseña ni su vínculo con Google. */
function userDto(user: StoredUser): UserDto {
  const role = user.isSuperAdmin ? "super_admin" : user.isAdmin ? "admin" : "client";
  return { id: user.id, email: user.email, displayName: user.displayName, role };
}

function routeStatus(body: Body): string {
  const status = optionalString(body, "status") ?? "APPROVED";
  if (!["APPROVED", "PENDING", "REJECTED"].includes(status)) throw new BadRequest("Estado desconocido");
  return status;
}

async function session(services: Services, user: StoredUser): Promise<SessionResponse> {
  return {
    token: await services.tokens.issue(user.id, user.displayName),
    user: userDto(user),
  };
}

// --- Lectura de lo que manda la app ---

type Body = Record<string, unknown>;

async function readBody(c: Context): Promise<Body> {
  let body: unknown;
  try {
    body = await c.req.json();
  } catch {
    throw new BadRequest("El cuerpo de la petición no es JSON válido");
  }
  if (!body || typeof body !== "object" || Array.isArray(body)) throw new BadRequest("Petición inválida");
  return body as Body;
}

function requireString(body: Body, key: string): string {
  const value = body[key];
  if (typeof value !== "string") throw new BadRequest(`Falta ${key}`);
  return value;
}

function optionalString(body: Body, key: string): string | null {
  const value = body[key];
  if (value === undefined || value === null) return null;
  if (typeof value !== "string") throw new BadRequest(`${key} tiene que ser texto`);
  return value;
}

function requireNumber(body: Body, key: string): number {
  const value = body[key];
  if (typeof value !== "number" || !Number.isFinite(value)) throw new BadRequest(`Falta ${key}`);
  return value;
}

function queryNumber(c: Context, key: string): number | null {
  const raw = c.req.query(key);
  if (raw === undefined || raw.trim() === "") return null;
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

function parsePoints(value: unknown, key: string): Body[] {
  if (!Array.isArray(value)) throw new BadRequest(`Falta ${key}`);
  return value.map((point) => {
    if (!point || typeof point !== "object") throw new BadRequest(`${key} no es válido`);
    return point as Body;
  });
}

export function parseRoute(body: Body): SubmitRouteRequest {
  if (Array.isArray(body.stops) && body.stops.length > 300) throw new BadRequest("Máximo 300 paradas");
  if (Array.isArray(body.path) && body.path.length > 20_000) throw new BadRequest("Recorrido demasiado largo");
  const stops: StopDto[] = parsePoints(body.stops, "stops").map((stop) => ({
    name: requireString(stop, "name"),
    latitude: requireNumber(stop, "latitude"),
    longitude: requireNumber(stop, "longitude"),
  }));
  const path: CoordinateDto[] | null = body.path === undefined || body.path === null
    ? null
    : parsePoints(body.path, "path").map((point) => ({
      latitude: requireNumber(point, "latitude"),
      longitude: requireNumber(point, "longitude"),
    }));
  const durationMinutes = requireNumber(body, "durationMinutes");
  if (!Number.isInteger(durationMinutes)) throw new BadRequest("La duración no parece válida");

  const request: SubmitRouteRequest = {
    id: optionalString(body, "id"),
    letter: requireString(body, "letter"),
    name: requireString(body, "name"),
    origin: requireString(body, "origin"),
    destination: requireString(body, "destination"),
    durationMinutes,
    fare: requireNumber(body, "fare"),
    schedule: requireString(body, "schedule"),
    stops,
    path,
    pathSnapped: body.pathSnapped === true,
    vehicleName: optionalString(body, "vehicleName")?.trim().slice(0, 120) ?? "",
    imageUrl: optionalString(body, "imageUrl")?.trim() || null,
    description: optionalString(body, "description")?.trim().slice(0, 1000) ?? "",
    bidirectional: body.bidirectional !== false,
  };

  if (request.imageUrl) {
    try {
      const url = new URL(request.imageUrl);
      if (url.protocol !== "https:" || url.username || url.password) throw new Error();
    } catch {
      throw new BadRequest("La imagen necesita una dirección HTTPS válida");
    }
  }
  for (const key of ["name", "origin", "destination", "schedule", "letter"] as const) {
    if (request[key].length > 160) throw new BadRequest(`${key} es demasiado largo`);
  }

  if (request.name.trim() === "") throw new BadRequest("La ruta necesita un nombre");
  if (request.origin.trim() === "" || request.destination.trim() === "") {
    throw new BadRequest("Falta el origen o el destino");
  }
  if (request.stops.length < 2) throw new BadRequest("Una ruta necesita al menos 2 paradas");
  if (request.fare <= 0 || request.fare > 50) throw new BadRequest("El pasaje no parece válido");
  if (request.durationMinutes <= 0 || request.durationMinutes > 1440) {
    throw new BadRequest("La duración no parece válida");
  }
  if (request.stops.some((stop) => !stop.name.trim() || stop.name.length > 120)) {
    throw new BadRequest("Cada parada necesita un nombre de hasta 120 caracteres");
  }
  if (request.stops.some((stop) => !isValidCoordinate(stop.latitude, stop.longitude))) {
    throw new BadRequest("Una parada está fuera del mapa");
  }
  if (request.path?.some((point) => !isValidCoordinate(point.latitude, point.longitude))) {
    throw new BadRequest("El recorrido sale del mapa");
  }
  return request;
}
