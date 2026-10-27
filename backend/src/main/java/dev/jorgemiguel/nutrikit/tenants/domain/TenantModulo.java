package dev.jorgemiguel.nutrikit.tenants.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;

import dev.jorgemiguel.nutrikit.shared.FiltroDeTenant;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Un módulo del sistema (pacientes, consultas, agenda...) y si está encendido para el consultorio.
 *
 * <p>Datos de consultorio: lleva el filtro de tenant y la tabla tiene Row-Level Security.
 */
@Entity
@Table(name = "tenant_modulo")
@IdClass(TenantModulo.Llave.class)
@Filter(name = FiltroDeTenant.NOMBRE)
public class TenantModulo {

	@Id
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@Id
	@Column(nullable = false, updatable = false, length = 40)
	private String modulo;

	@Column(nullable = false)
	private boolean habilitado;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	/** Solo para Hibernate. */
	protected TenantModulo() {
	}

	private TenantModulo(UUID tenantId, String modulo, boolean habilitado) {
		this.tenantId = tenantId;
		this.modulo = modulo;
		this.habilitado = habilitado;
		this.actualizadoEn = Instant.now();
	}

	/**
	 * Registra un módulo para el consultorio actual. El {@code tenant_id} sale siempre del
	 * contexto, nunca de la entrada del usuario.
	 */
	public static TenantModulo registrar(String modulo, boolean habilitado) {
		if (modulo == null || modulo.isBlank() || modulo.length() > 40) {
			throw new IllegalArgumentException("El nombre del módulo es obligatorio y admite máximo 40 caracteres");
		}
		return new TenantModulo(TenantContext.requerido(), modulo, habilitado);
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public String getModulo() {
		return modulo;
	}

	public boolean isHabilitado() {
		return habilitado;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}

	/** Llave compuesta: un consultorio tiene cada módulo una sola vez. */
	public record Llave(UUID tenantId, String modulo) implements Serializable {
	}

}
