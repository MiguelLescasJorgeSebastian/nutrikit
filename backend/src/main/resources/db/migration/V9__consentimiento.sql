-- Aviso de privacidad y consentimiento del paciente (RNF-LEG-01, LFPDPPP art. 8 y 9).
-- Ver docs/adr/0010-consentimiento.md.

-- Cada consultorio es responsable de los datos de sus pacientes y publica su propio aviso.
-- Una versión publicada no se edita: un cambio es una versión nueva.
CREATE TABLE aviso_de_privacidad (
    id            uuid         PRIMARY KEY,
    tenant_id     uuid         NOT NULL REFERENCES tenant (id),
    version       varchar(20)  NOT NULL CHECK (version ~ '^[A-Za-z0-9._-]+$'),
    contenido     text         NOT NULL,
    publicado_en  timestamptz  NOT NULL,
    CONSTRAINT aviso_de_privacidad_tenant_version_key UNIQUE (tenant_id, version)
);

-- Historial de consentimientos: cada otorgamiento o retiro es una fila nueva. El consentimiento
-- vigente es la última fila del paciente, si es OTORGADO.
-- Difiere del modelo de datos v0.1 (aceptado_en): registrado_en sirve para ambas acciones, y
-- accion y registrado_por distinguen qué pasó y quién lo capturó.
CREATE TABLE consentimiento (
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL REFERENCES tenant (id),
    paciente_id     uuid         NOT NULL REFERENCES paciente (id),
    accion          varchar(10)  NOT NULL CHECK (accion IN ('OTORGADO', 'RETIRADO')),
    -- Versión del aviso aceptada (o vigente al retirar). La FK garantiza que existe en ese consultorio.
    version_aviso   varchar(20)  NOT NULL,
    medio           varchar(10)  NOT NULL CHECK (medio IN ('PRESENCIAL', 'EN_LINEA')),
    registrado_en   timestamptz  NOT NULL,
    -- Usuario que lo capturó; null si lo registró el propio paciente en línea (fase posterior).
    registrado_por  uuid,
    CONSTRAINT consentimiento_aviso_fkey FOREIGN KEY (tenant_id, version_aviso)
        REFERENCES aviso_de_privacidad (tenant_id, version)
);

CREATE INDEX consentimiento_tenant_paciente_idx ON consentimiento (tenant_id, paciente_id, id);

-- Inmutables: la app inserta y lee, nunca modifica ni borra. Un retiro es otra fila.
GRANT SELECT, INSERT ON aviso_de_privacidad, consentimiento TO ${app_user};

ALTER TABLE aviso_de_privacidad ENABLE ROW LEVEL SECURITY;
ALTER TABLE aviso_de_privacidad FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_por_tenant ON aviso_de_privacidad
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE consentimiento ENABLE ROW LEVEL SECURITY;
ALTER TABLE consentimiento FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_por_tenant ON consentimiento
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
