package dev.jorgemiguel.nutrikit.consultations.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.jorgemiguel.nutrikit.consultations.domain.ConfiguracionDeCalculos;
import dev.jorgemiguel.nutrikit.consultations.domain.ConfiguracionDeCalculosRepository;
import dev.jorgemiguel.nutrikit.consultations.domain.Consulta;
import dev.jorgemiguel.nutrikit.consultations.domain.Indicador;
import dev.jorgemiguel.nutrikit.consultations.domain.IndicadorRepository;
import dev.jorgemiguel.nutrikit.consultations.domain.calculos.CalculadoraDeIndicadores;
import dev.jorgemiguel.nutrikit.consultations.domain.calculos.Entradas;
import dev.jorgemiguel.nutrikit.consultations.domain.calculos.Sexo;
import dev.jorgemiguel.nutrikit.patients.Pacientes;
import dev.jorgemiguel.nutrikit.patients.PerfilClinico;

/**
 * Reúne las entradas de una consulta (sus mediciones capturadas, la edad del paciente ese día y su
 * sexo), las pasa a la calculadora del dominio y guarda los resultados como mediciones calculadas.
 *
 * <p>Las fórmulas viven en {@code domain.calculos}, sin Spring; aquí solo se buscan los datos.
 * Se llama dentro de la transacción de quien guarda o corrige la consulta.
 */
@Component
class DerivadosDeConsulta {

	private final IndicadorRepository indicadores;
	private final ConfiguracionDeCalculosRepository configuraciones;
	private final Pacientes pacientes;

	DerivadosDeConsulta(IndicadorRepository indicadores, ConfiguracionDeCalculosRepository configuraciones,
			Pacientes pacientes) {
		this.indicadores = indicadores;
		this.configuraciones = configuraciones;
		this.pacientes = pacientes;
	}

	void recalcular(Consulta consulta) {
		Map<UUID, BigDecimal> capturados = consulta.valoresCapturados();
		if (capturados.isEmpty()) {
			return;
		}
		Map<String, BigDecimal> porCodigo = indicadores.findByIdIn(capturados.keySet()).stream()
				.collect(Collectors.toMap(Indicador::getCodigo, indicador -> capturados.get(indicador.getId())));
		PerfilClinico perfil = pacientes.perfilClinico(consulta.getPacienteId()).orElse(null);
		Integer edad = perfil == null ? null : perfil.edadEn(LocalDate.ofInstant(consulta.getFecha(), ZoneOffset.UTC));
		Sexo sexo = perfil == null ? null : Sexo.valueOf(perfil.sexoBiologico());
		var formula = configuraciones.findById(consulta.getTenantId())
				.map(ConfiguracionDeCalculos::getFormulaGeb)
				.orElse(ConfiguracionDeCalculos.FORMULA_POR_DEFECTO);

		Map<String, BigDecimal> resultados = CalculadoraDeIndicadores.con(formula).calcular(new Entradas(porCodigo, edad, sexo));
		if (resultados.isEmpty()) {
			return;
		}
		Map<String, Indicador> calculados = indicadores.findByCodigoIn(resultados.keySet()).stream()
				.filter(Indicador::isCalculado)
				.collect(Collectors.toMap(Indicador::getCodigo, Function.identity()));
		resultados.forEach((codigo, valor) -> {
			Indicador indicador = calculados.get(codigo);
			if (indicador != null) {
				consulta.registrarCalculado(indicador, valor);
			}
		});
	}

}
