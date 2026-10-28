/**
 * Módulo Cifrado: cifrado de campos sensibles con una llave de datos por consultorio, envuelta por
 * una llave maestra (envelope encryption). Ver docs/adr/0009-cifrado-de-campos.md.
 *
 * <p>API pública: los convertidores JPA {@link CampoCifrado} y {@link MapaCifrado}, y
 * {@link DatoCifradoInvalidoException}. Las llaves y el algoritmo son internos.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Cifrado")
package dev.jorgemiguel.nutrikit.encryption;
