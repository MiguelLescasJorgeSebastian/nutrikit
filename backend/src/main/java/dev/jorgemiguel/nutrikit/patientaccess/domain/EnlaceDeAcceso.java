package dev.jorgemiguel.nutrikit.patientaccess.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import dev.jorgemiguel.nutrikit.shared.FiltroDeTenant;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Enlace privado de un paciente: una credencial de solo lectura. Guarda el hash del token, nunca el
 * token; el PIN opcional, con Argon2.
 *
 * <p>{@code @DynamicUpdate}: solo se actualizan las columnas que cambian, las únicas que la app
 * tiene permiso de modificar (PIN, caducidad, revocación y último uso).
 */
@Entity
@Table(name = "enlace_acceso")
@Filter(name = FiltroDeTenant.NOMBRE)
@DynamicUpdate
public class EnlaceDeAcceso {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@Column(name = "paciente_id", nullable = false, updatable = false)
	private UUID pacienteId;

	@Column(name = "token_hash", nullable = false, updatable = false, length = 64)
	@JdbcTypeCode(SqlTypes.CHAR)
	private String tokenHash;

	@Column(name = "pin_hash", length = 100)
	private String pinHash;

	@Column(name = "expira_en")
	private Instant expiraEn;

	@Column(name = "revocado_en")
	private Instant revocadoEn;

	@Column(name = "ultimo_uso_en")
	private Instant ultimoUsoEn;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	/** Solo para Hibernate. */
	protected EnlaceDeAcceso() {
	}

	public static EnlaceDeAcceso emitir(UUID pacienteId, String tokenHash, String pinHash, Instant expiraEn, Instant ahora) {
		var enlace = new EnlaceDeAcceso();
		enlace.tenantId = TenantContext.requerido();
		enlace.pacienteId = pacienteId;
		enlace.tokenHash = tokenHash;
		enlace.creadoEn = ahora;
		enlace.configurar(pinHash, expiraEn, ahora);
		return enlace;
	}

	/** Reemplaza PIN y caducidad; {@code null} quita cada uno. */
	public void configurar(String pinHash, Instant expiraEn, Instant ahora) {
		if (expiraEn != null && !expiraEn.isAfter(ahora)) {
			throw new EnlaceInvalidoException("expiraEn", "debe ser una fecha futura");
		}
		this.pinHash = pinHash;
		this.expiraEn = expiraEn;
	}

	public void revocar(Instant ahora) {
		if (revocadoEn == null) {
			revocadoEn = ahora;
		}
	}

	public void registrarUso(Instant ahora) {
		ultimoUsoEn = ahora;
	}

	public boolean estaRevocado() {
		return revocadoEn != null;
	}

	public boolean expiro(Instant ahora) {
		return expiraEn != null && !ahora.isBefore(expiraEn);
	}

	public boolean tienePin() {
		return pinHash != null;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public UUID getPacienteId() {
		return pacienteId;
	}

	public String getPinHash() {
		return pinHash;
	}

	public Instant getExpiraEn() {
		return expiraEn;
	}

	public Instant getRevocadoEn() {
		return revocadoEn;
	}

	public Instant getUltimoUsoEn() {
		return ultimoUsoEn;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	/** Sin hashes. */
	@Override
	public String toString() {
		return "EnlaceDeAcceso[id=%s, pacienteId=%s, revocado=%s]".formatted(id, pacienteId, estaRevocado());
	}

}
