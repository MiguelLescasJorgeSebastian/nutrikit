-- Aislamiento entre consultorios. Ver docs/adr/0004-implementacion-aislamiento-tenant.md.
--
-- ${app_user} es el rol con el que se conecta la aplicación (placeholder de Flyway).
-- Regla para migraciones nuevas: cada tabla otorga sus permisos de forma explícita, y cada
-- tabla con tenant_id activa RLS con la misma política.

-- 1. Permisos de la aplicación: solo datos, nunca DDL. No es dueña de ninguna tabla.
GRANT USAGE ON SCHEMA public TO ${app_user};
GRANT SELECT, INSERT, UPDATE, DELETE ON tenant, tenant_modulo, event_publication TO ${app_user};

-- 2. Row-Level Security en tablas con tenant_id.
--    app.tenant_id lo fija la aplicación al inicio de cada transacción con
--    set_config('app.tenant_id', <uuid>, true). Sin variable, la política no deja ver ni
--    escribir nada (falla cerrada). NULLIF: cuando la variable ya se usó en la sesión y la
--    transacción terminó, vale '' y no NULL, y ''::uuid daría error.
--    FORCE: la política aplica incluso al dueño de la tabla.
--
--    La tabla tenant no lleva RLS: el operador de la plataforma lista y administra todos los
--    consultorios, y la resolución del consultorio de cada petición la consulta antes de
--    conocer el tenant.
ALTER TABLE tenant_modulo ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_modulo FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_por_tenant ON tenant_modulo
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
