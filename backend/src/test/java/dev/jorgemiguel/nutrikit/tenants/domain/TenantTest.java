package dev.jorgemiguel.nutrikit.tenants.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TenantTest {

	private Tenant nuevoTenant() {
		return Tenant.crear(Slug.of("nutri-ana"), "Consultorio Ana");
	}

	@Nested
	class Alta {

		@Test
		void naceActivoConSuSlugYNombre() {
			Tenant tenant = nuevoTenant();

			assertThat(tenant.getEstado()).isEqualTo(EstadoTenant.ACTIVO);
			assertThat(tenant.getSlug()).isEqualTo(Slug.of("nutri-ana"));
			assertThat(tenant.getNombre()).isEqualTo("Consultorio Ana");
			assertThat(tenant.getCreadoEn()).isNotNull();
		}

		@Test
		void quitaEspaciosSobrantesDelNombre() {
			Tenant tenant = Tenant.crear(Slug.of("nutri-ana"), "  Consultorio Ana  ");

			assertThat(tenant.getNombre()).isEqualTo("Consultorio Ana");
		}

		@Test
		void exigeNombre() {
			assertThatThrownBy(() -> Tenant.crear(Slug.of("nutri-ana"), "  "))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void rechazaNombresDeMasDe150Caracteres() {
			String largo = "a".repeat(Tenant.NOMBRE_LONGITUD_MAXIMA + 1);

			assertThatThrownBy(() -> Tenant.crear(Slug.of("nutri-ana"), largo))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void exigeSlug() {
			assertThatThrownBy(() -> Tenant.crear(null, "Consultorio Ana"))
					.isInstanceOf(NullPointerException.class);
		}

	}

	@Nested
	class TransicionesPermitidas {

		@Test
		void unActivoSePuedeSuspender() {
			Tenant tenant = nuevoTenant();

			tenant.suspender();

			assertThat(tenant.getEstado()).isEqualTo(EstadoTenant.SUSPENDIDO);
		}

		@Test
		void unSuspendidoSePuedeReactivar() {
			Tenant tenant = nuevoTenant();
			tenant.suspender();

			tenant.reactivar();

			assertThat(tenant.estaActivo()).isTrue();
		}

		@Test
		void unActivoSePuedeCancelar() {
			Tenant tenant = nuevoTenant();

			tenant.cancelar();

			assertThat(tenant.getEstado()).isEqualTo(EstadoTenant.CANCELADO);
		}

		@Test
		void unSuspendidoSePuedeCancelar() {
			Tenant tenant = nuevoTenant();
			tenant.suspender();

			tenant.cancelar();

			assertThat(tenant.getEstado()).isEqualTo(EstadoTenant.CANCELADO);
		}

	}

	@Nested
	class TransicionesInvalidas {

		@Test
		void unCanceladoNoSeReactiva() {
			Tenant tenant = nuevoTenant();
			tenant.cancelar();

			assertThatThrownBy(tenant::reactivar)
					.isInstanceOf(TransicionDeEstadoInvalidaException.class)
					.hasMessageContaining("CANCELADO a ACTIVO");
			assertThat(tenant.getEstado()).isEqualTo(EstadoTenant.CANCELADO);
		}

		@Test
		void unCanceladoNoSeSuspende() {
			Tenant tenant = nuevoTenant();
			tenant.cancelar();

			assertThatThrownBy(tenant::suspender).isInstanceOf(TransicionDeEstadoInvalidaException.class);
		}

		@Test
		void unCanceladoNoSeCancelaDosVeces() {
			Tenant tenant = nuevoTenant();
			tenant.cancelar();

			assertThatThrownBy(tenant::cancelar).isInstanceOf(TransicionDeEstadoInvalidaException.class);
		}

		@Test
		void unActivoNoSeReactiva() {
			Tenant tenant = nuevoTenant();

			assertThatThrownBy(tenant::reactivar)
					.isInstanceOf(TransicionDeEstadoInvalidaException.class)
					.satisfies(e -> {
						var error = (TransicionDeEstadoInvalidaException) e;
						assertThat(error.getOrigen()).isEqualTo(EstadoTenant.ACTIVO);
						assertThat(error.getDestino()).isEqualTo(EstadoTenant.ACTIVO);
					});
		}

		@Test
		void unSuspendidoNoSeSuspendeDosVeces() {
			Tenant tenant = nuevoTenant();
			tenant.suspender();

			assertThatThrownBy(tenant::suspender).isInstanceOf(TransicionDeEstadoInvalidaException.class);
		}

	}

}
