package dev.jorgemiguel.nutrikit.tenants.domain;

/** El slug no cumple el formato de un identificador público de consultorio. */
public class SlugInvalidoException extends RuntimeException {

	private final String valor;

	public SlugInvalidoException(String valor, String motivo) {
		super("Slug inválido '%s': %s".formatted(valor, motivo));
		this.valor = valor;
	}

	public String getValor() {
		return valor;
	}

}
