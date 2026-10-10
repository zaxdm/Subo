import bcrypt from "bcryptjs";
import { jwtVerify, SignJWT } from "jose";

// --- Contraseñas ---
//
// Nunca se guarda la contraseña: se guarda su huella con bcrypt, que es lenta a
// propósito. Si alguien se llevara la base de datos, no tendría las contraseñas.
// Las huellas son compatibles con las que generaba el servidor Ktor.

/** Coste de bcrypt: más alto es más seguro y más lento de comprobar. */
const BCRYPT_COST = 12;

export const PASSWORD_MIN_LENGTH = 8;

export function hashPassword(plain: string): Promise<string> {
  return bcrypt.hash(plain, BCRYPT_COST);
}

export async function passwordMatches(plain: string, hash: string | null): Promise<boolean> {
  if (!hash) return false;
  try {
    return await bcrypt.compare(plain, hash);
  } catch {
    return false;
  }
}

export function isPasswordValid(plain: string): boolean {
  return plain.length >= PASSWORD_MIN_LENGTH && plain.trim().length > 0;
}

// --- Nombre visible ---
//
// Es lo único del usuario que llega a los demás (quién publicó una alerta,
// quién grabó una ruta), así que se limpia antes de guardarlo.

export const DISPLAY_NAME_MAX_LENGTH = 40;

export function cleanDisplayName(raw: string): string {
  return raw.trim().replace(/\s+/g, " ");
}

/** Motivo por el que un nombre no sirve, listo para mostrar; `null` si está bien. */
export function displayNameProblem(raw: string): string | null {
  const name = cleanDisplayName(raw);
  if (name.length === 0) return "Escribe tu nombre";
  if (name.length > DISPLAY_NAME_MAX_LENGTH) {
    return `El nombre no puede pasar de ${DISPLAY_NAME_MAX_LENGTH} caracteres`;
  }
  return null;
}

// --- Tokens de sesión (JWT) ---
//
// Al iniciar sesión el servidor entrega un token firmado; la app lo manda en
// cada petición. El servidor solo comprueba la firma, sin guardar sesiones.
// Mismo emisor y formato que el servidor Ktor: con el mismo JWT_SECRET, las
// sesiones abiertas siguen valiendo.

export const TOKEN_ISSUER = "ruta-norte";

/** Un mes: el usuario no tiene que volver a entrar en cada viaje. */
export const TOKEN_LIFETIME_SECONDS = 30 * 24 * 60 * 60;

export class Tokens {
  private readonly key: Uint8Array;

  constructor(secret: string) {
    this.key = new TextEncoder().encode(secret);
  }

  issue(userId: string, displayName: string, nowMs: number = Date.now()): Promise<string> {
    const now = Math.floor(nowMs / 1000);
    return new SignJWT({ name: displayName })
      .setProtectedHeader({ alg: "HS256", typ: "JWT" })
      .setIssuer(TOKEN_ISSUER)
      .setSubject(userId)
      .setIssuedAt(now)
      .setExpirationTime(now + TOKEN_LIFETIME_SECONDS)
      .sign(this.key);
  }

  /** Id del usuario si el token es válido; `null` si está manipulado o caducado. */
  async userIdOf(token: string): Promise<string | null> {
    try {
      const { payload } = await jwtVerify(token, this.key, { issuer: TOKEN_ISSUER, algorithms: ["HS256"] });
      return payload.sub ?? null;
    } catch {
      return null;
    }
  }
}
