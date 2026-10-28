package dev.jorgemiguel.nutrikit.tenants.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Encender o apagar un módulo")
record CambiarModuloRequest(

		@Schema(example = "false")
		@NotNull(message = "es obligatorio")
		Boolean habilitado) {
}
