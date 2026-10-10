import * as AlertPolicy from "./alert_policy.ts";
import type { Sql } from "./db.ts";
import { boundingBox, distanceMeters } from "./geo.ts";
import type { AlertDto, FeedbackDto, RouteDto, SubmitRouteRequest } from "./models.ts";
import { type GoogleIdentity, nonceHash } from "./google.ts";

// --- Usuarios ---

export interface StoredUser {
  id: string;
  email: string;
  passwordHash: string | null;
  googleSubject?: string | null;
  displayName: string;
  createdAt: number;
  isAdmin: boolean;
  /** El administrador principal: el único que da o quita el acceso de administrador. */
  isSuperAdmin?: boolean;
}

const normalizeEmail = (email: string) => email.trim().toLowerCase();

export class UserStore {
  constructor(private readonly sql: Sql) {}

  async findByGoogleSubject(subject: string): Promise<StoredUser | null> {
    const rows = await this.sql`SELECT * FROM users WHERE google_sub = ${subject}`;
    return rows.length ? toUser(rows[0]) : null;
  }

  async createGoogle(identity: GoogleIdentity): Promise<StoredUser | null> {
    const id = crypto.randomUUID();
    const rows = await this.sql`
      INSERT INTO users (id, email, password_hash, display_name, created_at, google_sub)
      VALUES (${id}, ${identity.email}, NULL, ${identity.displayName}, ${Date.now()}, ${identity.subject})
      ON CONFLICT DO NOTHING RETURNING *`;
    return rows.length ? toUser(rows[0]) : this.findByGoogleSubject(identity.subject);
  }

  async linkGoogle(userId: string, subject: string): Promise<StoredUser | null> {
    const rows = await this.sql`
      UPDATE users SET google_sub = ${subject}
      WHERE id = ${userId} AND (google_sub IS NULL OR google_sub = ${subject}) RETURNING *`;
    return rows.length ? toUser(rows[0]) : null;
  }

  async googleChallenge(now = Date.now()): Promise<string> {
    const bytes = crypto.getRandomValues(new Uint8Array(32));
    const nonce = [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
    await this.sql`DELETE FROM google_login_challenges WHERE expires_at <= ${now}`;
    await this.sql`INSERT INTO google_login_challenges (nonce_hash, expires_at)
      VALUES (${await nonceHash(nonce)}, ${now + 300_000})`;
    return nonce;
  }

  async consumeGoogleChallenge(nonce: string, now = Date.now()): Promise<boolean> {
    const result = await this.sql`DELETE FROM google_login_challenges
      WHERE nonce_hash = ${await nonceHash(nonce)} AND expires_at > ${now}`;
    return result.count > 0;
  }

  async findByEmail(email: string): Promise<StoredUser | null> {
    const rows = await this.sql`
      SELECT id, email, password_hash, display_name, created_at, is_admin, is_super_admin, google_sub
      FROM users WHERE email = ${normalizeEmail(email)}`;
    return rows.length ? toUser(rows[0]) : null;
  }

  async findById(id: string): Promise<StoredUser | null> {
    const rows = await this.sql`
      SELECT id, email, password_hash, display_name, created_at, is_admin, is_super_admin, google_sub
      FROM users WHERE id = ${id}`;
    return rows.length ? toUser(rows[0]) : null;
  }

  /** Crea la cuenta; `null` si ese correo ya estaba registrado. */
  async create(email: string, passwordHash: string, displayName: string, now = Date.now()): Promise<StoredUser | null> {
    const user: StoredUser = {
      id: crypto.randomUUID(),
      email: normalizeEmail(email),
      passwordHash,
      displayName: displayName.trim(),
      createdAt: now,
      isAdmin: false,
    };
    const result = await this.sql`
      INSERT INTO users (id, email, password_hash, display_name, created_at)
      VALUES (${user.id}, ${user.email}, ${user.passwordHash}, ${user.displayName}, ${user.createdAt})
      ON CONFLICT (email) DO NOTHING`;
    return result.count === 0 ? null : user;
  }

  // --- Administradores: solo los gestiona el administrador principal ---

  /** Las cuentas con acceso de administrador, con el principal primero. */
  async admins(): Promise<StoredUser[]> {
    const rows = await this.sql`
      SELECT * FROM users WHERE is_admin ORDER BY is_super_admin DESC, created_at`;
    return rows.map(toUser);
  }

  /**
   * Da o quita el acceso de administrador. Al principal no se le toca: así
   * nunca se queda el panel sin nadie que pueda gestionarlo.
   */
  async setAdmin(id: string, admin: boolean): Promise<StoredUser | null> {
    const rows = await this.sql`
      UPDATE users SET is_admin = ${admin}
      WHERE id = ${id} AND NOT is_super_admin RETURNING *`;
    return rows.length ? toUser(rows[0]) : null;
  }

  /** Crea una cuenta que ya nace administradora; `null` si el correo estaba en uso. */
  async createAdmin(
    email: string,
    passwordHash: string,
    displayName: string,
    now = Date.now(),
  ): Promise<StoredUser | null> {
    const rows = await this.sql`
      INSERT INTO users (id, email, password_hash, display_name, created_at, is_admin)
      VALUES (${crypto.randomUUID()}, ${normalizeEmail(email)}, ${passwordHash}, ${displayName.trim()}, ${now}, TRUE)
      ON CONFLICT (email) DO NOTHING RETURNING *`;
    return rows.length ? toUser(rows[0]) : null;
  }

  /** Cambia el nombre visible; `null` si la cuenta ya no existe. */
  async updateDisplayName(id: string, displayName: string): Promise<StoredUser | null> {
    const result = await this.sql`UPDATE users SET display_name = ${displayName} WHERE id = ${id}`;
    return result.count === 0 ? null : this.findById(id);
  }
}

// deno-lint-ignore no-explicit-any
function toUser(row: any): StoredUser {
  return {
    id: row.id,
    email: row.email,
    passwordHash: row.password_hash,
    googleSubject: row.google_sub ?? null,
    displayName: row.display_name,
    createdAt: row.created_at,
    isAdmin: row.is_admin,
    isSuperAdmin: row.is_super_admin === true,
  };
}

// --- Alertas ---
//
// Los votos viven en su propia tabla, uno por usuario y alerta: así nadie puede
// votar dos veces y el recuento es siempre el de verdad.

export class AlertStore {
  constructor(private readonly sql: Sql) {}

  /** Alertas vigentes cerca de un punto, de la más reciente a la más antigua. */
  async near(
    latitude: number,
    longitude: number,
    radiusMeters: number,
    viewerId: string | null,
    now = Date.now(),
  ): Promise<AlertDto[]> {
    const box = boundingBox(latitude, longitude, radiusMeters);
    const rows = await this.sql`
      SELECT a.id, a.type, a.latitude, a.longitude, a.place_name, a.comment,
             a.created_at, a.last_confirmed_at, a.expires_at, a.author_id,
             COALESCE(SUM(CASE WHEN v.vote = 'STILL_THERE' THEN 1 ELSE 0 END), 0)::INT AS confirmations,
             COALESCE(SUM(CASE WHEN v.vote = 'GONE' THEN 1 ELSE 0 END), 0)::INT AS dismissals,
             MAX(CASE WHEN v.user_id = ${viewerId} THEN v.vote END) AS my_vote
      FROM alerts a
      LEFT JOIN alert_votes v ON v.alert_id = a.id
      WHERE a.expires_at > ${now}
        AND a.latitude BETWEEN ${box.minLatitude} AND ${box.maxLatitude}
        AND a.longitude BETWEEN ${box.minLongitude} AND ${box.maxLongitude}
      GROUP BY a.id
      ORDER BY a.created_at DESC`;

    // La caja es cuadrada: el círculo de verdad se recorta aquí. También se
    // quitan las que la gente dio por terminadas.
    return rows
      .map((row) => toAlert(row, viewerId))
      .filter((alert) =>
        distanceMeters(latitude, longitude, alert.latitude, alert.longitude) <= radiusMeters &&
        !AlertPolicy.isHidden(alert.confirmations, alert.dismissals) &&
        alert.myVote !== "GONE"
      );
  }

  async byId(id: string, viewerId: string | null): Promise<AlertDto | null> {
    const rows = await this.sql`
      SELECT a.id, a.type, a.latitude, a.longitude, a.place_name, a.comment,
             a.created_at, a.last_confirmed_at, a.expires_at, a.author_id,
             COALESCE(SUM(CASE WHEN v.vote = 'STILL_THERE' THEN 1 ELSE 0 END), 0)::INT AS confirmations,
             COALESCE(SUM(CASE WHEN v.vote = 'GONE' THEN 1 ELSE 0 END), 0)::INT AS dismissals,
             MAX(CASE WHEN v.user_id = ${viewerId} THEN v.vote END) AS my_vote
      FROM alerts a
      LEFT JOIN alert_votes v ON v.alert_id = a.id
      WHERE a.id = ${id}
      GROUP BY a.id`;
    return rows.length ? toAlert(rows[0], viewerId) : null;
  }

  async publish(
    authorId: string,
    type: string,
    latitude: number,
    longitude: number,
    placeName: string | null,
    comment: string | null,
    now = Date.now(),
  ): Promise<AlertDto> {
    const id = crypto.randomUUID();
    const expiresAt = AlertPolicy.expiresAt(type, now, now);
    const cleanComment = AlertPolicy.cleanComment(comment);
    await this.sql`
      INSERT INTO alerts (id, type, latitude, longitude, place_name, comment,
                          created_at, last_confirmed_at, expires_at, author_id)
      VALUES (${id}, ${type}, ${latitude}, ${longitude}, ${placeName}, ${cleanComment},
              ${now}, ${now}, ${expiresAt}, ${authorId})`;
    return {
      id,
      type,
      latitude,
      longitude,
      placeName,
      comment: cleanComment,
      createdAt: now,
      expiresAt,
      confirmations: 0,
      dismissals: 0,
      lastConfirmedAt: now,
      myVote: null,
      createdByMe: true,
    };
  }

  /**
   * Guarda el voto de un usuario (cambiándolo si ya había votado) y alarga la
   * vida de la alerta cuando alguien confirma que sigue ahí.
   * Devuelve `null` si la alerta no existe.
   */
  async vote(alertId: string, userId: string, vote: string, now = Date.now()): Promise<AlertDto | null> {
    const found = await this.sql.begin(async (tx) => {
      const alerts = await tx`SELECT type, created_at FROM alerts WHERE id = ${alertId} FOR UPDATE`;
      if (alerts.length === 0) return false;

      await tx`
        INSERT INTO alert_votes (alert_id, user_id, vote, created_at)
        VALUES (${alertId}, ${userId}, ${vote}, ${now})
        ON CONFLICT (alert_id, user_id) DO UPDATE SET vote = EXCLUDED.vote, created_at = EXCLUDED.created_at`;

      if (vote === "STILL_THERE") {
        const { type, created_at } = alerts[0];
        await tx`
          UPDATE alerts
          SET last_confirmed_at = ${now},
              expires_at = GREATEST(expires_at, ${AlertPolicy.expiresAt(type, created_at, now)})
          WHERE id = ${alertId}`;
      }
      return true;
    });
    return found ? this.byId(alertId, userId) : null;
  }
}

// deno-lint-ignore no-explicit-any
function toAlert(row: any, viewerId: string | null): AlertDto {
  return {
    id: row.id,
    type: row.type,
    latitude: row.latitude,
    longitude: row.longitude,
    placeName: row.place_name,
    comment: row.comment,
    createdAt: row.created_at,
    expiresAt: row.expires_at,
    confirmations: row.confirmations,
    dismissals: row.dismissals,
    lastConfirmedAt: row.last_confirmed_at,
    myVote: row.my_vote,
    createdByMe: viewerId !== null && row.author_id === viewerId,
  };
}

// --- Sugerencias ---

/** Cuántas sugerencias puede mandar una cuenta en un día. */
export const FEEDBACK_DAILY_LIMIT = 10;

export class FeedbackStore {
  constructor(private readonly sql: Sql) {}

  /** Guarda la sugerencia; `null` si esa cuenta ya mandó demasiadas hoy. */
  async add(
    userId: string,
    subject: string,
    message: string,
    appVersion: string,
    device: string,
    now = Date.now(),
  ): Promise<string | null> {
    const [{ count }] = await this.sql`
      SELECT COUNT(*)::INT AS count FROM feedback
      WHERE user_id = ${userId} AND created_at > ${now - 86_400_000}`;
    if (count >= FEEDBACK_DAILY_LIMIT) return null;

    const id = crypto.randomUUID();
    await this.sql`
      INSERT INTO feedback (id, user_id, subject, message, app_version, device, created_at)
      VALUES (${id}, ${userId}, ${subject}, ${message}, ${appVersion}, ${device}, ${now})`;
    return id;
  }

  /** Las más recientes primero. */
  async all(): Promise<FeedbackDto[]> {
    const rows = await this.sql`
      SELECT f.id, f.subject, f.message, f.app_version, f.device, f.created_at,
             u.display_name, u.email
      FROM feedback f
      LEFT JOIN users u ON u.id = f.user_id
      ORDER BY f.created_at DESC
      LIMIT 500`;
    return rows.map((row) => ({
      id: row.id,
      subject: row.subject,
      message: row.message,
      appVersion: row.app_version,
      device: row.device,
      createdAt: row.created_at,
      userName: row.display_name ?? null,
      userEmail: row.email ?? null,
    }));
  }

  async delete(id: string): Promise<boolean> {
    const result = await this.sql`DELETE FROM feedback WHERE id = ${id}`;
    return result.count > 0;
  }
}

// --- Rutas ---
//
// Las paradas y el trazado se guardan como jsonb: siempre se leen enteros y
// nunca se consultan por partes, así que una tabla aparte solo añadiría trabajo.

export class RouteStore {
  constructor(private readonly sql: Sql) {}

  async adminAll(): Promise<RouteDto[]> {
    const rows = await this.sql`SELECT * FROM routes ORDER BY created_at DESC`;
    return rows.map((row) => toRoute(row, null));
  }

  async adminSave(
    id: string | null,
    authorId: string,
    request: SubmitRouteRequest,
    status: string,
  ): Promise<RouteDto | null> {
    const routeId = id ?? `admin-${crypto.randomUUID()}`;
    return await this.sql.begin(async (tx) => {
      if (id === null) {
        await tx`
        INSERT INTO routes (id, letter, name, origin, destination, duration_minutes, fare, schedule,
          accent_index, stops, path, author_id, created_at, created_by_user, status)
        VALUES (${routeId}, ${request.letter}, ${request.name}, ${request.origin}, ${request.destination},
          ${request.durationMinutes}, ${request.fare}, ${request.schedule},
          (SELECT COUNT(*)::INT FROM routes), ${this.sql.json(request.stops)},
          ${request.path ? this.sql.json(request.path) : null}, ${authorId}, ${Date.now()}, FALSE, ${status})`;
      }
      const result = await tx`
      UPDATE routes SET letter = ${request.letter}, name = ${request.name}, origin = ${request.origin},
        destination = ${request.destination}, duration_minutes = ${request.durationMinutes}, fare = ${request.fare},
        schedule = ${request.schedule}, stops = ${this.sql.json(request.stops)},
        path = ${request.path ? this.sql.json(request.path) : null}, path_snapped = ${request.pathSnapped},
        vehicle_name = ${request.vehicleName ?? ""}, image_url = ${request.imageUrl ?? null},
        description = ${request.description ?? ""}, bidirectional = ${request.bidirectional ?? true}, status = ${status}
      WHERE id = ${routeId}`;
      if (!result.count) return null;
      const rows = await tx`SELECT * FROM routes WHERE id = ${routeId}`;
      return toRoute(rows[0], authorId);
    });
  }

  async all(viewerId: string | null): Promise<RouteDto[]> {
    const rows = await this.sql`
      SELECT * FROM routes
      WHERE status = 'APPROVED' OR author_id = ${viewerId}
      ORDER BY created_by_user, created_at`;
    return rows.map((row) => toRoute(row, viewerId));
  }

  async byId(id: string, viewerId: string | null): Promise<RouteDto | null> {
    const rows = await this
      .sql`SELECT * FROM routes WHERE id = ${id} AND (status = 'APPROVED' OR author_id = ${viewerId})`;
    return rows.length ? toRoute(rows[0], viewerId) : null;
  }

  async submit(authorId: string, request: SubmitRouteRequest, now = Date.now()): Promise<RouteDto> {
    // Si la app propone un id y nadie lo usa, se respeta: así la ruta es la
    // misma en el teléfono y en el servidor.
    const proposed = request.id?.trim();
    const existing = proposed ? await this.sql`SELECT * FROM routes WHERE id = ${proposed}` : [];
    // Un reintento de la misma publicación devuelve la ruta existente, sin duplicarla.
    if (existing.length && existing[0].author_id === authorId) return toRoute(existing[0], authorId);
    const id = proposed && !existing.length ? proposed : `usuario-${crypto.randomUUID()}`;
    const [{ count }] = await this.sql`SELECT COUNT(*)::INT AS count FROM routes`;
    await this.sql`
      INSERT INTO routes (id, letter, name, origin, destination, duration_minutes, fare, schedule,
                          accent_index, stops, path, path_snapped, created_by_user,
                          status, author_id, created_at, vehicle_name, image_url, description, bidirectional)
      VALUES (${id}, ${request.letter}, ${request.name}, ${request.origin}, ${request.destination},
              ${request.durationMinutes}, ${request.fare}, ${request.schedule}, ${count},
              ${this.sql.json(request.stops)}, ${request.path ? this.sql.json(request.path) : null},
              ${request.pathSnapped}, TRUE, 'APPROVED', ${authorId}, ${now}, ${request.vehicleName ?? ""},
              ${request.imageUrl ?? null}, ${request.description ?? ""}, ${request.bidirectional ?? true})`;
    return (await this.byId(id, authorId))!;
  }

  /** La borra su autor o un administrador; devuelve `false` si no le corresponde. */
  async delete(id: string, userId: string, isAdmin: boolean): Promise<boolean> {
    const result = isAdmin
      ? await this.sql`DELETE FROM routes WHERE id = ${id}`
      : await this.sql`DELETE FROM routes WHERE id = ${id} AND author_id = ${userId}`;
    return result.count > 0;
  }
}

// deno-lint-ignore no-explicit-any
function toRoute(row: any, viewerId: string | null): RouteDto {
  return {
    vehicleName: row.vehicle_name ?? "",
    imageUrl: row.image_url ?? null,
    description: row.description ?? "",
    bidirectional: row.bidirectional ?? true,
    id: row.id,
    letter: row.letter,
    name: row.name,
    origin: row.origin,
    destination: row.destination,
    durationMinutes: row.duration_minutes,
    fare: row.fare,
    schedule: row.schedule,
    stops: row.stops,
    accentIndex: row.accent_index,
    path: row.path,
    pathSnapped: row.path_snapped,
    createdByUser: row.created_by_user,
    status: row.status,
    createdByMe: viewerId !== null && row.author_id === viewerId,
  };
}
