-- Llaves de datos por consultorio e historia clínica cifrada (RF-PAC-03, RNF-SEG-02).
-- Ver docs/adr/0009-cifrado-de-campos.md.

-- Llave de datos (DEK) de cada consultorio, siempre envuelta con una llave maestra que no está en
-- la base. Sin la llave maestra, esta tabla no sirve para descifrar nada.
CREATE TABLE llave_de_datos (
    tenant_id         uuid        NOT NULL REFERENCES tenant (id),
    version           integer     NOT NULL CHECK (version > 0),
    -- nonce (12) + llave cifrada (32) + tag (16)
    llave_envuelta    bytea       NOT NULL,
    -- Con qué llave maestra se envolvió; al rotar la maestra se re-envuelve y cambia este id.
    llave_maestra_id  varchar(50) NOT NULL,
    creada_en         timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, version)
);

-- La app crea llaves y las re-envuelve al rotar la maestra; no las borra ni cambia su versión.
GRANT SELECT, INSERT ON llave_de_datos TO ${app_user};
GRANT UPDATE (llave_envuelta, llave_maestra_id) ON llave_de_datos TO ${app_user};

ALTER TABLE llave_de_datos ENABLE ROW LEVEL SECURITY;
ALTER TABLE llave_de_datos FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_por_tenant ON llave_de_datos
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Historia clínica: una por paciente. Los campos sensibles son bytea con el formato de
-- encryption.CifradorAesGcm (formato, versión de llave, nonce, texto cifrado y tag).
-- campos_extra es jsonb ENC en el modelo de datos: cifrado ya no es JSON, así que es bytea.
CREATE TABLE historia_clinica (
    id              uuid        PRIMARY KEY,
    tenant_id       uuid        NOT NULL REFERENCES tenant (id),
    paciente_id     uuid        NOT NULL UNIQUE REFERENCES paciente (id),
    antecedentes    bytea,
    padecimientos   bytea,
    medicamentos    bytea,
    alergias        bytea,
    campos_extra    bytea,
    actualizado_en  timestamptz NOT NULL,
    version         bigint      NOT NULL DEFAULT 0
);

CREATE INDEX historia_clinica_tenant_paciente_idx ON historia_clinica (tenant_id, paciente_id);

-- Sin DELETE: es parte del expediente.
GRANT SELECT, INSERT, UPDATE ON historia_clinica TO ${app_user};

ALTER TABLE historia_clinica ENABLE ROW LEVEL SECURITY;
ALTER TABLE historia_clinica FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_por_tenant ON historia_clinica
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
