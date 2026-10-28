package dev.jorgemiguel.nutrikit.patients.application;

/**
 * Alguien más editó al paciente después de que este cliente lo leyó. Se responde 409 para que el
 * cliente recargue y decida, en lugar de pisar el cambio del otro sin saberlo.
 */
public class VersionDesactualizadaException extends RuntimeException {

	private final long versionEnviada;
	private final long versionActual;

	public VersionDesactualizadaException(long versionEnviada, long versionActual) {
		super("El paciente cambió mientras lo editabas (versión %d, actual %d). Recarga sus datos y vuelve a intentarlo."
				.formatted(versionEnviada, versionActual));
		this.versionEnviada = versionEnviada;
		this.versionActual = versionActual;
	}

	public long getVersionEnviada() {
		return versionEnviada;
	}

	public long getVersionActual() {
		return versionActual;
	}

}
