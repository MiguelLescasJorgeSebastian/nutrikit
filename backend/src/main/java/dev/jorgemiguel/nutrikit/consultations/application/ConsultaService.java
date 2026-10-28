package dev.jorgemiguel.nutrikit.consultations.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.consultations.ConsultaRegistrada;
import dev.jorgemiguel.nutrikit.consultations.domain.Consulta;
import dev.jorgemiguel.nutrikit.consultations.domain.ConsultaRepository;
import dev.jorgemiguel.nutrikit.consultations.domain.Indicador;
import dev.jorgemiguel.nutrikit.consultations.domain.IndicadorRepository;
import dev.jorgemiguel.nutrikit.consultations.domain.MedicionInvalidaException;
import dev.jorgemiguel.nutrikit.consultations.domain.OrigenMedicion;
import dev.jorgemiguel.nutrikit.patients.Pacientes;
import dev.jorgemiguel.nutrikit.shared.Origen;

/**
 * Consultas y sus mediciones. El paciente vive en otro módulo: aquí solo se guarda su id y se
 * pregunta a {@link Pacientes} si existe y si dio su consentimiento, sin tocar sus clases internas.
 */
@Service
@Transactional
public class ConsultaService {

	/** Línea de tiempo: de la más reciente a la más antigua; el id desempata. */
	static final Sort LINEA_DE_TIEMPO = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"));

	private final ConsultaRepository consultas;
	private final IndicadorRepository indicadores;
	private final Pacientes pacientes;
	private final ApplicationEventPublisher eventos;
	private final Clock clock;

	ConsultaService(ConsultaRepository consultas, IndicadorRepository indicadores, Pacientes pacientes,
			ApplicationEventPublisher eventos, Clock clock) {
		this.consultas = consultas;
		this.indicadores = indicadores;
		this.pacientes = pacientes;
		this.eventos = eventos;
		this.clock = clock;
	}

	/** Una medición pedida por código de indicador. */
	public record MedicionPedida(String indicador, BigDecimal valor) {
	}

	/**
	 * @throws ConsultaNoDisponibleException si el paciente no existe o está dado de baja (404)
	 * @throws ConflictoDeConsultaException si no tiene consentimiento vigente (409, ADR-0010)
	 * @throws MedicionInvalidaException si un indicador no existe, se repite o su valor está fuera de rango
	 */
	public Consulta registrar(UUID pacienteId, Instant fecha, String motivo, String notas, List<MedicionPedida> pedidas) {
		exigirPacienteActivo(pacienteId);
		if (!pacientes.tieneConsentimientoVigente(pacienteId)) {
			throw new ConflictoDeConsultaException("Consentimiento requerido",
					"El paciente no tiene un consentimiento vigente para el tratamiento de sus datos de salud.");
		}
		Origen origen = Origen.actual();
		Consulta consulta = Consulta.registrar(pacienteId, origen.actorId(), fecha, motivo, notas, clock.instant());
		Map<String, Indicador> porCodigo = indicadoresPorCodigo(pedidas);
		for (MedicionPedida pedida : pedidas) {
			Indicador indicador = porCodigo.get(pedida.indicador());
			if (indicador == null) {
				throw new MedicionInvalidaException("mediciones", "no existe el indicador '%s'".formatted(pedida.indicador()));
			}
			consulta.medir(indicador, pedida.valor(), OrigenMedicion.MANUAL);
		}
		consulta = consultas.save(consulta);
		eventos.publishEvent(new ConsultaRegistrada(consulta.getTenantId(), consulta.getId(), pacienteId, origen,
				clock.instant()));
		return consulta;
	}

	@Transactional(readOnly = true)
	public Consulta obtener(UUID consultaId) {
		return consultas.findById(consultaId).orElseThrow(() -> new ConsultaNoDisponibleException(
				"Consulta no encontrada", "No existe una consulta con id %s".formatted(consultaId)));
	}

	@Transactional(readOnly = true)
	public Page<Consulta> lineaDeTiempo(UUID pacienteId, int pagina, int tamano) {
		exigirPacienteActivo(pacienteId);
		Pageable pageable = PageRequest.of(pagina, tamano, LINEA_DE_TIEMPO);
		Page<Consulta> resultado = consultas.findByPacienteId(pacienteId, pageable);
		// Dentro de la transacción: con @BatchSize, una sola consulta SQL para todas las mediciones.
		resultado.forEach(consulta -> consulta.getMediciones().size());
		return resultado;
	}

	/** Indicadores (globales y propios) por id, para mostrar nombre y unidad de cada medición. */
	@Transactional(readOnly = true)
	public Map<UUID, Indicador> indicadoresDe(List<Consulta> lista) {
		var ids = lista.stream().flatMap(c -> c.getMediciones().stream()).map(m -> m.getIndicadorId()).distinct().toList();
		return ids.isEmpty() ? Map.of()
				: indicadores.findByIdIn(ids).stream().collect(Collectors.toMap(Indicador::getId, Function.identity()));
	}

	private Map<String, Indicador> indicadoresPorCodigo(List<MedicionPedida> pedidas) {
		var codigos = pedidas.stream().map(MedicionPedida::indicador).distinct().toList();
		return codigos.isEmpty() ? Map.of()
				: indicadores.findByCodigoIn(codigos).stream().collect(Collectors.toMap(Indicador::getCodigo, Function.identity()));
	}

	private void exigirPacienteActivo(UUID pacienteId) {
		if (!pacientes.estaActivo(pacienteId)) {
			throw new ConsultaNoDisponibleException("Paciente no encontrado",
					"No existe un paciente activo con id %s en este consultorio".formatted(pacienteId));
		}
	}

}
