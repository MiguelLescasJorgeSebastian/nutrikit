-- Indicadores calculados (RF-CON-03): IMC, índice cintura-cadera y gasto energético basal.
-- Ver docs/adr/0012-indicadores-calculados.md.

-- La política de inserción de indicador solo admite filas del consultorio actual. Esta migración
-- agrega globales, así que se quita FORCE mientras tanto (el dueño de la tabla no queda sujeto a
-- RLS) y se restaura al final. Igual que en V6.
ALTER TABLE indicador NO FORCE ROW LEVEL SECURITY;

-- Sin rango estricto de captura: nadie los captura, se calculan de valores ya validados.
INSERT INTO indicador (id, tenant_id, codigo, nombre, unidad, calculado, formula, valor_minimo, valor_maximo) VALUES
    (gen_random_uuid(), NULL, 'imc',                    'Índice de masa corporal',  'kg/m²',   true, 'peso / talla²',            0, 999999),
    (gen_random_uuid(), NULL, 'indice_cintura_cadera',  'Índice cintura-cadera',    'razón',   true, 'cintura / cadera',         0, 999999),
    (gen_random_uuid(), NULL, 'gasto_energetico_basal', 'Gasto energético basal',   'kcal/día',true, 'según el consultorio',     0, 999999);

ALTER TABLE indicador FORCE ROW LEVEL SECURITY;

-- Corregir una medición capturada solo cambia su valor; el resto de la fila no se toca.
GRANT UPDATE (valor) ON medicion TO ${app_user};

-- Fórmula de gasto energético que prefiere cada consultorio. Sin fila, Mifflin-St Jeor.
CREATE TABLE configuracion_de_calculos (
    tenant_id      uuid        PRIMARY KEY REFERENCES tenant (id),
    formula_geb    varchar(20) NOT NULL CHECK (formula_geb IN ('MIFFLIN_ST_JEOR', 'HARRIS_BENEDICT')),
    actualizado_en timestamptz NOT NULL
);

GRANT SELECT, INSERT, UPDATE ON configuracion_de_calculos TO ${app_user};

ALTER TABLE configuracion_de_calculos ENABLE ROW LEVEL SECURITY;
ALTER TABLE configuracion_de_calculos FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_por_tenant ON configuracion_de_calculos
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
