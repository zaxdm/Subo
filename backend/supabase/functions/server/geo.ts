/**
 * Distancias sobre el mapa.
 *
 * Es la misma fórmula que usa la app (haversine), repetida aquí a propósito:
 * el servidor no depende del código Android, y las dos partes tienen que medir
 * igual para que "a menos de 500 m" signifique lo mismo en ambos lados.
 */

const EARTH_RADIUS_METERS = 6_371_008.8;
const METERS_PER_DEGREE_LAT = 110_540.0;
const METERS_PER_DEGREE_LON_AT_EQUATOR = 111_320.0;

const toRadians = (degrees: number) => (degrees * Math.PI) / 180;

export interface BoundingBox {
  minLatitude: number;
  maxLatitude: number;
  minLongitude: number;
  maxLongitude: number;
}

export function distanceMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const dLat = toRadians(lat2 - lat1);
  const dLon = toRadians(lon2 - lon1);
  const sinLat = Math.sin(dLat / 2);
  const sinLon = Math.sin(dLon / 2);
  const a = sinLat * sinLat + Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) * sinLon * sinLon;
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1, Math.max(0, a))));
}

/**
 * Caja que contiene todo lo que está a `radiusMeters` de un punto.
 *
 * La base de datos filtra primero por esta caja, que usa los índices y es
 * instantánea; la distancia exacta se calcula después sobre esas pocas filas.
 */
export function boundingBox(latitude: number, longitude: number, radiusMeters: number): BoundingBox {
  const latSpan = radiusMeters / METERS_PER_DEGREE_LAT;
  // Cerca de los polos un grado de longitud son pocos metros: se acota para
  // no dividir por casi cero.
  const metersPerDegreeLon = Math.max(1, METERS_PER_DEGREE_LON_AT_EQUATOR * Math.cos(toRadians(latitude)));
  const lonSpan = radiusMeters / metersPerDegreeLon;
  return {
    minLatitude: latitude - latSpan,
    maxLatitude: latitude + latSpan,
    minLongitude: longitude - lonSpan,
    maxLongitude: longitude + lonSpan,
  };
}

export function isValidCoordinate(latitude: number, longitude: number): boolean {
  return Number.isFinite(latitude) && Number.isFinite(longitude) &&
    Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
}
