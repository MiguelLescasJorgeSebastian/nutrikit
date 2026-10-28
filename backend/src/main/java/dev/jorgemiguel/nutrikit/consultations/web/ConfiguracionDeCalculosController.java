package dev.jorgemiguel.nutrikit.consultations.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jorgemiguel.nutrikit.consultations.application.ConfiguracionDeCalculosService;
import dev.jorgemiguel.nutrikit.consultations.domain.calculos.FormulaGeb;

/** Fórmula de gasto energético basal del consultorio. Cambiarla es decisión de la titular. */
@Tag(name = "Consultas")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/configuracion/gasto-energetico")
class ConfiguracionDeCalculosController {

	private final ConfiguracionDeCalculosService service;

	ConfiguracionDeCalculosController(ConfiguracionDeCalculosService service) {
		this.service = service;
	}

	@Operation(summary = "Fórmula de gasto energético del consultorio", description = "Por defecto, Mifflin-St Jeor.")
	@PreAuthorize("hasAnyRole('TITULAR', 'COLABORADOR')")
	@GetMapping
	Formula actual() {
		return new Formula(service.formulaGeb());
	}

	@Operation(summary = "Elegir la fórmula de gasto energético",
			description = "Aplica a las consultas que se registren o corrijan desde ahora; las anteriores no cambian.")
	@PreAuthorize("hasRole('TITULAR')")
	@PutMapping
	Formula elegir(@RequestBody Formula formula) {
		return new Formula(service.elegir(formula.formula()));
	}

	@Schema(name = "FormulaDeGastoEnergetico")
	record Formula(@Schema(example = "MIFFLIN_ST_JEOR") FormulaGeb formula) {
	}

}
