package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

/** Fórmulas de gasto energético que un consultorio puede elegir. */
public enum FormulaGeb {

	MIFFLIN_ST_JEOR(new MifflinStJeor()),
	HARRIS_BENEDICT(new HarrisBenedictRevisada());

	private final FormulaGastoEnergetico formula;

	FormulaGeb(FormulaGastoEnergetico formula) {
		this.formula = formula;
	}

	public FormulaGastoEnergetico formula() {
		return formula;
	}

}
