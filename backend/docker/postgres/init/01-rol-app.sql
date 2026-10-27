-- Usuario con el que se conecta la aplicación. No es superusuario, no es dueño de las tablas
-- y no puede saltarse Row-Level Security. Las tablas las crea Flyway con el usuario dueño, y
-- los permisos de este rol se otorgan en las migraciones.
--
-- Postgres corre este script solo al crear el volumen. La misma ruta la usan Docker Compose
-- (desarrollo) y Testcontainers (pruebas). En producción el rol lo crea la infraestructura.
CREATE ROLE nutrikit_app LOGIN PASSWORD 'nutrikit_app' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
