package dev.jorgemiguel.nutrikit.tenants.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/**
 * Consultorio: raíz de agregado y dueño de todos los datos del sistema ({@code tenant_id}).
 *
 * <p>No tiene setters: el estado solo cambia con {@link #suspender()}, {@link #reactivar()} y
 * {@link #cancelar()}, que aplican las reglas del ciclo de vida de {@link EstadoTenant}.
 */
@Entity
@Table(name = "tenant")
public class Tenant {

	public static final int NOMBRE_LONGITUD_MAXIMA = 150;

	/** UUIDv7: ordenado por tiempo, así los índices B-tree no se fragmentan como con v4. */
	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Embedded
	private Slug slug;

	@Column(nullable = false, length = NOMBRE_LONGITUD_MAXIMA)
	private String nombre;

	@Column(name = "dominio_propio")
	private String dominioPropio;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoTenant estado;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	/** Solo para Hibernate. */
	protected Tenant() {
	}

	private Tenant(Slug slug, String nombre) {
		this.slug = Objects.requireNonNull(slug, "El slug es obligatorio");
		this.nombre = validarNombre(nombre);
		this.estado = EstadoTenant.ACTIVO;
		this.creadoEn = Instant.now();
	}

	/** Da de alta un consultorio nuevo, siempre en estado {@link EstadoTenant#ACTIVO}. */
	public static Tenant crear(Slug slug, String nombre) {
		return new Tenant(slug, nombre);
	}

	public void suspender() {
		cambiarEstado(EstadoTenant.SUSPENDIDO);
	}

	public void reactivar() {
		cambiarEstado(EstadoTenant.ACTIVO);
	}

	public void cancelar() {
		cambiarEstado(EstadoTenant.CANCELADO);
	}

	public void renombrar(String nombre) {
		this.nombre = validarNombre(nombre);
	}

	public boolean estaActivo() {
		return estado == EstadoTenant.ACTIVO;
	}

	private void cambiarEstado(EstadoTenant destino) {
		if (!estado.puedeCambiarA(destino)) {
			throw new TransicionDeEstadoInvalidaException(estado, destino);
		}
		this.estado = destino;
	}

	private static String validarNombre(String nombre) {
		if (nombre == null || nombre.isBlank()) {
			throw new IllegalArgumentException("El nombre del consultorio es obligatorio");
		}
		String limpio = nombre.strip();
		if (limpio.length() > NOMBRE_LONGITUD_MAXIMA) {
			throw new IllegalArgumentException(
					"El nombre del consultorio admite máximo %d caracteres".formatted(NOMBRE_LONGITUD_MAXIMA));
		}
		return limpio;
	}

	public UUID getId() {
		return id;
	}

	public Slug getSlug() {
		return slug;
	}

	public String getNombre() {
		return nombre;
	}

	public String getDominioPropio() {
		return dominioPropio;
	}

	public EstadoTenant getEstado() {
		return estado;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	/** Igualdad por identidad: dos instancias son el mismo consultorio si tienen el mismo id. */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof Tenant otro && id != null && id.equals(otro.id);
	}

	@Override
	public int hashCode() {
		return Tenant.class.hashCode();
	}

	@Override
	public String toString() {
		return "Tenant[id=%s, slug=%s, estado=%s]".formatted(id, slug, estado);
	}

}
