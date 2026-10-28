package dev.jorgemiguel.nutrikit.consultations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública de lectura para otros módulos (por ejemplo, la página del paciente): su última
 * consulta y la evolución de un indicador, sin notas ni datos internos del módulo.
 */
public interface ConsultasDelPaciente {

	/** La consulta más reciente con sus mediciones; vacío si no tiene. */
	Optional<ResumenDeConsulta> ultima(UUID pacienteId);

	/** Evolución de un indicador, del punto más antiguo al más reciente. */
	SerieDeEvolucion serie(UUID pacienteId, String indicador);

	record ValorMedido(String indicador, String nombre, String unidad, BigDecimal valor) {
	}

	record ResumenDeConsulta(Instant fecha, List<ValorMedido> mediciones) {
	}

	record PuntoDeEvolucion(Instant fecha, BigDecimal valor) {
	}

	record SerieDeEvolucion(String indicador, String nombre, String unidad, List<PuntoDeEvolucion> puntos) {
	}

}
