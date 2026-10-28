package dev.jorgemiguel.nutrikit.patients.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UuidGenerator;

import dev.jorgemiguel.nutrikit.encryption.CampoCifrado;
import dev.jorgemiguel.nutrikit.encryption.MapaCifrado;
import dev.jorgemiguel.nutrikit.shared.FiltroDeTenant;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Historia clínica de un paciente (RF-PAC-03): una por paciente. Los campos clínicos se cifran con
 * la llave del consultorio ({@link CampoCifrado}); en la base son bytes ilegibles sin la llave.
 */
@Entity
@Table(name = "historia_clinica")
@Filter(name = FiltroDeTenant.NOMBRE)
public class HistoriaClinica {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@Column(name = "paciente_id", nullable = false, updatable = false)
	private UUID pacienteId;

	@Convert(converter = CampoCifrado.class)
	private String antecedentes;

	@Convert(converter = CampoCifrado.class)
	private String padecimientos;

	@Convert(converter = CampoCifrado.class)
	private String medicamentos;

	@Convert(converter = CampoCifrado.class)
	private String alergias;

	@Convert(converter = MapaCifrado.class)
	@Column(name = "campos_extra")
	private Map<String, String> camposExtra;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	@Version
	@Column(nullable = false)
	private long version;

	/** Solo para Hibernate. */
	protected HistoriaClinica() {
	}

	private HistoriaClinica(UUID tenantId, UUID pacienteId) {
		this.tenantId = tenantId;
		this.pacienteId = pacienteId;
	}

	public static HistoriaClinica de(Paciente paciente) {
		if (!paciente.getTenantId().equals(TenantContext.requerido())) {
			throw new IllegalStateException("El paciente no pertenece al consultorio actual");
		}
		return new HistoriaClinica(paciente.getTenantId(), paciente.getId());
	}

	/** Reemplaza el contenido completo. Los mapas se copian: nunca se modifican en su lugar. */
	public void registrar(ContenidoClinico contenido, Instant ahora) {
		this.antecedentes = contenido.antecedentes();
		this.padecimientos = contenido.padecimientos();
		this.medicamentos = contenido.medicamentos();
		this.alergias = contenido.alergias();
		this.camposExtra = Map.copyOf(contenido.camposExtra());
		this.actualizadoEn = ahora;
	}

	public ContenidoClinico contenido() {
		return new ContenidoClinico(antecedentes, padecimientos, medicamentos, alergias,
				camposExtra == null ? Map.of() : camposExtra);
	}

	public UUID getId() {
		return id;
	}

	public UUID getPacienteId() {
		return pacienteId;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}

	public long getVersion() {
		return version;
	}

	/** Nunca imprime el contenido clínico. */
	@Override
	public String toString() {
		return "HistoriaClinica[id=%s, pacienteId=%s]".formatted(id, pacienteId);
	}

}
