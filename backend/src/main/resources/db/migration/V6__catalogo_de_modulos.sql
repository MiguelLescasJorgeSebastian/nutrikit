-- Catálogo de módulos por consultorio (RF-TEN-05). Ver docs/adr/0007-modulos-por-consultorio.md.
--
-- Los códigos son los de dev.jorgemiguel.nutrikit.tenants.ModuloDelConsultorio (el nombre del
-- paquete de cada módulo en Spring Modulith). Si agregas un módulo, cambia el CHECK en una
-- migración nueva y agrega su fila a los consultorios existentes.

-- FORCE ROW LEVEL SECURITY también aplica al dueño de la tabla, y en esta migración no hay
-- consultorio en contexto. Se quita mientras se corrigen los datos de todos y se restaura al final.
ALTER TABLE tenant_modulo NO FORCE ROW LEVEL SECURITY;

-- Hasta ahora el nombre del módulo era texto libre. Las filas que no son del catálogo no
-- corresponden a ningún módulo real y no se pueden conservar con el CHECK.
DELETE FROM tenant_modulo
WHERE modulo NOT IN ('patients', 'consultations', 'patientaccess', 'mealplans', 'integrations', 'scheduling', 'website');

ALTER TABLE tenant_modulo ADD CONSTRAINT tenant_modulo_modulo_check
    CHECK (modulo IN ('patients', 'consultations', 'patientaccess', 'mealplans', 'integrations', 'scheduling', 'website'));

-- Los consultorios que ya existen reciben todos los módulos con su valor por defecto.
-- Los nuevos los registra la app al darlos de alta.
INSERT INTO tenant_modulo (tenant_id, modulo, habilitado)
SELECT t.id, m.modulo, m.habilitado
FROM tenant t
CROSS JOIN (VALUES
    ('patients', true),
    ('consultations', true),
    ('patientaccess', true),
    ('mealplans', false),
    ('integrations', false),
    ('scheduling', false),
    ('website', false)
) AS m (modulo, habilitado)
ON CONFLICT (tenant_id, modulo) DO NOTHING;

-- Un módulo de núcleo nunca queda apagado.
UPDATE tenant_modulo SET habilitado = true WHERE modulo = 'patients';

ALTER TABLE tenant_modulo FORCE ROW LEVEL SECURITY;
