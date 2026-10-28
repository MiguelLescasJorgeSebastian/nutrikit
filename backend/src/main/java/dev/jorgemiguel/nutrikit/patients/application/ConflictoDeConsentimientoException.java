package dev.jorgemiguel.nutrikit.patients.application;

/**
 * Una operación choca con el estado del consentimiento o del aviso de privacidad. Se responde 409:
 * la petición es válida, pero el estado actual no la permite.
 */
public class ConflictoDeConsentimientoException extends RuntimeException {

	private final String titulo;

	ConflictoDeConsentimientoException(String titulo, String mensaje) {
		super(mensaje);
		this.titulo = titulo;
	}

	static ConflictoDeConsentimientoException requerido() {
		return new ConflictoDeConsentimientoException("Consentimiento requerido",
				"El paciente no tiene un consentimiento vigente para el tratamiento de sus datos de salud. "
						+ "Registra su aceptación del aviso de privacidad antes de guardar su historia clínica.");
	}

	static ConflictoDeConsentimientoException sinAviso() {
		return new ConflictoDeConsentimientoException("Sin aviso de privacidad",
				"El consultorio no ha publicado un aviso de privacidad; publícalo antes de registrar consentimientos.");
	}

	static ConflictoDeConsentimientoException yaVigente(String version) {
		return new ConflictoDeConsentimientoException("Consentimiento ya vigente",
				"El paciente ya aceptó la versión %s del aviso de privacidad.".formatted(version));
	}

	static ConflictoDeConsentimientoException nadaQueRetirar() {
		return new ConflictoDeConsentimientoException("Sin consentimiento vigente",
				"El paciente no tiene un consentimiento vigente que retirar.");
	}

	static ConflictoDeConsentimientoException versionDuplicada(String version) {
		return new ConflictoDeConsentimientoException("Versión de aviso duplicada",
				"Ya existe la versión %s del aviso de privacidad; una versión publicada no se modifica.".formatted(version));
	}

	public String getTitulo() {
		return titulo;
	}

}
