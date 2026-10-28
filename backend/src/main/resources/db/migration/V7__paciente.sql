-- Pacientes del consultorio (RF-PAC-01, RF-PAC-02, RF-PAC-04). Ver docs/modelo-de-datos.pdf,
-- página 3, y docs/adr/0008-pacientes.md.

-- Búsqueda sin importar acentos ni mayúsculas, con índice trigram. Las tres extensiones son
-- "trusted": el dueño de la base las puede crear sin ser superusuario.
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- btree_gin permite que el índice GIN empiece por tenant_id, como todos los índices de negocio.
CREATE EXTENSION IF NOT EXISTS btree_gin;

-- unaccent() no es IMMUTABLE (depende del diccionario configurado), así que no se puede usar en
-- una columna generada ni en un índice. Esta envoltura fija el diccionario y sí lo es.
CREATE FUNCTION inmutable_unaccent(texto text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    RETURN public.unaccent('public.unaccent'::regdictionary, texto);

CREATE TABLE paciente (
    id               uuid         PRIMARY KEY,
    -- Sin ON DELETE CASCADE: el expediente se conserva aunque se intente borrar el consultorio.
    tenant_id        uuid         NOT NULL REFERENCES tenant (id),
    nombre           varchar(100) NOT NULL,
    apellidos        varchar(150) NOT NULL,
    fecha_nacimiento date         NOT NULL,
    -- Sexo biológico: lo usan las fórmulas de gasto energético y composición corporal.
    sexo             varchar(10)  NOT NULL CHECK (sexo IN ('FEMENINO', 'MASCULINO')),
    telefono         varchar(20),
    email            citext,
    ocupacion        varchar(100),
    -- Baja lógica: el paciente deja de aparecer, pero su expediente se conserva (NOM-004).
    eliminado_en     timestamptz,
    creado_en        timestamptz  NOT NULL,
    -- Bloqueo optimista: cada edición incrementa la versión; una edición con versión vieja falla.
    version          bigint       NOT NULL DEFAULT 0,
    -- Lo que se busca, ya normalizado: "José Pérez 5512345678" → "jose perez 5512345678".
    texto_busqueda   text         GENERATED ALWAYS AS (
                         lower(inmutable_unaccent(nombre || ' ' || apellidos || coalesce(' ' || telefono, '')))
                     ) STORED,
    CONSTRAINT paciente_fecha_nacimiento_check CHECK (fecha_nacimiento >= DATE '1900-01-01')
);

-- Búsqueda: LIKE '%termino%' sobre el texto normalizado. Solo pacientes activos.
CREATE INDEX paciente_busqueda_idx ON paciente
    USING gin (tenant_id, texto_busqueda gin_trgm_ops)
    WHERE eliminado_en IS NULL;

-- Listado por orden alfabético.
CREATE INDEX paciente_tenant_apellidos_idx ON paciente (tenant_id, apellidos, nombre)
    WHERE eliminado_en IS NULL;

-- Sin DELETE: la baja es lógica y la base lo garantiza aunque el código tenga un error.
GRANT SELECT, INSERT, UPDATE ON paciente TO ${app_user};

ALTER TABLE paciente ENABLE ROW LEVEL SECURITY;
ALTER TABLE paciente FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_por_tenant ON paciente
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
