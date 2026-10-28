package dev.jorgemiguel.nutrikit.consultations.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.consultations.ConsultasDelPaciente;
import dev.jorgemiguel.nutrikit.consultations.domain.Indicador;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/** Lecturas para otros módulos, con la misma estrategia que {@link SeriesDeIndicadores}: SQL y sin entidades. */
@Component
@Transactional(readOnly = true)
class ConsultasDelPacienteAdapter implements ConsultasDelPaciente {

	private final JdbcClient jdbc;
	private final SeriesDeIndicadores series;

	ConsultasDelPacienteAdapter(JdbcClient jdbc, SeriesDeIndicadores series) {
		this.jdbc = jdbc;
		this.series = series;
	}

	private record Fila(Instant fecha, String codigo, String nombre, String unidad, BigDecimal valor) {
	}

	@Override
	public Optional<ResumenDeConsulta> ultima(UUID pacienteId) {
		List<Fila> filas = jdbc.sql("""
				WITH ultima AS (
				    SELECT id, fecha FROM consulta
				    WHERE tenant_id = :tenant AND paciente_id = :paciente
				    ORDER BY fecha DESC, id DESC
				    LIMIT 1
				)
				SELECT u.fecha, i.codigo, i.nombre, i.unidad, m.valor
				FROM ultima u
				LEFT JOIN medicion m ON m.consulta_id = u.id
				LEFT JOIN indicador i ON i.id = m.indicador_id
				ORDER BY i.calculado, i.codigo
				""")
				.param("tenant", TenantContext.requerido())
				.param("paciente", pacienteId)
				.query(Fila.class)
				.list();
		if (filas.isEmpty()) {
			return Optional.empty();
		}
		List<ValorMedido> mediciones = filas.stream().filter(f -> f.codigo() != null)
				.map(f -> new ValorMedido(f.codigo(), f.nombre(), f.unidad(), Indicador.sinCerosSobrantes(f.valor())))
				.toList();
		return Optional.of(new ResumenDeConsulta(filas.getFirst().fecha(), mediciones));
	}

	@Override
	public SerieDeEvolucion serie(UUID pacienteId, String indicador) {
		var serie = series.series(pacienteId, List.of(indicador), null, null).getFirst();
		return new SerieDeEvolucion(serie.indicador(), serie.nombre(), serie.unidad(),
				serie.puntos().stream().map(p -> new PuntoDeEvolucion(p.fecha(), p.valor())).toList());
	}

}
