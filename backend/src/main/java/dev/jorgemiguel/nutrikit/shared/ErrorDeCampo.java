package dev.jorgemiguel.nutrikit.shared;

/**
 * Un error de validación de un campo de la petición. Va en la propiedad {@code errores} de las
 * respuestas Problem Details con estado 400.
 *
 * @param campo nombre del campo en el JSON de entrada
 * @param mensaje qué está mal, para mostrarlo al usuario
 */
public record ErrorDeCampo(String campo, String mensaje) {
}
