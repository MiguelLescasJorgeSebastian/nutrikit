package dev.jorgemiguel.nutrikit.iam.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import dev.jorgemiguel.nutrikit.shared.FiltroDeTenant;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Refresh token rotativo: sirve una sola vez. Al usarlo se revoca y se emite otro. Solo se guarda
 * su SHA-256; si alguien lee la tabla, no obtiene tokens utilizables.
 */
@Entity
@Table(name = "refresh_token")
@Filter(name = FiltroDeTenant.NOMBRE)
public class RefreshToken {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private UUID usuarioId;

	/** {@code char(64)}: el SHA-256 en hexadecimal siempre mide 64. */
	@Column(name = "token_hash", nullable = false, updatable = false, length = 64)
	@JdbcTypeCode(SqlTypes.CHAR)
	private String tokenHash;

	@Column(name = "expira_en", nullable = false, updatable = false)
	private Instant expiraEn;

	@Column(name = "revocado_en")
	private Instant revocadoEn;

	/** Solo para Hibernate. */
	protected RefreshToken() {
	}

	private RefreshToken(UUID tenantId, UUID usuarioId, String tokenHash, Instant expiraEn) {
		this.tenantId = tenantId;
		this.usuarioId = usuarioId;
		this.tokenHash = tokenHash;
		this.expiraEn = expiraEn;
	}

	public static RefreshToken emitir(Usuario usuario, String tokenHash, Instant expiraEn) {
		if (!usuario.getTenantId().equals(TenantContext.requerido())) {
			throw new IllegalStateException("El usuario no pertenece al consultorio actual");
		}
		return new RefreshToken(usuario.getTenantId(), usuario.getId(), tokenHash, expiraEn);
	}

	public boolean fueRevocado() {
		return revocadoEn != null;
	}

	public boolean expiro(Instant ahora) {
		return !ahora.isBefore(expiraEn);
	}

	public void revocar(Instant ahora) {
		if (revocadoEn == null) {
			revocadoEn = ahora;
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public UUID getUsuarioId() {
		return usuarioId;
	}

	public Instant getExpiraEn() {
		return expiraEn;
	}

	public Instant getRevocadoEn() {
		return revocadoEn;
	}

	@Override
	public String toString() {
		return "RefreshToken[id=%s, usuarioId=%s, revocado=%s]".formatted(id, usuarioId, fueRevocado());
	}

}
