package dev.jorgemiguel.nutrikit.iam.application;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jorgemiguel.nutrikit.iam.domain.RolUsuario;
import dev.jorgemiguel.nutrikit.iam.domain.Usuario;
import dev.jorgemiguel.nutrikit.iam.domain.UsuarioRepository;
import dev.jorgemiguel.nutrikit.shared.ConsultorioNoDisponibleException;
import dev.jorgemiguel.nutrikit.shared.TenantContext;
import dev.jorgemiguel.nutrikit.tenants.Consultorios;

/** Alta de usuarios de un consultorio desde el panel de operador. */
@Service
public class UsuarioService {

	private final UsuarioRepository usuarios;
	private final PasswordEncoder passwordEncoder;
	private final Consultorios consultorios;
	private final TransactionTemplate tx;

	UsuarioService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, Consultorios consultorios,
			TransactionTemplate tx) {
		this.usuarios = usuarios;
		this.passwordEncoder = passwordEncoder;
		this.consultorios = consultorios;
		this.tx = tx;
	}

	/**
	 * El operador no pertenece a ningún consultorio, así que la operación entra explícitamente al
	 * consultorio destino ({@link TenantContext#ejecutarComo}) antes de abrir la transacción: RLS
	 * aplica igual que para cualquier otro usuario, sin saltárselo.
	 */
	public Usuario registrarEnConsultorio(UUID tenantId, String email, String nombre, RolUsuario rol, String password) {
		if (!consultorios.existe(tenantId)) {
			throw new ConsultorioNoDisponibleException(tenantId.toString());
		}
		String correo = Usuario.normalizarEmail(email);
		String hash = passwordEncoder.encode(password);
		return TenantContext.ejecutarComo(tenantId, () -> tx.execute(estado -> {
			if (usuarios.existsByEmail(correo)) {
				throw new EmailDuplicadoException(correo);
			}
			return usuarios.save(Usuario.registrar(correo, nombre, rol, hash));
		}));
	}

}
