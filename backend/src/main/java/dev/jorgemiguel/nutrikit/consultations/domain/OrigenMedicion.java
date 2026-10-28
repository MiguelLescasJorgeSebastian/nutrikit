package dev.jorgemiguel.nutrikit.consultations.domain;

/** De dónde salió un valor (RF-MED-03). */
public enum OrigenMedicion {

	/** Capturado por la nutrióloga. */
	MANUAL,

	/** Importado del CSV que exporta LookinBody (fase 2). */
	INBODY_CSV,

	/** Derivado de otras mediciones, como el IMC (#24). */
	CALCULADO

}
