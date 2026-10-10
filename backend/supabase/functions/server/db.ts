import postgres from "postgres";

/**
 * Conexión a la base de datos de Supabase.
 *
 * Sin ORM ni cliente REST: el SQL está a la vista, igual que en el servidor
 * Ktor. Supabase le pasa a cada función `SUPABASE_DB_URL`, la conexión directa
 * a Postgres, que no pasa por RLS.
 */
export type Sql = postgres.Sql;

export function connect(databaseUrl: string): Sql {
  return postgres(databaseUrl, {
    // El pooler de Supabase (modo transacción) no admite sentencias preparadas.
    prepare: false,
    // Una función atiende pocas peticiones a la vez: no hace falta un pool grande.
    max: 3,
    types: {
      // Las fechas se guardan en milisegundos (BIGINT). Caben de sobra en un
      // number y así llegan a la app como en el servidor Ktor, no como texto.
      bigint: {
        to: 20,
        from: [20],
        serialize: (value: number) => value.toString(),
        parse: (value: string) => Number(value),
      },
    },
  });
}
