/**
 * Piezas transversales que todos los módulos pueden usar: formato de errores de la API y
 * configuración de OpenAPI.
 *
 * <p>Cualquier módulo puede depender de la API pública (la raíz de este paquete), pero este módulo no
 * depende de ninguno. Si algo aquí empieza a saber de pacientes o consultas, va en otro módulo.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Compartido")
package dev.jorgemiguel.nutrikit.shared;
