package dev.jorgemiguel.nutrikit.tenants.web;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.jorgemiguel.nutrikit.tenants.domain.TenantModulo;

@Schema(description = "Módulo del sistema y si está encendido para el consultorio")
record ModuloResponse(

		@Schema(example = "scheduling")
		String modulo,

		@Schema(example = "true")
		boolean habilitado,

		@Schema(description = "Los módulos de núcleo están siempre encendidos", example = "false")
		boolean nucleo) {

	static ModuloResponse de(TenantModulo modulo) {
		return new ModuloResponse(modulo.getModulo(), modulo.isHabilitado(), modulo.getCatalogo().nucleo());
	}

}
