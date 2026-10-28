package dev.jorgemiguel.nutrikit.encryption.infrastructure;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Llaves maestras ({@code nutrikit.cifrado.*}).
 *
 * @param llavesMaestras {@code id:base64[,id:base64...]}, de {@code NUTRIKIT_LLAVES_MAESTRAS}
 * @param llaveMaestraActiva id de la que envuelve las llaves nuevas; obligatoria si hay más de una
 * @param archivoLlaveLocal solo desarrollo: si no hay llaves maestras, se usa (o se crea) una en
 * este archivo, fuera del repositorio
 */
@ConfigurationProperties("nutrikit.cifrado")
record CifradoProperties(String llavesMaestras, String llaveMaestraActiva, Path archivoLlaveLocal) {
}
