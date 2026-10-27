package dev.jorgemiguel.nutrikit.tenants.web;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.jorgemiguel.nutrikit.tenants.domain.EstadoTenant;
import dev.jorgemiguel.nutrikit.tenants.domain.Tenant;

/** Representación pública del consultorio. La entidad JPA nunca sale de la capa web. */
@Schema(description = "Consultorio")
record TenantResponse(

		@Schema(example = "019a2b3c-4d5e-7f60-8a1b-2c3d4e5f6a7b")
		UUID id,

		@Schema(example = "nutri-ana")
		String slug,

		@Schema(example = "Consultorio de Nutrición Ana López")
		String nombre,

		@Schema(description = "Dominio propio del sitio público, si tiene", example = "nutriana.mx", nullable = true)
		String dominioPropio,

		@Schema(example = "ACTIVO")
		EstadoTenant estado,

		@Schema(example = "2026-10-27T18:30:00Z")
		Instant creadoEn) {

	static TenantResponse de(Tenant tenant) {
		return new TenantResponse(
				tenant.getId(),
				tenant.getSlug().valor(),
				tenant.getNombre(),
				tenant.getDominioPropio(),
				tenant.getEstado(),
				tenant.getCreadoEn());
	}

}
