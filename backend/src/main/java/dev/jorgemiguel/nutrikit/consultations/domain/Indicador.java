package dev.jorgemiguel.nutrikit.consultations.domain;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import dev.jorgemiguel.nutrikit.shared.FiltroDeTenant;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Algo que se mide en una consulta: peso, cintura, % de grasa... Es un catálogo: agregar uno es
 * una fila, no una columna. Los globales ({@code tenant_id} nulo) los ven todos los consultorios;
 * los propios, solo el suyo (filtro {@link FiltroDeTenant#NOMBRE_CON_GLOBALES} y RLS).
 */
@Entity
@Immutable
@Table(name = "indicador")
@Filter(name = FiltroDeTenant.NOMBRE_CON_GLOBALES)
public class Indicador {

	private static final Pattern FORMATO_CODIGO = Pattern.compile("^[a-z][a-z0-9_]{0,39}$");
	public static final int ESCALA = 3;

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	/** Nulo en los globales. */
	@Column(name = "tenant_id", updatable = false)
	private UUID tenantId;

	@Column(nullable = false, length = 40)
	private String codigo;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(nullable = false, length = 20)
	private String unidad;

	@Column(nullable = false)
	private boolean calculado;

	@Column(length = 60)
	private String formula;

	@Column(name = "valor_minimo", nullable = false, precision = 10, scale = 3)
	private BigDecimal valorMinimo;

	@Column(name = "valor_maximo", nullable = false, precision = 10, scale = 3)
	private BigDecimal valorMaximo;

	/** Solo para Hibernate. */
	protected Indicador() {
	}

	/** Indicador propio del consultorio actual, capturado a mano. */
	public static Indicador propio(String codigo, String nombre, String unidad, BigDecimal minimo, BigDecimal maximo) {
		var indicador = new Indicador();
		indicador.tenantId = TenantContext.requerido();
		indicador.codigo = codigoValido(codigo);
		indicador.nombre = texto("nombre", nombre, 100);
		indicador.unidad = texto("unidad", unidad, 20);
		if (minimo == null || maximo == null || minimo.compareTo(maximo) >= 0) {
			throw new MedicionInvalidaException("valorMaximo", "el rango debe tener un mínimo menor que el máximo");
		}
		indicador.valorMinimo = minimo;
		indicador.valorMaximo = maximo;
		return indicador;
	}

	/**
	 * Comprueba que un valor esté dentro del rango razonable y tenga a lo más 3 decimales.
	 *
	 * @return el valor sin ceros sobrantes
	 */
	public BigDecimal validar(BigDecimal valor) {
		if (valor == null) {
			throw new MedicionInvalidaException(codigo, "el valor es obligatorio");
		}
		BigDecimal limpio = valor.stripTrailingZeros();
		if (limpio.scale() > ESCALA) {
			throw new MedicionInvalidaException(codigo, "admite máximo %d decimales".formatted(ESCALA));
		}
		if (limpio.compareTo(valorMinimo) < 0 || limpio.compareTo(valorMaximo) > 0) {
			throw new MedicionInvalidaException(codigo, "debe estar entre %s y %s %s"
					.formatted(valorMinimo.stripTrailingZeros().toPlainString(), valorMaximo.stripTrailingZeros().toPlainString(), unidad));
		}
		return limpio;
	}

	public boolean esGlobal() {
		return tenantId == null;
	}

	static String codigoValido(String codigo) {
		if (codigo == null || !FORMATO_CODIGO.matcher(codigo).matches()) {
			throw new MedicionInvalidaException("codigo",
					"minúsculas, números y guion bajo, empieza con letra; máximo 40 (por ejemplo: perimetro_brazo)");
		}
		return codigo;
	}

	private static String texto(String campo, String valor, int maximo) {
		if (valor == null || valor.isBlank()) {
			throw new MedicionInvalidaException(campo, "es obligatorio");
		}
		if (valor.strip().length() > maximo) {
			throw new MedicionInvalidaException(campo, "admite máximo %d caracteres".formatted(maximo));
		}
		return valor.strip();
	}

	public UUID getId() {
		return id;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getNombre() {
		return nombre;
	}

	public String getUnidad() {
		return unidad;
	}

	public boolean isCalculado() {
		return calculado;
	}

	public BigDecimal getValorMinimo() {
		return valorMinimo;
	}

	public BigDecimal getValorMaximo() {
		return valorMaximo;
	}

}
