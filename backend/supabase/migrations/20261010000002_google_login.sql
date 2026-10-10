-- Las cuentas Google no necesitan una contraseña local inventada.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN google_sub TEXT UNIQUE;

CREATE TABLE google_login_challenges (
    nonce_hash TEXT PRIMARY KEY,
    expires_at BIGINT NOT NULL
);
CREATE INDEX google_challenges_expiry_idx ON google_login_challenges (expires_at);
ALTER TABLE google_login_challenges ENABLE ROW LEVEL SECURITY;
