package dev.jorgemiguel.nutrikit.tenants.domain;

/** Se intentó un cambio de estado que el ciclo de vida del consultorio no permite. */
public class TransicionDeEstadoInvalidaException extends RuntimeException {

	private final EstadoTenant origen;
	private final EstadoTenant destino;

	public TransicionDeEstadoInvalidaException(EstadoTenant origen, EstadoTenant destino) {
		super("No se puede pasar un consultorio de %s a %s".formatted(origen, destino));
		this.origen = origen;
		this.destino = destino;
	}

	public EstadoTenant getOrigen() {
		return origen;
	}

	public EstadoTenant getDestino() {
		return destino;
	}

}
