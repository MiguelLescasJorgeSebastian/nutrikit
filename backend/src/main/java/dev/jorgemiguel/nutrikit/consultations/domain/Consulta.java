package dev.jorgemiguel.nutrikit.consultations.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UuidGenerator;

import dev.jorgemiguel.nutrikit.encryption.CampoCifrado;
import dev.jorgemiguel.nutrikit.shared.FiltroDeTenant;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Consulta de un paciente: raíz del agregado que contiene sus mediciones. Las reglas que cruzan
 * mediciones (un indicador una sola vez por consulta) se validan aquí, en un solo lugar.
 *
 * <p>Referencias a otros módulos solo por id ({@code paciente_id}, {@code nutriologo_id}), sin FK.
 */
@Entity
@Table(name = "consulta")
@Filter(name = FiltroDeTenant.NOMBRE)
public class Consulta {

	/** Margen para relojes desfasados: una consulta no puede ser del futuro. */
	private static final Duration TOLERANCIA_FUTURO = Duration.ofMinutes(5);
	public static final int MAXIMO_MEDICIONES = 100;

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@Column(name = "paciente_id", nullable = false, updatable = false)
	private UUID pacienteId;

	@Column(name = "nutriologo_id", updatable = false)
	private UUID nutriologoId;

	@Column(nullable = false)
	private Instant fecha;

	@Column(length = 255)
	private String motivo;

	@Convert(converter = CampoCifrado.class)
	private String notas;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	/** En la línea de tiempo, las mediciones de toda la página se cargan en una consulta SQL, no una por consulta. */
	@OneToMany(mappedBy = "consulta", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	@BatchSize(size = 50)
	private List<Medicion> mediciones = new ArrayList<>();

	/** Solo para Hibernate. */
	protected Consulta() {
	}

	public static Consulta registrar(UUID pacienteId, UUID nutriologoId, Instant fecha, String motivo, String notas,
			Instant ahora) {
		var consulta = new Consulta();
		consulta.tenantId = TenantContext.requerido();
		consulta.pacienteId = pacienteId;
		consulta.nutriologoId = nutriologoId;
		consulta.fecha = fecha == null ? ahora : fecha;
		if (consulta.fecha.isAfter(ahora.plus(TOLERANCIA_FUTURO))) {
			throw new MedicionInvalidaException("fecha", "no puede ser futura");
		}
		consulta.motivo = opcional("motivo", motivo, 255);
		consulta.notas = opcional("notas", notas, 20_000);
		consulta.creadoEn = ahora;
		return consulta;
	}

	/**
	 * Agrega una medición validada contra su indicador.
	 *
	 * @throws MedicionInvalidaException si el indicador ya se midió en esta consulta o el valor
	 * está fuera de rango
	 */
	public Medicion medir(Indicador indicador, BigDecimal valor, OrigenMedicion origen) {
		if (indicador.isCalculado()) {
			throw new MedicionInvalidaException(indicador.getCodigo(), "se calcula automáticamente; no se captura a mano");
		}
		if (mediciones.stream().anyMatch(m -> m.getIndicadorId().equals(indicador.getId()))) {
			throw new MedicionInvalidaException(indicador.getCodigo(), "se repite en la consulta; cada indicador va una sola vez");
		}
		if (mediciones.size() >= MAXIMO_MEDICIONES) {
			throw new MedicionInvalidaException("mediciones", "admite máximo %d mediciones".formatted(MAXIMO_MEDICIONES));
		}
		var medicion = new Medicion(this, indicador.getId(), indicador.validar(valor), origen);
		mediciones.add(medicion);
		return medicion;
	}

	/**
	 * Corrige el valor de una medición capturada. Las calculadas no se corrigen: se recalculan.
	 *
	 * @throws MedicionInvalidaException si el indicador es calculado o el valor está fuera de rango
	 * @throws java.util.NoSuchElementException si la consulta no tiene esa medición
	 */
	public void corregir(Indicador indicador, BigDecimal valor) {
		if (indicador.isCalculado()) {
			throw new MedicionInvalidaException(indicador.getCodigo(), "se calcula automáticamente; no se corrige a mano");
		}
		Medicion medicion = medicionDe(indicador.getId()).orElseThrow();
		medicion.cambiarValor(indicador.validar(valor));
	}

	/**
	 * Guarda o actualiza un indicador derivado. No valida rango: se calcula de valores ya
	 * validados.
	 */
	public void registrarCalculado(Indicador indicador, BigDecimal valor) {
		if (!indicador.isCalculado()) {
			throw new IllegalArgumentException("Solo los indicadores calculados se registran así: " + indicador.getCodigo());
		}
		medicionDe(indicador.getId()).ifPresentOrElse(
				existente -> existente.cambiarValor(valor),
				() -> mediciones.add(new Medicion(this, indicador.getId(), valor, OrigenMedicion.CALCULADO)));
	}

	/** Valores capturados (no calculados), por id de indicador: la entrada de los cálculos. */
	public java.util.Map<UUID, BigDecimal> valoresCapturados() {
		return mediciones.stream().filter(m -> !m.esCalculada())
				.collect(java.util.stream.Collectors.toMap(Medicion::getIndicadorId, Medicion::getValor));
	}

	private java.util.Optional<Medicion> medicionDe(UUID indicadorId) {
		return mediciones.stream().filter(m -> m.getIndicadorId().equals(indicadorId)).findFirst();
	}

	private static String opcional(String campo, String valor, int maximo) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		if (valor.strip().length() > maximo) {
			throw new MedicionInvalidaException(campo, "admite máximo %d caracteres".formatted(maximo));
		}
		return valor.strip();
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

	public UUID getNutriologoId() {
		return nutriologoId;
	}

	public Instant getFecha() {
		return fecha;
	}

	public String getMotivo() {
		return motivo;
	}

	public String getNotas() {
		return notas;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public List<Medicion> getMediciones() {
		return Collections.unmodifiableList(mediciones);
	}

	/** Sin notas: pueden tener información clínica. */
	@Override
	public String toString() {
		return "Consulta[id=%s, pacienteId=%s]".formatted(id, pacienteId);
	}

}
