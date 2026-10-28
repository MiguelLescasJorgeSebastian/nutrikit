package dev.jorgemiguel.nutrikit.patients.domain;

import java.time.Instant;
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
 * Versión del aviso de privacidad de un consultorio. Una vez publicada no cambia
 * ({@link Immutable}): un consentimiento apunta a una versión concreta y debe poder mostrarse
 * exactamente lo que el paciente aceptó.
 */
@Entity
@Immutable
@Table(name = "aviso_de_privacidad")
@Filter(name = FiltroDeTenant.NOMBRE)
public class AvisoDePrivacidad {

	private static final Pattern FORMATO_VERSION = Pattern.compile("^[A-Za-z0-9._-]{1,20}$");
	public static final int CONTENIDO_MAXIMO = 100_000;

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@Column(nullable = false, length = 20)
	private String version;

	@Column(nullable = false, columnDefinition = "text")
	private String contenido;

	@Column(name = "publicado_en", nullable = false)
	private Instant publicadoEn;

	/** Solo para Hibernate. */
	protected AvisoDePrivacidad() {
	}

	private AvisoDePrivacidad(UUID tenantId, String version, String contenido, Instant publicadoEn) {
		this.tenantId = tenantId;
		this.version = version;
		this.contenido = contenido;
		this.publicadoEn = publicadoEn;
	}

	public static AvisoDePrivacidad publicar(String version, String contenido, Instant ahora) {
		if (version == null || !FORMATO_VERSION.matcher(version.strip()).matches()) {
			throw new DatoInvalidoException("version", "solo letras, números, punto, guion y guion bajo; máximo 20");
		}
		if (contenido == null || contenido.isBlank()) {
			throw new DatoInvalidoException("contenido", "es obligatorio");
		}
		if (contenido.length() > CONTENIDO_MAXIMO) {
			throw new DatoInvalidoException("contenido", "admite máximo %d caracteres".formatted(CONTENIDO_MAXIMO));
		}
		return new AvisoDePrivacidad(TenantContext.requerido(), version.strip(), contenido.strip(), ahora);
	}

	public UUID getId() {
		return id;
	}

	public String getVersion() {
		return version;
	}

	public String getContenido() {
		return contenido;
	}

	public Instant getPublicadoEn() {
		return publicadoEn;
	}

}
