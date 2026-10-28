package dev.jorgemiguel.nutrikit.consultations.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UuidGenerator;

import dev.jorgemiguel.nutrikit.shared.FiltroDeTenant;

/**
 * Un valor de un indicador en una consulta. Es parte del agregado {@link Consulta}: se crea con
 * ella y no existe sin ella. Referencia al indicador solo por id (otro agregado).
 */
@Entity
@Table(name = "medicion")
@Filter(name = FiltroDeTenant.NOMBRE)
public class Medicion {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "consulta_id", nullable = false, updatable = false)
	private Consulta consulta;

	@Column(name = "indicador_id", nullable = false, updatable = false)
	private UUID indicadorId;

	/** {@code numeric(10,3)}: nunca {@code double}, que no representa exactamente 72.3. */
	@Column(nullable = false, precision = 10, scale = 3)
	private BigDecimal valor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private OrigenMedicion origen;

	@Column(name = "importacion_id")
	private UUID importacionId;

	/** Solo para Hibernate. */
	protected Medicion() {
	}

	Medicion(Consulta consulta, UUID indicadorId, BigDecimal valor, OrigenMedicion origen) {
		this.consulta = consulta;
		this.tenantId = consulta.getTenantId();
		this.indicadorId = indicadorId;
		this.valor = valor;
		this.origen = origen;
	}

	public UUID getId() {
		return id;
	}

	public UUID getIndicadorId() {
		return indicadorId;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public OrigenMedicion getOrigen() {
		return origen;
	}

	public UUID getImportacionId() {
		return importacionId;
	}

}
