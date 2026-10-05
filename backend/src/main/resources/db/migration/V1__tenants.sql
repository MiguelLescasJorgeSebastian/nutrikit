-- Núcleo multi-tenant: consultorios y módulos activos por consultorio.
-- Ver docs/modelo-de-datos.pdf, página 2.

CREATE TABLE tenant (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    slug           varchar(60)  NOT NULL UNIQUE,
    nombre         varchar(150) NOT NULL,
    dominio_propio varchar(255) UNIQUE,
    estado         varchar(20)  NOT NULL DEFAULT 'ACTIVO'
                   CHECK (estado IN ('ACTIVO', 'SUSPENDIDO', 'CANCELADO')),
    creado_en      timestamptz  NOT NULL DEFAULT now()
);

-- Feature flags: un módulo apagado no aparece en menús ni expone endpoints.
CREATE TABLE tenant_modulo (
    tenant_id      uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    modulo         varchar(40)  NOT NULL,
    habilitado     boolean      NOT NULL DEFAULT false,
    config         jsonb        NOT NULL DEFAULT '{}'::jsonb,
    actualizado_en timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, modulo)
);
