package dev.jorgemiguel.nutrikit.tenants.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cómo se identifica el consultorio de cada petición.
 *
 * @param dominioBase dominio de la plataforma; el consultorio es el subdominio ({@code nutri-ana.nutrikit.mx})
 * @param cabeceraHabilitada si se acepta la cabecera; solo en desarrollo, porque cualquiera podría enviarla
 * @param nombreCabecera cabecera con el slug del consultorio
 */
@ConfigurationProperties("nutrikit.tenancy")
record TenancyProperties(
		@DefaultValue("nutrikit.mx") String dominioBase,
		@DefaultValue("false") boolean cabeceraHabilitada,
		@DefaultValue("X-Tenant") String nombreCabecera) {
}
