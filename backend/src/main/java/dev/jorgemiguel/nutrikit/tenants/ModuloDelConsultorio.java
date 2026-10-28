package dev.jorgemiguel.nutrikit.tenants;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catálogo de módulos que un consultorio tiene encendidos o apagados (RF-TEN-05).
 *
 * <p>El código de cada módulo es el nombre de su paquete en Spring Modulith
 * ({@code dev.jorgemiguel.nutrikit.<codigo>}). Así, a qué módulo pertenece un controlador se
 * deduce de su paquete y no hay que marcarlo a mano.
 *
 * <p>Los módulos de núcleo están siempre encendidos. Los de plataforma ({@code tenants},
 * {@code iam}, {@code audit}, {@code shared}) no son módulos del consultorio y no aparecen aquí.
 *
 * <p>Si agregas un módulo, agrégalo también al {@code CHECK} de {@code tenant_modulo.modulo} con
 * una migración nueva; {@code CatalogoDeModulosTest} falla si no coinciden.
 */
public enum ModuloDelConsultorio {

	PACIENTES("patients", true, true),
	CONSULTAS("consultations", false, true),
	ACCESO_DEL_PACIENTE("patientaccess", false, true),
	PLANES_DE_ALIMENTACION("mealplans", false, false),
	INTEGRACIONES("integrations", false, false),
	AGENDA("scheduling", false, false),
	SITIO_WEB("website", false, false);

	private static final String PAQUETE_BASE = paqueteBase();

	private final String codigo;
	private final boolean nucleo;
	private final boolean encendidoPorDefecto;

	ModuloDelConsultorio(String codigo, boolean nucleo, boolean encendidoPorDefecto) {
		this.codigo = codigo;
		this.nucleo = nucleo;
		this.encendidoPorDefecto = encendidoPorDefecto;
	}

	/** Lo que se guarda en {@code tenant_modulo.modulo}. */
	public String codigo() {
		return codigo;
	}

	/** Siempre encendido: no se puede apagar. */
	public boolean nucleo() {
		return nucleo;
	}

	/** Estado con el que se registra en un consultorio nuevo. */
	public boolean encendidoPorDefecto() {
		return encendidoPorDefecto;
	}

	public static Optional<ModuloDelConsultorio> deCodigo(String codigo) {
		return Arrays.stream(values()).filter(m -> m.codigo.equals(codigo)).findFirst();
	}

	/**
	 * El módulo al que pertenece una clase, por su paquete. Vacío si la clase es de un módulo de
	 * plataforma o de fuera de la aplicación.
	 */
	public static Optional<ModuloDelConsultorio> deClase(Class<?> clase) {
		String nombre = clase.getName();
		if (!nombre.startsWith(PAQUETE_BASE)) {
			return Optional.empty();
		}
		String resto = nombre.substring(PAQUETE_BASE.length());
		int punto = resto.indexOf('.');
		return punto < 0 ? Optional.empty() : deCodigo(resto.substring(0, punto));
	}

	/** {@code dev.jorgemiguel.nutrikit.}: el paquete padre de este módulo. */
	private static String paqueteBase() {
		String propio = ModuloDelConsultorio.class.getPackageName();
		return propio.substring(0, propio.lastIndexOf('.') + 1);
	}

}
