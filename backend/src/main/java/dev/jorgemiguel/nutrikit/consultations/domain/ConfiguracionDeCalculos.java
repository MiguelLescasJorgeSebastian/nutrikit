package dev.jorgemiguel.nutrikit.consultations.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;

import dev.jorgemiguel.nutrikit.consultations.domain.calculos.FormulaGeb;
import dev.jorgemiguel.nutrikit.shared.FiltroDeTenant;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/** Preferencias de cálculo del consultorio: qué fórmula de gasto energético usa. */
@Entity
@Table(name = "configuracion_de_calculos")
@Filter(name = FiltroDeTenant.NOMBRE)
public class ConfiguracionDeCalculos {

	/** Sin configuración, la recomendada para población actual. */
	public static final FormulaGeb FORMULA_POR_DEFECTO = FormulaGeb.MIFFLIN_ST_JEOR;

	@Id
	@Column(name = "tenant_id")
	private UUID tenantId;

	@Enumerated(EnumType.STRING)
	@Column(name = "formula_geb", nullable = false, length = 20)
	private FormulaGeb formulaGeb;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	/** Solo para Hibernate. */
	protected ConfiguracionDeCalculos() {
	}

	public static ConfiguracionDeCalculos delConsultorioActual(FormulaGeb formula, Instant ahora) {
		var configuracion = new ConfiguracionDeCalculos();
		configuracion.tenantId = TenantContext.requerido();
		configuracion.elegir(formula, ahora);
		return configuracion;
	}

	public void elegir(FormulaGeb formula, Instant ahora) {
		if (formula == null) {
			throw new MedicionInvalidaException("formula", "es obligatoria: MIFFLIN_ST_JEOR o HARRIS_BENEDICT");
		}
		this.formulaGeb = formula;
		this.actualizadoEn = ahora;
	}

	public FormulaGeb getFormulaGeb() {
		return formulaGeb;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}

}
