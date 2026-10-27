package dev.jorgemiguel.nutrikit.tenants.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import dev.jorgemiguel.nutrikit.tenants.domain.EstadoTenant;

@Schema(description = "Estado al que se lleva el consultorio")
record CambiarEstadoRequest(

		@Schema(description = "ACTIVO ⇄ SUSPENDIDO; ambos pueden pasar a CANCELADO, que es final",
				example = "SUSPENDIDO")
		@NotNull(message = "es obligatorio")
		EstadoTenant estado) {
}
