import { assert, assertEquals, assertFalse, assertNotEquals } from "@std/assert";
import * as AlertPolicy from "./alert_policy.ts";
import {
  cleanDisplayName,
  DISPLAY_NAME_MAX_LENGTH,
  displayNameProblem,
  hashPassword,
  isPasswordValid,
  passwordMatches,
  TOKEN_LIFETIME_SECONDS,
  Tokens,
} from "./auth.ts";
import { boundingBox, distanceMeters, isValidCoordinate } from "./geo.ts";

// Las mismas pruebas que tenía el servidor Ktor (ktor/src/test).

const ahora = 1_757_800_000_000;
const minuto = 60_000;

// --- Alertas ---

Deno.test("un micro lleno caduca en una hora", () => {
  assertEquals(AlertPolicy.expiresAt("CROWDED", ahora, ahora), ahora + 60 * minuto);
});

Deno.test("unas obras duran un día", () => {
  assertEquals(AlertPolicy.expiresAt("ROADWORK", ahora, ahora), ahora + 24 * 60 * minuto);
});

Deno.test("confirmar que sigue ahí alarga la vida de la alerta", () => {
  const sinConfirmar = AlertPolicy.expiresAt("TRAFFIC", ahora, ahora);
  const confirmadaMasTarde = AlertPolicy.expiresAt("TRAFFIC", ahora, ahora + 110 * minuto);
  assert(confirmadaMasTarde > sinConfirmar);
});

Deno.test("un tipo desconocido no rompe el cálculo", () => {
  assertEquals(AlertPolicy.expiresAt("LO_QUE_SEA", ahora, ahora), ahora + 120 * minuto);
  assertFalse(AlertPolicy.isKnownType("LO_QUE_SEA"));
  assertFalse(AlertPolicy.isKnownType("toString"));
  assert(AlertPolicy.isKnownType("TRAFFIC"));
  assert(AlertPolicy.isKnownType("OVERCHARGE"));
});

Deno.test("dos votos de que ya no está la ocultan", () => {
  assertFalse(AlertPolicy.isHidden(0, 1));
  assert(AlertPolicy.isHidden(0, 2));
  // Si otros confirman que sigue ahí, no se oculta.
  assertFalse(AlertPolicy.isHidden(1, 2));
});

Deno.test("el comentario se limpia y se recorta", () => {
  assertEquals(AlertPolicy.cleanComment("   "), null);
  assertEquals(AlertPolicy.cleanComment(null), null);
  assertEquals(AlertPolicy.cleanComment("  Cola larga  "), "Cola larga");
  assertEquals(AlertPolicy.cleanComment("a".repeat(500))!.length, AlertPolicy.MAX_COMMENT_LENGTH);
});

// --- Cuentas ---

Deno.test("la contraseña no se guarda tal cual y se puede comprobar", async () => {
  const hash = await hashPassword("micro-2026");
  assertFalse(hash.includes("micro-2026"));
  assert(await passwordMatches("micro-2026", hash));
  assertFalse(await passwordMatches("otra-cosa", hash));
  assertFalse(await passwordMatches("micro-2026", "no-es-un-hash"));
});

Deno.test("una contraseña corta no vale", () => {
  assertFalse(isPasswordValid("1234"));
  assert(isPasswordValid("micro-2026"));
});

Deno.test("el nombre se guarda sin espacios de sobra", () => {
  assertEquals(cleanDisplayName("  Ana   Maria "), "Ana Maria");
  assertEquals(displayNameProblem("Ana Maria"), null);
});

Deno.test("un nombre vacío o larguísimo no vale", () => {
  assertNotEquals(displayNameProblem("   "), null);
  assertNotEquals(displayNameProblem("a".repeat(DISPLAY_NAME_MAX_LENGTH + 1)), null);
});

Deno.test("un token propio identifica al usuario", async () => {
  const tokens = new Tokens("secreto-de-prueba-muy-largo");
  assertEquals(await tokens.userIdOf(await tokens.issue("usuario-1", "Ana")), "usuario-1");
});

Deno.test("un token firmado con otro secreto no vale", async () => {
  const token = await new Tokens("secreto-de-prueba-muy-largo").issue("usuario-1", "Ana");
  assertEquals(await new Tokens("otro-secreto-distinto").userIdOf(token), null);
});

Deno.test("un token caducado no vale", async () => {
  const tokens = new Tokens("secreto-de-prueba-muy-largo");
  const viejo = await tokens.issue("usuario-1", "Ana", Date.now() - TOKEN_LIFETIME_SECONDS * 1000 - 1_000);
  assertEquals(await tokens.userIdOf(viejo), null);
});

Deno.test("un texto cualquiera no es un token", async () => {
  assertEquals(await new Tokens("secreto-de-prueba-muy-largo").userIdOf("esto-no-es-un-token"), null);
});

// --- Mapa ---

const plazaDeArmas = [-8.1120, -79.0288] as const;
const avEspana = [-8.1132, -79.0330] as const;

Deno.test("mide la distancia entre dos puntos de Trujillo", () => {
  const metros = distanceMeters(...plazaDeArmas, ...avEspana);
  // Son unas cuatro cuadras: alrededor de 480 m.
  assert(metros >= 400 && metros <= 550, `midió ${metros} m`);
});

Deno.test("la distancia de un punto a sí mismo es cero", () => {
  assert(Math.abs(distanceMeters(-8.11, -79.03, -8.11, -79.03)) < 0.001);
});

Deno.test("la caja contiene los puntos dentro del radio", () => {
  const caja = boundingBox(-8.1120, -79.0288, 1_000);
  const dentro = (lat: number) => lat >= caja.minLatitude && lat <= caja.maxLatitude;
  // Un punto a unos 500 m al norte tiene que caer dentro; uno a 5 km, fuera.
  assert(dentro(-8.1120 + 500 / 110_540));
  assertFalse(dentro(-8.1120 + 5_000 / 110_540));
});

Deno.test("rechaza coordenadas imposibles", () => {
  assert(isValidCoordinate(-8.11, -79.03));
  assertFalse(isValidCoordinate(120, -79.03));
  assertFalse(isValidCoordinate(-8.11, 200));
  assertFalse(isValidCoordinate(NaN, -79.03));
});
