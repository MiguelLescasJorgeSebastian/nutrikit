package dev.jorgemiguel.nutrikit.patients.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import dev.jorgemiguel.nutrikit.shared.FiltroDeTenant;

/**
 * Un hecho en el historial de consentimiento de un paciente: lo otorgó o lo retiró. Nunca se
 * modifica ni se borra; el estado actual es el último hecho.
 */
@Entity
@Immutable
@Table(name = "consentimiento")
@Filter(name = FiltroDeTenant.NOMBRE)
public class Consentimiento {

	public enum Accion {
		OTORGADO, RETIRADO
	}

	public enum Medio {
		/** Firmado o aceptado en el consultorio. */
		PRESENCIAL,
		/** Aceptado por el paciente en línea. */
		EN_LINEA
	}

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "paciente_id", nullable = false)
	private UUID pacienteId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Accion accion;

	@Column(name = "version_aviso", nullable = false, length = 20)
	private String versionAviso;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Medio medio;

	@Column(name = "registrado_en", nullable = false)
	private Instant registradoEn;

	@Column(name = "registrado_por")
	private UUID registradoPor;

	/** Solo para Hibernate. */
	protected Consentimiento() {
	}

	private Consentimiento(Paciente paciente, Accion accion, String versionAviso, Medio medio, Instant ahora,
			UUID registradoPor) {
		if (medio == null) {
			throw new DatoInvalidoException("medio", "es obligatorio");
		}
		this.tenantId = paciente.getTenantId();
		this.pacienteId = paciente.getId();
		this.accion = accion;
		this.versionAviso = versionAviso;
		this.medio = medio;
		this.registradoEn = ahora;
		this.registradoPor = registradoPor;
	}

	public static Consentimiento otorgar(Paciente paciente, AvisoDePrivacidad aviso, Medio medio, Instant ahora,
			UUID registradoPor) {
		return new Consentimiento(paciente, Accion.OTORGADO, aviso.getVersion(), medio, ahora, registradoPor);
	}

	/** Registra el retiro sobre la misma versión que se había aceptado. */
	public static Consentimiento retirar(Paciente paciente, Consentimiento vigente, Medio medio, Instant ahora,
			UUID registradoPor) {
		if (!vigente.estaOtorgado()) {
			throw new IllegalArgumentException("Solo se retira un consentimiento otorgado");
		}
		return new Consentimiento(paciente, Accion.RETIRADO, vigente.versionAviso, medio, ahora, registradoPor);
	}

	public boolean estaOtorgado() {
		return accion == Accion.OTORGADO;
	}

	public UUID getId() {
		return id;
	}

	public UUID getPacienteId() {
		return pacienteId;
	}

	public Accion getAccion() {
		return accion;
	}

	public String getVersionAviso() {
		return versionAviso;
	}

	public Medio getMedio() {
		return medio;
	}

	public Instant getRegistradoEn() {
		return registradoEn;
	}

	public UUID getRegistradoPor() {
		return registradoPor;
	}

}
