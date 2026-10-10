import { createApp } from "./app.ts";
import { Tokens } from "./auth.ts";
import { connect } from "./db.ts";
import { DEFAULT_MODEL, Gemini } from "./gemini.ts";
import { AlertStore, FeedbackStore, RouteStore, UserStore } from "./stores.ts";
import { SupabaseImages } from "./images.ts";
import { GoogleTokens } from "./google.ts";

/**
 * Arranque de la función `server` en Supabase.
 *
 * SUPABASE_DB_URL la pone Supabase sola. JWT_SECRET y GEMINI_API_KEY son
 * secretos del proyecto (`supabase secrets set`). El esquema y el catálogo
 * inicial no se crean aquí: llegan con las migraciones de supabase/migrations.
 */
function env(key: string, fallback?: string): string {
  const value = Deno.env.get(key) ?? fallback;
  if (value === undefined || value === "") {
    throw new Error(`Falta ${key}. Configúralo con: supabase secrets set ${key}=...`);
  }
  return value;
}

const sql = connect(env("SUPABASE_DB_URL"));

const gemini = new Gemini(env("GEMINI_API_KEY", " ").trim(), env("GEMINI_MODEL", DEFAULT_MODEL));
if (!gemini.isEnabled) {
  console.warn("Sin GEMINI_API_KEY: el asistente queda desactivado y la moderación deja pasar los textos");
}

const app = createApp({
  google: new GoogleTokens(
    (Deno.env.get("GOOGLE_WEB_CLIENT_IDS") ?? "").split(",").map((id) => id.trim()).filter(Boolean),
  ),
  users: new UserStore(sql),
  alerts: new AlertStore(sql),
  routes: new RouteStore(sql),
  feedback: new FeedbackStore(sql),
  tokens: new Tokens(env("JWT_SECRET")),
  gemini,
  images: new SupabaseImages(Deno.env.get("SUPABASE_URL") ?? "", Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""),
});

Deno.serve(app.fetch);
