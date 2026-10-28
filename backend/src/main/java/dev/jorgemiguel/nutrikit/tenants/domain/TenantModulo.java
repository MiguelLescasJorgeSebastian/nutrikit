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
import dev.jorgemiguel.nutrikit.tenants.ModuloDelConsultorio;

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
	 * contexto, nunca de la entrada del usuario. Un módulo de núcleo siempre queda encendido.
	 */
	public static TenantModulo registrar(ModuloDelConsultorio modulo, boolean habilitado) {
		return new TenantModulo(TenantContext.requerido(), modulo.codigo(), modulo.nucleo() || habilitado);
	}

	/**
	 * @return si el estado cambió
	 * @throws ModuloDeNucleoException si se intenta apagar un módulo de núcleo
	 */
	public boolean cambiar(boolean encender) {
		ModuloDelConsultorio catalogo = getCatalogo();
		if (!encender && catalogo.nucleo()) {
			throw new ModuloDeNucleoException(catalogo);
		}
		if (habilitado == encender) {
			return false;
		}
		habilitado = encender;
		actualizadoEn = Instant.now();
		return true;
	}

	public ModuloDelConsultorio getCatalogo() {
		return ModuloDelConsultorio.deCodigo(modulo)
				.orElseThrow(() -> new IllegalStateException("Módulo fuera del catálogo: " + modulo));
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
