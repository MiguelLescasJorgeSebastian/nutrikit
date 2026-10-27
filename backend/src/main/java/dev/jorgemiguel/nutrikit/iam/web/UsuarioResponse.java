package dev.jorgemiguel.nutrikit.iam.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.jorgemiguel.nutrikit.iam.domain.RolUsuario;
import dev.jorgemiguel.nutrikit.iam.domain.Usuario;

/** Nunca incluye el hash de la contraseña. */
@Schema(description = "Usuario de un consultorio")
record UsuarioResponse(UUID id, UUID tenantId, String email, String nombre, RolUsuario rol, boolean activo) {

	static UsuarioResponse de(Usuario usuario) {
		return new UsuarioResponse(usuario.getId(), usuario.getTenantId(), usuario.getEmail(), usuario.getNombre(),
				usuario.getRol(), usuario.isActivo());
	}

}
