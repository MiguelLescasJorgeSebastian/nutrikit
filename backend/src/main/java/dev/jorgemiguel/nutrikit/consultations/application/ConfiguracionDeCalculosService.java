package dev.jorgemiguel.nutrikit.consultations.application;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.consultations.domain.ConfiguracionDeCalculos;
import dev.jorgemiguel.nutrikit.consultations.domain.ConfiguracionDeCalculosRepository;
import dev.jorgemiguel.nutrikit.consultations.domain.calculos.FormulaGeb;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/** Fórmula de gasto energético del consultorio. Aplica a las consultas que se guarden o corrijan después. */
@Service
@Transactional
public class ConfiguracionDeCalculosService {

	private final ConfiguracionDeCalculosRepository configuraciones;
	private final Clock clock;

	ConfiguracionDeCalculosService(ConfiguracionDeCalculosRepository configuraciones, Clock clock) {
		this.configuraciones = configuraciones;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public FormulaGeb formulaGeb() {
		return configuraciones.findById(TenantContext.requerido())
				.map(ConfiguracionDeCalculos::getFormulaGeb)
				.orElse(ConfiguracionDeCalculos.FORMULA_POR_DEFECTO);
	}

	public FormulaGeb elegir(FormulaGeb formula) {
		var configuracion = configuraciones.findById(TenantContext.requerido()).orElse(null);
		if (configuracion == null) {
			configuraciones.save(ConfiguracionDeCalculos.delConsultorioActual(formula, clock.instant()));
		}
		else {
			configuracion.elegir(formula, clock.instant());
		}
		return formula;
	}

}
