-- Enlace privado del paciente (RF-ACC-01 a RF-ACC-07). Ver docs/adr/0003-acceso-paciente-enlace.md
-- y docs/adr/0014-enlace-privado-del-paciente.md.

CREATE TABLE enlace_acceso (
    id             uuid         PRIMARY KEY,
    tenant_id      uuid         NOT NULL REFERENCES tenant (id),
    -- Referencia lógica a otro módulo, sin FK (ADR-0001).
    paciente_id    uuid         NOT NULL,
    -- Solo el SHA-256 del token: si la base se filtra, los enlaces no se pueden reconstruir.
    token_hash     char(64)     NOT NULL UNIQUE,
    -- Argon2; null = sin PIN.
    pin_hash       varchar(100),
    expira_en      timestamptz,
    revocado_en    timestamptz,
    ultimo_uso_en  timestamptz,
    creado_en      timestamptz  NOT NULL
);

-- Un solo enlace vigente por paciente: regenerar revoca el anterior.
CREATE UNIQUE INDEX enlace_acceso_vigente_por_paciente ON enlace_acceso (tenant_id, paciente_id)
    WHERE revocado_en IS NULL;

-- La app crea enlaces y solo puede cambiar su estado; el token y el paciente no cambian nunca.
GRANT SELECT, INSERT ON enlace_acceso TO ${app_user};
GRANT UPDATE (pin_hash, expira_en, revocado_en, ultimo_uso_en) ON enlace_acceso TO ${app_user};

ALTER TABLE enlace_acceso ENABLE ROW LEVEL SECURITY;
ALTER TABLE enlace_acceso FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_por_tenant ON enlace_acceso
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- La bitácora registra al paciente como actor cuando entra por su enlace.
ALTER TABLE auditoria DROP CONSTRAINT auditoria_actor_tipo_check;
ALTER TABLE auditoria ADD CONSTRAINT auditoria_actor_tipo_check
    CHECK (actor_tipo IN ('USUARIO', 'OPERADOR', 'ANONIMO', 'SISTEMA', 'PACIENTE'));
