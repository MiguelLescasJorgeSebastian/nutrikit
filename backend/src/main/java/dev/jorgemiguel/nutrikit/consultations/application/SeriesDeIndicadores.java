package dev.jorgemiguel.nutrikit.consultations.application;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.consultations.domain.Indicador;
import dev.jorgemiguel.nutrikit.consultations.domain.IndicadorRepository;
import dev.jorgemiguel.nutrikit.consultations.domain.MedicionInvalidaException;
import dev.jorgemiguel.nutrikit.consultations.domain.OrigenMedicion;
import dev.jorgemiguel.nutrikit.patients.Pacientes;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Lado de lectura de consultas y mediciones (CQRS ligero): series para gráficas de evolución y
 * comparación entre dos consultas.
 *
 * <p>No carga entidades {@code Consulta}: una serie de 5 años son cientos de consultas con todas sus
 * mediciones, y aquí solo hacen falta fecha, código y valor. Una sola consulta SQL con proyección
 * a records trae exactamente esas columnas, sin N+1 ni descifrar notas que nadie va a leer.
 *
 * <p>SQL nativo no pasa por el filtro de Hibernate, así que filtra {@code tenant_id} de forma
 * explícita; RLS sigue aplicando como segunda barrera.
 */
@Service
@Transactional(readOnly = true)
public class SeriesDeIndicadores {

	public static final int MAXIMO_INDICADORES = 10;

	private final JdbcClient jdbc;
	private final IndicadorRepository indicadores;
	private final Pacientes pacientes;

	SeriesDeIndicadores(JdbcClient jdbc, IndicadorRepository indicadores, Pacientes pacientes) {
		this.jdbc = jdbc;
		this.indicadores = indicadores;
		this.pacientes = pacientes;
	}

	// --- respuesta ----------------------------------------------------------------------------

	public record Punto(Instant fecha, UUID consultaId, BigDecimal valor, OrigenMedicion origen) {
	}

	public record Serie(String indicador, String nombre, String unidad, boolean calculado, List<Punto> puntos) {
	}

	public record Diferencia(String indicador, String nombre, String unidad, BigDecimal valorAnterior,
			BigDecimal valorPosterior, BigDecimal diferencia, BigDecimal porcentaje) {
	}

	public record Comparacion(UUID pacienteId, UUID consultaAnterior, Instant fechaAnterior, UUID consultaPosterior,
			Instant fechaPosterior, List<Diferencia> indicadores) {
	}

	private record Fila(String codigo, String nombre, String unidad, boolean calculado, UUID consultaId,
			UUID pacienteId, Instant fecha, BigDecimal valor, String origen) {
	}

	// --- series -------------------------------------------------------------------------------

	/**
	 * Serie de cada indicador pedido, del punto más antiguo al más reciente.
	 *
	 * @param codigos indicadores a graficar; vacío = todos los que tengan datos
	 * @param desde inclusive; nulo = sin límite
	 * @param hasta inclusive; nulo = sin límite
	 */
	public List<Serie> series(UUID pacienteId, List<String> codigos, LocalDate desde, LocalDate hasta) {
		exigirPacienteActivo(pacienteId);
		if (desde != null && hasta != null && desde.isAfter(hasta)) {
			throw new MedicionInvalidaException("desde", "no puede ser posterior a hasta");
		}
		Set<String> pedidos = new TreeSet<>(codigos == null ? List.of() : codigos);
		if (pedidos.size() > MAXIMO_INDICADORES) {
			throw new MedicionInvalidaException("indicadores", "máximo %d por petición".formatted(MAXIMO_INDICADORES));
		}
		Map<String, Indicador> conocidos = new TreeMap<>();
		if (!pedidos.isEmpty()) {
			indicadores.findByCodigoIn(pedidos).forEach(i -> conocidos.put(i.getCodigo(), i));
			pedidos.stream().filter(codigo -> !conocidos.containsKey(codigo)).findFirst().ifPresent(codigo -> {
				throw new MedicionInvalidaException("indicadores", "no existe el indicador '%s'".formatted(codigo));
			});
		}

		List<Fila> filas = jdbc.sql("""
				SELECT i.codigo, i.nombre, i.unidad, i.calculado, c.id AS consulta_id, c.paciente_id, c.fecha,
				       m.valor, m.origen
				FROM consulta c
				JOIN medicion m ON m.consulta_id = c.id
				JOIN indicador i ON i.id = m.indicador_id
				WHERE c.tenant_id = :tenant AND c.paciente_id = :paciente
				  AND c.fecha >= :desde AND c.fecha < :hasta
				  AND (cardinality(CAST(:codigos AS text[])) = 0 OR i.codigo = ANY (CAST(:codigos AS text[])))
				ORDER BY i.codigo, c.fecha, c.id
				""")
				.param("tenant", TenantContext.requerido())
				.param("paciente", pacienteId)
				.param("desde", java.sql.Timestamp.from(inicio(desde)))
				.param("hasta", java.sql.Timestamp.from(fin(hasta)))
				.param("codigos", pedidos.toArray(String[]::new))
				.query(Fila.class)
				.list();

		Map<String, Serie> porCodigo = new LinkedHashMap<>();
		conocidos.values().forEach(i -> porCodigo.put(i.getCodigo(),
				new Serie(i.getCodigo(), i.getNombre(), i.getUnidad(), i.isCalculado(), new ArrayList<>())));
		for (Fila fila : filas) {
			porCodigo.computeIfAbsent(fila.codigo(),
					codigo -> new Serie(codigo, fila.nombre(), fila.unidad(), fila.calculado(), new ArrayList<>()))
					.puntos().add(new Punto(fila.fecha(), fila.consultaId(), Indicador.sinCerosSobrantes(fila.valor()),
							OrigenMedicion.valueOf(fila.origen())));
		}
		return porCodigo.values().stream()
				.sorted(Comparator.comparing(Serie::indicador))
				.map(s -> new Serie(s.indicador(), s.nombre(), s.unidad(), s.calculado(), List.copyOf(s.puntos())))
				.toList();
	}

	// --- comparación --------------------------------------------------------------------------

	/**
	 * Diferencia por indicador entre dos consultas del mismo paciente. Sin importar el orden en que
	 * se pidan, se compara la más reciente contra la más antigua: diferencia = posterior − anterior.
	 */
	public Comparacion comparar(UUID unaConsulta, UUID otraConsulta) {
		if (unaConsulta.equals(otraConsulta)) {
			throw new MedicionInvalidaException("consultas", "elige dos consultas distintas");
		}
		List<Fila> filas = jdbc.sql("""
				SELECT i.codigo, i.nombre, i.unidad, i.calculado, c.id AS consulta_id, c.paciente_id, c.fecha,
				       m.valor, m.origen
				FROM consulta c
				LEFT JOIN medicion m ON m.consulta_id = c.id
				LEFT JOIN indicador i ON i.id = m.indicador_id
				WHERE c.tenant_id = :tenant AND c.id IN (:a, :b)
				ORDER BY c.fecha, c.id, i.codigo
				""")
				.param("tenant", TenantContext.requerido())
				.param("a", unaConsulta)
				.param("b", otraConsulta)
				.query(Fila.class)
				.list();

		Map<UUID, List<Fila>> porConsulta = new LinkedHashMap<>();
		filas.forEach(f -> porConsulta.computeIfAbsent(f.consultaId(), id -> new ArrayList<>()).add(f));
		if (porConsulta.size() < 2) {
			throw new ConsultaNoDisponibleException("Consulta no encontrada",
					"Alguna de las dos consultas no existe en este consultorio");
		}
		List<List<Fila>> ordenadas = new ArrayList<>(porConsulta.values());
		Fila anterior = ordenadas.get(0).getFirst();
		Fila posterior = ordenadas.get(1).getFirst();
		if (!anterior.pacienteId().equals(posterior.pacienteId())) {
			throw new MedicionInvalidaException("consultas", "las dos consultas deben ser del mismo paciente");
		}
		exigirPacienteActivo(anterior.pacienteId());

		Map<String, Fila> antes = porCodigo(ordenadas.get(0));
		Map<String, Fila> despues = porCodigo(ordenadas.get(1));
		Set<String> codigos = new TreeSet<>(antes.keySet());
		codigos.addAll(despues.keySet());
		List<Diferencia> diferencias = codigos.stream().map(codigo -> {
			Fila referencia = antes.getOrDefault(codigo, despues.get(codigo));
			BigDecimal valorAntes = valor(antes.get(codigo));
			BigDecimal valorDespues = valor(despues.get(codigo));
			return new Diferencia(codigo, referencia.nombre(), referencia.unidad(), valorAntes, valorDespues,
					diferencia(valorAntes, valorDespues), porcentaje(valorAntes, valorDespues));
		}).toList();

		return new Comparacion(anterior.pacienteId(), anterior.consultaId(), anterior.fecha(), posterior.consultaId(),
				posterior.fecha(), diferencias);
	}

	/** Absoluta, con la escala del valor más preciso. Nula si falta alguno de los dos. */
	static BigDecimal diferencia(BigDecimal antes, BigDecimal despues) {
		return antes == null || despues == null ? null : Indicador.sinCerosSobrantes(despues.subtract(antes));
	}

	/** Respecto del valor anterior, con un decimal. Nula si falta alguno o el anterior es cero. */
	static BigDecimal porcentaje(BigDecimal antes, BigDecimal despues) {
		if (antes == null || despues == null || antes.signum() == 0) {
			return null;
		}
		return despues.subtract(antes).multiply(BigDecimal.valueOf(100))
				.divide(antes, MathContext.DECIMAL64).setScale(1, RoundingMode.HALF_UP);
	}

	private static Map<String, Fila> porCodigo(List<Fila> filas) {
		Map<String, Fila> mapa = new TreeMap<>();
		filas.stream().filter(f -> f.codigo() != null).forEach(f -> mapa.put(f.codigo(), f));
		return mapa;
	}

	private static BigDecimal valor(Fila fila) {
		return fila == null ? null : Indicador.sinCerosSobrantes(fila.valor());
	}

	private static Instant inicio(LocalDate desde) {
		return desde == null ? Instant.parse("1900-01-01T00:00:00Z") : desde.atStartOfDay(ZoneOffset.UTC).toInstant();
	}

	/** {@code hasta} es inclusive: el límite es el inicio del día siguiente. */
	private static Instant fin(LocalDate hasta) {
		return hasta == null ? Instant.parse("3000-01-01T00:00:00Z")
				: hasta.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
	}

	private void exigirPacienteActivo(UUID pacienteId) {
		if (!pacientes.estaActivo(pacienteId)) {
			throw new ConsultaNoDisponibleException("Paciente no encontrado",
					"No existe un paciente activo con id %s en este consultorio".formatted(pacienteId));
		}
	}

}
