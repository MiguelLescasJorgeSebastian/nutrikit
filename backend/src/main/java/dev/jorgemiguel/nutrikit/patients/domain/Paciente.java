package dev.jorgemiguel.nutrikit.patients.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

import dev.jorgemiguel.nutrikit.shared.FiltroDeTenant;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Paciente de un consultorio: raíz del expediente.
 *
 * <p>La baja es lógica ({@code eliminado_en}). {@link SQLRestriction} agrega
 * {@code eliminado_en IS NULL} a toda consulta de la entidad, así un paciente dado de baja no se
 * cuela en listados, búsquedas ni en {@code findById}, pero su fila y su historial se conservan.
 */
@Entity
@Table(name = "paciente")
@Filter(name = FiltroDeTenant.NOMBRE)
@SQLRestriction("eliminado_en IS NULL")
public class Paciente {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(nullable = false, length = 150)
	private String apellidos;

	@Column(name = "fecha_nacimiento", nullable = false)
	private LocalDate fechaNacimiento;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Sexo sexo;

	@Embedded
	private Telefono telefono;

	@Embedded
	private CorreoElectronico email;

	@Column(length = 100)
	private String ocupacion;

	@Column(name = "eliminado_en")
	private Instant eliminadoEn;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	/** Bloqueo optimista: Hibernate la incrementa en cada actualización y falla si cambió en medio. */
	@Version
	@Column(nullable = false)
	private long version;

	/** Columna generada por la base para la búsqueda; solo lectura. */
	@Column(name = "texto_busqueda", insertable = false, updatable = false, columnDefinition = "text")
	private String textoBusqueda;

	/** Solo para Hibernate. */
	protected Paciente() {
	}

	private Paciente(UUID tenantId, DatosPersonales datos, Instant ahora) {
		this.tenantId = tenantId;
		this.creadoEn = ahora;
		aplicar(datos);
	}

	/** Alta en el consultorio actual; el {@code tenant_id} sale del contexto, no de la entrada. */
	public static Paciente registrar(DatosPersonales datos, Instant ahora) {
		return new Paciente(TenantContext.requerido(), datos, ahora);
	}

	public void actualizar(DatosPersonales datos) {
		aplicar(datos);
	}

	public void darDeBaja(Instant ahora) {
		if (eliminadoEn == null) {
			eliminadoEn = ahora;
		}
	}

	/** La edad no se guarda: cambia sola con el tiempo y quedaría desactualizada. */
	public int edad(LocalDate hoy) {
		return Period.between(fechaNacimiento, hoy).getYears();
	}

	private void aplicar(DatosPersonales datos) {
		this.nombre = datos.nombre();
		this.apellidos = datos.apellidos();
		this.fechaNacimiento = datos.fechaNacimiento();
		this.sexo = datos.sexo();
		this.telefono = datos.telefono();
		this.email = datos.email();
		this.ocupacion = datos.ocupacion();
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public String getNombre() {
		return nombre;
	}

	public String getApellidos() {
		return apellidos;
	}

	public LocalDate getFechaNacimiento() {
		return fechaNacimiento;
	}

	public Sexo getSexo() {
		return sexo;
	}

	public Telefono getTelefono() {
		return telefono;
	}

	public CorreoElectronico getEmail() {
		return email;
	}

	public String getOcupacion() {
		return ocupacion;
	}

	public Instant getEliminadoEn() {
		return eliminadoEn;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public long getVersion() {
		return version;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof Paciente otro && id != null && id.equals(otro.id);
	}

	@Override
	public int hashCode() {
		return Paciente.class.hashCode();
	}

	/** Solo el id: los datos personales no deben terminar en un log. */
	@Override
	public String toString() {
		return "Paciente[id=%s]".formatted(id);
	}

}
