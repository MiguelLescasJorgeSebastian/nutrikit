package dev.jorgemiguel.nutrikit.iam.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends Repository<RefreshToken, UUID> {

	RefreshToken save(RefreshToken token);

	/**
	 * Bloquea la fila ({@code SELECT ... FOR UPDATE}): si dos peticiones intentan rotar el mismo
	 * token a la vez, la segunda espera y lo encuentra ya revocado. Sin el bloqueo, ambas
	 * obtendrían un token nuevo.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/** Revoca todos los tokens vigentes del usuario; se usa al detectar la reutilización de uno. */
	@Modifying
	@Query("UPDATE RefreshToken t SET t.revocadoEn = :ahora WHERE t.usuarioId = :usuarioId AND t.revocadoEn IS NULL")
	int revocarTodosDelUsuario(@Param("usuarioId") UUID usuarioId, @Param("ahora") Instant ahora);

}
