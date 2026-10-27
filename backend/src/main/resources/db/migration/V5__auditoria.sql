-- Bitácora de auditoría append-only, particionada por mes (RNF-SEG-03, NOM-004).
-- Ver docs/modelo-de-datos.pdf, página 3, y docs/adr/0006-bitacora-de-auditoria.md.
--
-- Sin FK a tenant: la bitácora se conserva aunque el consultorio se borre.

CREATE TABLE auditoria (
    id            bigint       GENERATED ALWAYS AS IDENTITY,
    tenant_id     uuid         NOT NULL,
    actor_tipo    varchar(20)  NOT NULL
                  CHECK (actor_tipo IN ('USUARIO', 'OPERADOR', 'ANONIMO', 'SISTEMA')),
    actor_id      uuid,
    accion        varchar(50)  NOT NULL,
    recurso_tipo  varchar(50)  NOT NULL,
    recurso_id    uuid,
    ip            inet,
    user_agent    text,
    ocurrido_en   timestamptz  NOT NULL,
    -- En una tabla particionada, la llave primaria debe incluir la columna de partición.
    PRIMARY KEY (id, ocurrido_en)
) PARTITION BY RANGE (ocurrido_en);

-- Se crea en cada partición. Consultas típicas: la bitácora de un consultorio por fechas.
CREATE INDEX auditoria_tenant_ocurrido_idx ON auditoria (tenant_id, ocurrido_en);

-- Append-only: la app solo inserta y lee. Ni UPDATE ni DELETE, ni siquiera por error.
-- Las particiones no reciben permisos: la app solo llega a ellas a través de la tabla padre.
GRANT SELECT, INSERT ON auditoria TO ${app_user};

ALTER TABLE auditoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_por_tenant ON auditoria
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Crear particiones es DDL y la app no tiene ese permiso. Esta función corre con los permisos de
-- su dueño (SECURITY DEFINER) y solo sabe hacer una cosa: crear la partición del mes indicado si
-- no existe. La app la llama a diario para tener listos el mes actual y los dos siguientes.
--
-- No hay partición DEFAULT a propósito: si faltara la del mes, el INSERT falla, el evento queda
-- pendiente en event_publication y se reintenta cuando la partición exista. Una DEFAULT recibiría
-- esas filas y después impediría crear la partición del mes.
CREATE FUNCTION asegurar_particion_auditoria(mes date) RETURNS text
    LANGUAGE plpgsql
    SECURITY DEFINER
    -- Ruta fija: evita que quien la llama la engañe con objetos propios del mismo nombre.
    SET search_path = public, pg_temp
AS $$
DECLARE
    inicio date := date_trunc('month', mes)::date;
    fin    date := (date_trunc('month', mes) + interval '1 month')::date;
    nombre text := 'auditoria_' || to_char(inicio, 'YYYY_MM');
BEGIN
    IF to_regclass(nombre) IS NULL THEN
        EXECUTE format('CREATE TABLE %I PARTITION OF auditoria FOR VALUES FROM (%L) TO (%L)', nombre, inicio, fin);
    END IF;
    RETURN nombre;
EXCEPTION
    -- Dos instancias de la app la llamaron a la vez: la otra ya la creó.
    WHEN duplicate_table THEN
        RETURN nombre;
END;
$$;

REVOKE ALL ON FUNCTION asegurar_particion_auditoria(date) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION asegurar_particion_auditoria(date) TO ${app_user};

-- Mes actual y los dos siguientes; después los mantiene la app.
SELECT asegurar_particion_auditoria((date_trunc('month', now()) + make_interval(months => m))::date)
FROM generate_series(0, 2) AS m;
