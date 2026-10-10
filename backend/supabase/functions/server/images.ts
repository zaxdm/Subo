export interface ImageStorage {
  upload(bytes: Uint8Array, contentType: string): Promise<string>;
}

/** La clave de servicio nunca llega al navegador. */
export class SupabaseImages implements ImageStorage {
  constructor(private readonly url: string, private readonly key: string) {}

  async upload(bytes: Uint8Array, contentType: string): Promise<string> {
    if (!this.url || !this.key) throw new Error("Storage no configurado");
    const extension = { "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp" }[contentType];
    if (!extension) throw new Error("Formato de imagen no permitido");
    const path = `${crypto.randomUUID()}.${extension}`;
    const response = await fetch(`${this.url}/storage/v1/object/route-images/${path}`, {
      method: "POST",
      headers: { Authorization: `Bearer ${this.key}`, apikey: this.key, "Content-Type": contentType },
      body: bytes as BodyInit,
    });
    if (!response.ok) {
      await response.body?.cancel();
      throw new Error(`No se pudo subir la imagen (${response.status})`);
    }
    await response.body?.cancel();
    return `${this.url}/storage/v1/object/public/route-images/${path}`;
  }
}
