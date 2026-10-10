-- Roles de las cuentas.
--
--   cliente                  is_admin = FALSE  (lo que crea el registro público)
--   administrador            is_admin = TRUE
--   administrador principal  is_admin = TRUE y is_super_admin = TRUE
--
-- El principal es el único que puede dar o quitar el acceso de administrador,
-- y solo puede haber uno: lo garantiza el índice único parcial.

ALTER TABLE users ADD COLUMN is_super_admin BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX users_single_super_admin_idx ON users (is_super_admin) WHERE is_super_admin;

-- El principal siempre es administrador.
ALTER TABLE users ADD CONSTRAINT users_super_admin_is_admin CHECK (NOT is_super_admin OR is_admin);

UPDATE users
SET is_admin = TRUE, is_super_admin = TRUE
WHERE email = 'stebandioses@gmail.com';
