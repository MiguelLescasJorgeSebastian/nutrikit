package dev.jorgemiguel.nutrikit.tenants.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import dev.jorgemiguel.nutrikit.tenants.domain.TenantRepository;

/** Reglas para extraer el slug de una petición, sin base de datos. */
class ResolutorDeTenantTest {

	private final TenantRepository repository = mock(TenantRepository.class);

	private ResolutorDeTenant resolutor(boolean cabeceraHabilitada) {
		return new ResolutorDeTenant(repository, new TenancyProperties("nutrikit.mx", cabeceraHabilitada, "X-Tenant"));
	}

	private static MockHttpServletRequest peticionA(String host) {
		var peticion = new MockHttpServletRequest();
		peticion.setServerName(host);
		return peticion;
	}

	@Test
	void tomaElSlugDelSubdominio() {
		assertThat(resolutor(false).slugDe(peticionA("nutri-ana.nutrikit.mx"))).contains("nutri-ana");
	}

	@Test
	void ignoraMayusculasEnElHost() {
		assertThat(resolutor(false).slugDe(peticionA("Nutri-Ana.NutriKit.MX"))).contains("nutri-ana");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"nutrikit.mx",              // dominio raíz
			"www.nutrikit.mx",          // subdominio reservado
			"api.nutrikit.mx",          // subdominio reservado
			"a.b.nutrikit.mx",          // más de un nivel
			"nutri-ana.otrodominio.mx", // otro dominio
			"nutrikit.mx.atacante.com", // dominio que solo contiene el nuestro
			"localhost",
	})
	void noHayConsultorioEnOtrosHosts(String host) {
		assertThat(resolutor(false).slugDe(peticionA(host))).isEmpty();
	}

	@Test
	void ignoraLaCabeceraSiNoEstaHabilitada() {
		var peticion = peticionA("nutrikit.mx");
		peticion.addHeader("X-Tenant", "nutri-ana");

		assertThat(resolutor(false).slugDe(peticion)).isEmpty();
	}

	@Test
	void laCabeceraHabilitadaTienePrioridadSobreElSubdominio() {
		var peticion = peticionA("nutri-beto.nutrikit.mx");
		peticion.addHeader("X-Tenant", "nutri-ana");

		assertThat(resolutor(true).slugDe(peticion)).contains("nutri-ana");
	}

}
