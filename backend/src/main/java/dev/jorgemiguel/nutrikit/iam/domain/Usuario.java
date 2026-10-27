package dev.jorgemiguel.nutrikit.iam.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UuidGenerator;

import dev.jorgemiguel.nutrikit.shared.FiltroDeTenant;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Persona que entra al panel de un consultorio. Solo guarda el hash de la contraseña; nunca la
 * contraseña en claro.
 */
@Entity
@Table(name = "usuario")
@Filter(name = FiltroDeTenant.NOMBRE)
public class Usuario {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	/** {@code citext} en la base; aquí además se normaliza a minúsculas. */
	@Column(nullable = false, columnDefinition = "citext")
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(nullable = false, length = 150)
	private String nombre;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RolUsuario rol;

	@Column(nullable = false)
	private boolean activo;

	@Column(name = "ultimo_acceso")
	private Instant ultimoAcceso;

	/** Solo para Hibernate. */
	protected Usuario() {
	}

	private Usuario(UUID tenantId, String email, String nombre, RolUsuario rol, String passwordHash) {
		this.tenantId = tenantId;
		this.email = email;
		this.nombre = nombre;
		this.rol = rol;
		this.passwordHash = passwordHash;
		this.activo = true;
	}

	/**
	 * Registra un usuario en el consultorio actual. Recibe la contraseña ya cifrada: el dominio
	 * nunca ve la contraseña en claro.
	 */
	public static Usuario registrar(String email, String nombre, RolUsuario rol, String passwordHash) {
		if (nombre == null || nombre.isBlank()) {
			throw new IllegalArgumentException("El nombre es obligatorio");
		}
		return new Usuario(TenantContext.requerido(), normalizarEmail(email), nombre.strip(),
				Objects.requireNonNull(rol, "El rol es obligatorio"),
				Objects.requireNonNull(passwordHash, "El hash de la contraseña es obligatorio"));
	}

	/** Forma canónica del correo para guardarlo y buscarlo. */
	public static String normalizarEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("El correo es obligatorio");
		}
		return email.strip().toLowerCase(Locale.ROOT);
	}

	public void registrarAcceso(Instant momento) {
		this.ultimoAcceso = momento;
	}

	public void desactivar() {
		this.activo = false;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getNombre() {
		return nombre;
	}

	public RolUsuario getRol() {
		return rol;
	}

	public boolean isActivo() {
		return activo;
	}

	public Instant getUltimoAcceso() {
		return ultimoAcceso;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof Usuario otro && id != null && id.equals(otro.id);
	}

	@Override
	public int hashCode() {
		return Usuario.class.hashCode();
	}

	/** Sin el hash de la contraseña, para que nunca termine en un log. */
	@Override
	public String toString() {
		return "Usuario[id=%s, rol=%s, activo=%s]".formatted(id, rol, activo);
	}

}
