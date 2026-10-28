-- Consultas, mediciones y catálogo de indicadores (RF-CON-01, RF-CON-02, RF-MED-02, RF-MED-03).
-- Ver docs/modelo-de-datos.pdf, página 4, y docs/adr/0011-consultas-y-mediciones.md.

-- Catálogo de indicadores. tenant_id NULL = global (sembrado aquí, de solo lectura para los
-- consultorios); con tenant_id = propio del consultorio. Agregar un indicador es una fila, no una
-- columna: no requiere migración.
-- valor_minimo y valor_maximo no están en el modelo de datos v0.1: definen el rango razonable de
-- cada indicador para rechazar errores de captura (un peso de 5000 kg).
CREATE TABLE indicador (
    id            uuid          PRIMARY KEY,
    tenant_id     uuid          REFERENCES tenant (id),
    codigo        varchar(40)   NOT NULL CHECK (codigo ~ '^[a-z][a-z0-9_]*$'),
    nombre        varchar(100)  NOT NULL,
    unidad        varchar(20)   NOT NULL,
    calculado     boolean       NOT NULL DEFAULT false,
    formula       varchar(60),
    valor_minimo  numeric(10,3) NOT NULL,
    valor_maximo  numeric(10,3) NOT NULL,
    CONSTRAINT indicador_rango_check CHECK (valor_minimo < valor_maximo)
);

-- Código único dentro del consultorio y único entre los globales. Que un código propio no choque
-- con uno global lo valida la aplicación.
CREATE UNIQUE INDEX indicador_tenant_codigo_key ON indicador (tenant_id, codigo) WHERE tenant_id IS NOT NULL;
CREATE UNIQUE INDEX indicador_global_codigo_key ON indicador (codigo) WHERE tenant_id IS NULL;

-- Catálogo global base.
INSERT INTO indicador (id, tenant_id, codigo, nombre, unidad, valor_minimo, valor_maximo) VALUES
    (gen_random_uuid(), NULL, 'peso',                  'Peso',                     'kg',    0.5,  500),
    (gen_random_uuid(), NULL, 'talla',                 'Talla',                    'cm',     30,  250),
    (gen_random_uuid(), NULL, 'circunferencia_cintura','Circunferencia de cintura','cm',     20,  300),
    (gen_random_uuid(), NULL, 'circunferencia_cadera', 'Circunferencia de cadera', 'cm',     20,  300),
    (gen_random_uuid(), NULL, 'porcentaje_grasa',      'Grasa corporal',           '%',       1,   80),
    (gen_random_uuid(), NULL, 'porcentaje_musculo',    'Masa muscular',            '%',       5,   80),
    (gen_random_uuid(), NULL, 'grasa_visceral',        'Grasa visceral',           'nivel',   1,   60),
    (gen_random_uuid(), NULL, 'agua_corporal',         'Agua corporal total',      'L',       5,  120);

-- Consulta. paciente_id, nutriologo_id y cita_id son referencias lógicas a otros módulos: sin FK,
-- para que cada módulo se pueda apagar o extraer (ADR-0001). La app valida que el paciente exista.
CREATE TABLE consulta (
    id             uuid         PRIMARY KEY,
    tenant_id      uuid         NOT NULL REFERENCES tenant (id),
    paciente_id    uuid         NOT NULL,
    nutriologo_id  uuid,
    cita_id        uuid,
    fecha          timestamptz  NOT NULL,
    motivo         varchar(255),
    -- Cifradas con la llave del consultorio (ADR-0009).
    notas          bytea,
    creado_en      timestamptz  NOT NULL
);

-- Línea de tiempo del paciente: de la más reciente a la más antigua.
CREATE INDEX consulta_tenant_paciente_fecha_idx ON consulta (tenant_id, paciente_id, fecha DESC, id DESC);

-- Medición: un valor de un indicador en una consulta.
CREATE TABLE medicion (
    id              uuid          PRIMARY KEY,
    tenant_id       uuid          NOT NULL REFERENCES tenant (id),
    consulta_id     uuid          NOT NULL REFERENCES consulta (id),
    indicador_id    uuid          NOT NULL REFERENCES indicador (id),
    valor           numeric(10,3) NOT NULL,
    -- CALCULADO lo usan los indicadores derivados (IMC, cintura-cadera).
    origen          varchar(15)   NOT NULL CHECK (origen IN ('MANUAL', 'INBODY_CSV', 'CALCULADO')),
    importacion_id  uuid,
    CONSTRAINT medicion_consulta_indicador_key UNIQUE (consulta_id, indicador_id)
);

-- Series para gráficas: un indicador a lo largo del tiempo.
CREATE INDEX medicion_tenant_indicador_idx ON medicion (tenant_id, indicador_id);

-- El expediente no se borra: solo se agrega.
GRANT SELECT, INSERT ON indicador TO ${app_user};
GRANT SELECT, INSERT ON consulta, medicion TO ${app_user};

-- RLS. En indicador, los globales (tenant_id NULL) se leen desde cualquier consultorio, pero solo
-- se insertan indicadores del consultorio actual: un consultorio no puede crear globales.
ALTER TABLE indicador ENABLE ROW LEVEL SECURITY;
ALTER TABLE indicador FORCE ROW LEVEL SECURITY;
CREATE POLICY lectura_global_o_propia ON indicador FOR SELECT
    USING (tenant_id IS NULL OR tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY escritura_propia ON indicador FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE consulta ENABLE ROW LEVEL SECURITY;
ALTER TABLE consulta FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_por_tenant ON consulta
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE medicion ENABLE ROW LEVEL SECURITY;
ALTER TABLE medicion FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_por_tenant ON medicion
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
