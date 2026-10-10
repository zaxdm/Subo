// Lo que viaja entre la app y el servidor. Los nombres coinciden con los de la
// app (y con los DTO del servidor Ktor) para que la app no tenga que cambiar.

/** Qué puede hacer una cuenta. El registro público solo crea clientes. */
export type UserRole = "client" | "admin" | "super_admin";

export interface UserDto {
  id: string;
  email: string;
  displayName: string;
  /** La app decide con esto si abre la vista de cliente o el panel de administración. */
  role: UserRole;
}

export interface SessionResponse {
  token: string;
  user: UserDto;
}

export interface AlertDto {
  id: string;
  type: string;
  latitude: number;
  longitude: number;
  placeName: string | null;
  comment: string | null;
  createdAt: number;
  expiresAt: number;
  confirmations: number;
  dismissals: number;
  lastConfirmedAt: number;
  /** Voto de quien pregunta: STILL_THERE, GONE o nada. */
  myVote: string | null;
  createdByMe: boolean;
}

/** Una sugerencia del buzón, tal como la ve el administrador. */
export interface FeedbackDto {
  id: string;
  subject: string;
  message: string;
  appVersion: string;
  device: string;
  createdAt: number;
  /** Quién la mandó; `null` si esa cuenta ya no existe. */
  userName: string | null;
  userEmail: string | null;
}

export type StopDto = {
  name: string;
  latitude: number;
  longitude: number;
};

export type CoordinateDto = {
  latitude: number;
  longitude: number;
};

export interface RouteDto {
  vehicleName?: string;
  imageUrl?: string | null;
  description?: string;
  bidirectional?: boolean;
  id: string;
  letter: string;
  name: string;
  origin: string;
  destination: string;
  durationMinutes: number;
  fare: number;
  schedule: string;
  stops: StopDto[];
  accentIndex: number;
  path: CoordinateDto[] | null;
  pathSnapped: boolean;
  createdByUser: boolean;
  /** PENDING, APPROVED o REJECTED: las grabadas se revisan antes de compartirse. */
  status: string;
  createdByMe: boolean;
}

export interface SubmitRouteRequest {
  vehicleName?: string;
  imageUrl?: string | null;
  description?: string;
  bidirectional?: boolean;
  /**
   * Id que la app ya le dio a la ruta en el teléfono. Se respeta si está
   * libre, para que la misma ruta no acabe con dos ids distintos.
   */
  id: string | null;
  letter: string;
  name: string;
  origin: string;
  destination: string;
  durationMinutes: number;
  fare: number;
  schedule: string;
  stops: StopDto[];
  path: CoordinateDto[] | null;
  pathSnapped: boolean;
}
