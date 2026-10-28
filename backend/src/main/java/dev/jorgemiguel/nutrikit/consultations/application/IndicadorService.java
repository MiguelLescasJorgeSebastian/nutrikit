package dev.jorgemiguel.nutrikit.consultations.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.consultations.IndicadorCreado;
import dev.jorgemiguel.nutrikit.consultations.domain.Indicador;
import dev.jorgemiguel.nutrikit.consultations.domain.IndicadorRepository;
import dev.jorgemiguel.nutrikit.shared.Origen;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/** Catálogo de indicadores del consultorio: los globales más los que agrega la nutrióloga. */
@Service
@Transactional
public class IndicadorService {

	private final IndicadorRepository indicadores;
	private final ApplicationEventPublisher eventos;
	private final Clock clock;

	IndicadorService(IndicadorRepository indicadores, ApplicationEventPublisher eventos, Clock clock) {
		this.indicadores = indicadores;
		this.eventos = eventos;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Indicador> catalogo() {
		return indicadores.findAllVisibles();
	}

	/**
	 * Agrega un indicador propio. Es una fila nueva, sin migración. Su código no puede repetir uno
	 * global ni uno propio: el código identifica al indicador al capturar mediciones.
	 */
	public Indicador crear(String codigo, String nombre, String unidad, BigDecimal minimo, BigDecimal maximo) {
		Indicador indicador = Indicador.propio(codigo, nombre, unidad, minimo, maximo);
		if (indicadores.existsByCodigo(indicador.getCodigo())) {
			throw new ConflictoDeConsultaException("Indicador duplicado",
					"Ya existe un indicador con el código '%s' (global o de tu consultorio)".formatted(codigo));
		}
		indicador = indicadores.save(indicador);
		eventos.publishEvent(new IndicadorCreado(TenantContext.requerido(), indicador.getId(), indicador.getCodigo(),
				Origen.actual(), clock.instant()));
		return indicador;
	}

}
