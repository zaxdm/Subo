/**
 * Cuánto vive una alerta y cuándo deja de mostrarse.
 *
 * Son las mismas reglas que la app aplicaba sola cuando los datos vivían en el
 * teléfono. Ahora mandan aquí: si cada usuario decidiera por su cuenta, dos
 * personas verían cosas distintas del mismo sitio.
 */

/** Tipos válidos y cuánto tiempo tiene sentido cada aviso, en minutos. */
export const LIFETIME_MINUTES: Record<string, number> = {
  TRAFFIC: 120,
  ACCIDENT: 180,
  OVERCHARGE: 120,
  ROADWORK: 24 * 60,
  CROWDED: 60,
  DANGER: 360,
  OTHER: 120,
};

export const VOTES = new Set(["STILL_THERE", "GONE"]);

/** Con esta diferencia de votos "ya no está" sobre "sigue ahí", se oculta. */
export const DISMISSALS_TO_HIDE = 2;

export const MAX_COMMENT_LENGTH = 140;

/** Radio por defecto y máximo al pedir las alertas de una zona. */
export const DEFAULT_RADIUS_METERS = 5_000;
export const MAX_RADIUS_METERS = 50_000;

const MINUTE_MS = 60_000;

export function isKnownType(type: string): boolean {
  return Object.hasOwn(LIFETIME_MINUTES, type);
}

/**
 * Momento en que caduca: la vida del tipo desde que se creó, alargada si
 * alguien confirmó que seguía ahí.
 */
export function expiresAt(type: string, createdAt: number, lastConfirmedAt: number): number {
  const lifetime = (LIFETIME_MINUTES[type] ?? LIFETIME_MINUTES.OTHER) * MINUTE_MS;
  return Math.max(createdAt + lifetime, lastConfirmedAt + Math.floor(lifetime / 2));
}

/** Una alerta con demasiados "ya no está" desaparece para todos. */
export function isHidden(confirmations: number, dismissals: number): boolean {
  return dismissals - confirmations >= DISMISSALS_TO_HIDE;
}

export function cleanComment(comment: string | null | undefined): string | null {
  const trimmed = comment?.trim();
  return trimmed ? trimmed.slice(0, MAX_COMMENT_LENGTH) : null;
}
