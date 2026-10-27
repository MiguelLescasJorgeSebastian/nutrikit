package dev.jorgemiguel.nutrikit.iam.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/**
 * Usuarios del consultorio actual. El filtro de tenant y RLS limitan cada consulta al consultorio
 * de la petición, así que {@code findByEmail} no puede encontrar el usuario de otro consultorio.
 */
public interface UsuarioRepository extends Repository<Usuario, UUID> {

	Usuario save(Usuario usuario);

	Optional<Usuario> findById(UUID id);

	/** Recibe el correo ya normalizado con {@link Usuario#normalizarEmail}. */
	Optional<Usuario> findByEmail(String email);

	boolean existsByEmail(String email);

}
