import type { RouteDto } from "./models.ts";

/**
 * Llamadas a Gemini, desde el servidor.
 *
 * Antes la app llevaba la clave dentro del APK, de donde cualquiera podía
 * sacarla. Aquí la clave no sale del servidor (es un secreto de Supabase).
 */

const BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";
export const DEFAULT_MODEL = "gemini-3.5-flash-lite";

type Json = Record<string, unknown>;

export class Gemini {
  constructor(private readonly apiKey: string, private readonly model: string = DEFAULT_MODEL) {}

  get isEnabled(): boolean {
    return this.apiKey.trim().length > 0;
  }

  /**
   * Pide una respuesta en JSON siguiendo `responseSchema`.
   * Devuelve `null` si el servicio falla: quien llama decide qué hacer.
   */
  async generateJson(
    systemInstruction: string,
    userText: string,
    responseSchema: Json,
    { maxOutputTokens = 1024, temperature = 0.2 } = {},
  ): Promise<Json | null> {
    if (!this.isEnabled) return null;

    const body = {
      systemInstruction: { parts: [{ text: systemInstruction }] },
      contents: [{ role: "user", parts: [{ text: userText }] }],
      generationConfig: {
        temperature,
        maxOutputTokens,
        responseMimeType: "application/json",
        responseSchema,
      },
    };

    try {
      const response = await fetch(`${BASE_URL}/${this.model}:generateContent`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": this.apiKey },
        body: JSON.stringify(body),
      });
      if (!response.ok) {
        console.warn(`Gemini respondió ${response.status}`);
        return null;
      }
      const data = await response.json();
      const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
      if (typeof text !== "string") return null;
      const parsed = JSON.parse(text);
      return parsed && typeof parsed === "object" ? parsed : null;
    } catch (error) {
      console.warn("No se pudo hablar con Gemini", error);
      return null;
    }
  }
}

/** Esquema del asistente: una respuesta corta y, si la hay, la ruta que sirve. */
export const ASSISTANT_SCHEMA: Json = {
  type: "object",
  properties: {
    respuesta: { type: "string" },
    rutaId: { type: "string", description: "Id exacto de la ruta recomendada, o vacío si ninguna sirve" },
  },
  required: ["respuesta"],
};

/** Esquema de la moderación. */
export const MODERATION_SCHEMA: Json = {
  type: "object",
  properties: {
    aceptable: { type: "boolean" },
    motivo: { type: "string", description: "Por qué se rechaza; vacío si es aceptable" },
  },
  required: ["aceptable"],
};

// --- Instrucciones que se le dan a Gemini, en un sitio para poder ajustarlas ---

export function assistantSystem(routes: RouteDto[], language: string): string {
  const catalog = routes.map((route) =>
    `- id=${route.id} | ${route.name} (${route.letter}) | ${route.origin} → ${route.destination} | ` +
    `S/ ${route.fare} | ${route.durationMinutes} min | ${route.schedule} | ` +
    `paradas: ${route.stops.map((stop) => stop.name).join(", ")}`
  ).join("\n");
  const languageNote = language === "en" ? "Answer in English." : "Responde en español.";
  return `Eres el asistente de una app de micros de Trujillo, Perú. Ayudas a la gente
a saber qué micro tomar para llegar a donde va.

Estas son las únicas rutas que existen:
${catalog}

Reglas:
- Recomienda solo rutas de esa lista y devuelve su id exacto en "rutaId".
- Si ninguna sirve, dilo con claridad y deja "rutaId" vacío. No inventes rutas.
- Responde corto, en dos o tres frases, diciendo qué micro tomar, dónde subir,
  cuánto cuesta y cuánto demora.
- Los horarios son aproximados: recuérdalo cuando venga a cuento.
${languageNote}`;
}

export function moderationSystem(language: string): string {
  const languageNote = language === "en" ? "Write the reason in English." : "Escribe el motivo en español.";
  return `Eres el moderador de una app de transporte público de Trujillo, Perú, donde la
gente publica alertas sobre el tráfico y el estado de las calles.

Rechaza un texto solo si contiene:
- insultos o groserías, incluidos los disfrazados con símbolos o letras cambiadas
- ataques, acoso o amenazas a personas
- contenido sexual, discriminación o incitación al odio
- datos personales de terceros (teléfonos, direcciones, placas con nombre)
- publicidad o estafas

Permite las quejas sobre el servicio, aunque estén escritas con enfado: decir
que un micro es lento, que el conductor maneja mal o que el servicio es pésimo
es una crítica legítima, no una ofensa.

Devuelve el motivo corto y en segunda persona, solo cuando rechaces.
${languageNote}`;
}
