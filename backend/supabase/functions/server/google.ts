import { createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from "jose";

export interface GoogleIdentity {
  subject: string;
  email: string;
  displayName: string;
}
export interface GoogleVerifier {
  readonly isEnabled: boolean;
  verify(idToken: string, nonce: string): Promise<GoogleIdentity | null>;
}

/** Se verifican tokens firmados, nunca IDs o correos enviados por el cliente. */
export class GoogleTokens implements GoogleVerifier {
  readonly isEnabled: boolean;
  private readonly keys: JWTVerifyGetKey;
  constructor(private readonly clientIds: string[], keys?: JWTVerifyGetKey) {
    this.isEnabled = clientIds.length > 0;
    this.keys = keys ?? createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));
  }
  async verify(idToken: string, nonce: string): Promise<GoogleIdentity | null> {
    if (!this.isEnabled || idToken.length > 16_000 || nonce.length < 32 || nonce.length > 128) return null;
    try {
      const { payload } = await jwtVerify(idToken, this.keys, {
        algorithms: ["RS256"],
        audience: this.clientIds,
        issuer: ["https://accounts.google.com", "accounts.google.com"],
        requiredClaims: ["sub", "email", "email_verified", "exp", "iat", "nonce"],
        maxTokenAge: "1h",
        clockTolerance: 30,
      });
      if (
        payload.nonce !== nonce || payload.email_verified !== true ||
        typeof payload.sub !== "string" || !payload.sub || payload.sub.length > 255 ||
        typeof payload.email !== "string" || !payload.email.includes("@") || payload.email.length > 254
      ) return null;
      const email = payload.email.trim().toLowerCase();
      const rawName = typeof payload.name === "string" ? payload.name : email.split("@")[0];
      const displayName = rawName.trim().replace(/\s+/g, " ").slice(0, 40) || "Usuario";
      return { subject: payload.sub, email, displayName };
    } catch {
      return null;
    }
  }
}
export async function nonceHash(nonce: string): Promise<string> {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(nonce));
  return [...new Uint8Array(bytes)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
