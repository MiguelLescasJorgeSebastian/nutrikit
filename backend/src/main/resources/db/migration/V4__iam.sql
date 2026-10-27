-- Identidad y acceso: usuarios del consultorio y refresh tokens rotativos.
-- Ver docs/modelo-de-datos.pdf, página 3, y docs/adr/0005-autenticacion-jwt.md.

-- citext: el email se compara sin distinguir mayúsculas (Ana@x.mx = ana@x.mx).
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE usuario (
    id             uuid         PRIMARY KEY,
    tenant_id      uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    email          citext       NOT NULL,
    password_hash  varchar(100) NOT NULL,
    nombre         varchar(150) NOT NULL,
    rol            varchar(20)  NOT NULL
                   CHECK (rol IN ('TITULAR', 'COLABORADOR', 'ASISTENTE')),
    activo         boolean      NOT NULL DEFAULT true,
    ultimo_acceso  timestamptz,
    -- El mismo correo puede existir en dos consultorios distintos, no dos veces en el mismo.
    CONSTRAINT usuario_tenant_email_key UNIQUE (tenant_id, email)
);

-- tenant_id no está en el modelo de datos v0.1; se agrega por la regla del núcleo
-- ("tenant_id en todas las tablas + Row-Level Security").
CREATE TABLE refresh_token (
    id           uuid        PRIMARY KEY,
    tenant_id    uuid        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    usuario_id   uuid        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    -- Solo el SHA-256 en hexadecimal; el token en claro nunca se guarda.
    token_hash   char(64)    NOT NULL UNIQUE,
    expira_en    timestamptz NOT NULL,
    revocado_en  timestamptz
);

CREATE INDEX refresh_token_tenant_usuario_idx ON refresh_token (tenant_id, usuario_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON usuario, refresh_token TO ${app_user};

ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuario FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_por_tenant ON usuario
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE refresh_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_token FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_por_tenant ON refresh_token
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
