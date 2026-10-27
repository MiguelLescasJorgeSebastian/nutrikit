package dev.jorgemiguel.nutrikit.tenants.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import dev.jorgemiguel.nutrikit.tenants.domain.Slug;
import dev.jorgemiguel.nutrikit.tenants.domain.Tenant;

/**
 * Alta de consultorio. Valida la forma de la entrada para responder 400 con el detalle por
 * campo; {@link Slug} y {@link Tenant} vuelven a validar porque son la fuente de verdad.
 */
@Schema(description = "Datos para dar de alta un consultorio")
record CrearTenantRequest(

		@Schema(description = "Identificador público; se usa como subdominio", example = "nutri-ana")
		@NotBlank(message = "es obligatorio")
		@Size(min = Slug.LONGITUD_MINIMA, max = Slug.LONGITUD_MAXIMA,
				message = "debe tener entre {min} y {max} caracteres")
		@Pattern(regexp = Slug.FORMATO_REGEX,
				message = "solo admite minúsculas, números y guiones, sin guion al inicio, al final ni repetido")
		String slug,

		@Schema(description = "Nombre comercial del consultorio", example = "Consultorio de Nutrición Ana López")
		@NotBlank(message = "es obligatorio")
		@Size(max = Tenant.NOMBRE_LONGITUD_MAXIMA, message = "admite máximo {max} caracteres")
		String nombre) {
}
