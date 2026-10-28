# 0009. Cifrado de campos con llaves por consultorio (envelope encryption)

- Estado: Aceptada
- Fecha: 2026-10-27

## Contexto

La historia clínica (antecedentes, padecimientos, medicamentos, alergias) es un dato personal sensible (LFPDPPP, art. 3 fr. VI) y parte del expediente (NOM-004). RNF-SEG-02 pide cifrarla a nivel de campo: alguien con acceso a la base, a un respaldo o a un volcado no debe poder leerla. Otros módulos (notas de consulta) necesitarán lo mismo.

## Decisión

### Esquema de llaves

```
Llave maestra (KEK)          fuera de la base: variable de entorno, después AWS KMS
   │ envuelve (AES-256-GCM)
   ▼
Llave de datos (DEK)         una por consultorio y versión, en llave_de_datos, siempre envuelta
   │ cifra (AES-256-GCM)
   ▼
Campos cifrados              bytea en historia_clinica (y en otras tablas que lo necesiten)
```

- **Llave maestra:** `NUTRIKIT_LLAVES_MAESTRAS=id:base64[,id:base64...]` (256 bits cada una) y `NUTRIKIT_LLAVE_MAESTRA_ACTIVA=id`. Sin ellas la app no arranca. En desarrollo se genera una vez en `~/.nutrikit/llave-maestra-dev`, fuera del repositorio. Nunca se registra en logs.
- **Llave de datos:** 256 bits aleatorios por consultorio, creada la primera vez que el consultorio cifra algo. En `llave_de_datos` solo se guarda envuelta, junto con el id de la llave maestra que la envolvió. La tabla tiene RLS.
- **Una llave por consultorio:** limita el alcance de una fuga a un consultorio; permite rotar o destruir las llaves de un cliente sin tocar a los demás (borrar su llave de datos vuelve ilegibles sus datos: *crypto-shredding*); y prepara el camino a llaves gestionadas por el cliente.

### Formato de cada valor cifrado

| Bytes | Contenido |
| --- | --- |
| 1 | Formato (`1`) |
| 4 | Versión de la llave de datos con que se cifró |
| 12 | Nonce aleatorio |
| resto | Texto cifrado + tag de autenticación de 16 bytes |

- **AES-GCM** es cifrado autenticado: si un byte cambia, el descifrado falla con error en lugar de devolver basura.
- **Nonce aleatorio de 96 bits** en cada cifrado: el mismo texto produce resultados distintos, y repetir un nonce con la misma llave rompería GCM.
- **Datos autenticados adicionales (AAD):** formato, versión y `tenant_id`. Un valor copiado a otro consultorio o con la versión alterada no se descifra.

### Uso desde otros módulos

El módulo `encryption` expone convertidores JPA: `CampoCifrado` (texto) y `MapaCifrado` (mapa clave-valor en JSON). Una entidad declara `@Convert(converter = CampoCifrado.class)` sobre una columna `bytea`, y el cifrado y descifrado ocurren al guardar y al leer, siempre con la llave del consultorio de la petición.

`campos_extra` es `jsonb ENC` en el modelo de datos, pero un valor cifrado no puede ser JSON consultable: es `bytea` con el JSON cifrado.

## Plan de rotación

| Qué se rota | Cómo | ¿Se vuelve a cifrar todo? |
| --- | --- | --- |
| **Llave maestra** (anual, o si se filtra) | Agregar la nueva a `NUTRIKIT_LLAVES_MAESTRAS`, marcarla activa y reiniciar. Al arrancar, la app vuelve a envolver cada llave de datos con la nueva. Cuando ninguna dependa de la anterior, se retira. | No: solo se re-envuelven las llaves de datos (una por consultorio). |
| **Llave de datos de un consultorio** (si se sospecha que se filtró) | Crear la versión siguiente; lo nuevo se cifra con ella y lo viejo se sigue leyendo con la suya, porque cada valor dice su versión. Un proceso en segundo plano re-cifra lo viejo y después se borra la versión anterior. | Sí, los datos de ese consultorio. Pendiente de implementar. |

## Consecuencias

- **Protege contra** lectura de la base, respaldos, réplicas o volcados por alguien sin la llave maestra, y contra un DBA curioso.
- **No protege contra** quien controle la aplicación o tenga la llave maestra: la app descifra para cualquier usuario autorizado. Para eso están la autorización, la bitácora de lecturas y la protección de la llave (KMS).
- **Sin búsquedas ni orden** por campos cifrados. Si un día se necesita buscar por alergia, las opciones son un índice ciego (HMAC del valor normalizado) o un catálogo de alergias no cifrado y separado del texto libre.
- **Llave maestra en variable de entorno** es un paso intermedio: en AWS se reemplaza por KMS (`GenerateDataKey`/`Decrypt`), y el formato de los datos no cambia.
- **Perder la llave maestra** es perder los datos cifrados. Debe respaldarse fuera del servidor, con el mismo cuidado que la base.
