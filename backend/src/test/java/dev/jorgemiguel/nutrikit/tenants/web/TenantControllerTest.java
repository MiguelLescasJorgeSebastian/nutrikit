package dev.jorgemiguel.nutrikit.tenants.web;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import dev.jorgemiguel.nutrikit.iam.infrastructure.SeguridadConfig;
import dev.jorgemiguel.nutrikit.tenants.application.TenantNoEncontradoException;
import dev.jorgemiguel.nutrikit.tenants.application.TenantService;
import dev.jorgemiguel.nutrikit.tenants.domain.EstadoTenant;
import dev.jorgemiguel.nutrikit.tenants.domain.Slug;
import dev.jorgemiguel.nutrikit.tenants.domain.SlugDuplicadoException;
import dev.jorgemiguel.nutrikit.tenants.domain.Tenant;
import dev.jorgemiguel.nutrikit.tenants.domain.TransicionDeEstadoInvalidaException;

/**
 * Prueba de la capa web: rutas, validación, códigos de estado y formato de errores. El servicio
 * es un mock; la integración con la base está en {@link TenantApiTest}.
 */
@WebMvcTest(TenantController.class)
@Import(SeguridadConfig.class)
class TenantControllerTest {

	private static final String RUTA = "/api/operador/tenants";
	private static final UUID ID = UUID.fromString("019a2b3c-4d5e-7f60-8a1b-2c3d4e5f6a7b");

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private TenantService service;

	/** La configuración de seguridad lo necesita; {@code jwt()} evita usarlo. */
	@MockitoBean
	private JwtDecoder jwtDecoder;

	private static RequestPostProcessor operador() {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"));
	}

	/** Peticiones con un JWT de operador; las rutas de este controlador lo exigen. */
	private static MockHttpServletRequestBuilder get(String url) {
		return MockMvcRequestBuilders.get(url).with(operador());
	}

	private static MockHttpServletRequestBuilder post(String url) {
		return MockMvcRequestBuilders.post(url).with(operador());
	}

	private static MockHttpServletRequestBuilder patch(String url) {
		return MockMvcRequestBuilders.patch(url).with(operador());
	}

	private static Tenant tenantConId(String slug) {
		Tenant tenant = Tenant.crear(Slug.of(slug), "Consultorio Ana");
		ReflectionTestUtils.setField(tenant, "id", ID);
		return tenant;
	}

	@Test
	void crearDevuelve201ConLocationYElRecurso() throws Exception {
		given(service.crear(Slug.of("nutri-ana"), "Consultorio Ana")).willReturn(tenantConId("nutri-ana"));

		mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
				{"slug": "nutri-ana", "nombre": "Consultorio Ana"}
				"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost" + RUTA + "/" + ID))
				.andExpect(jsonPath("$.id").value(ID.toString()))
				.andExpect(jsonPath("$.slug").value("nutri-ana"))
				.andExpect(jsonPath("$.nombre").value("Consultorio Ana"))
				.andExpect(jsonPath("$.estado").value("ACTIVO"))
				.andExpect(jsonPath("$.creadoEn").isNotEmpty());
	}

	@Test
	void laRespuestaSoloTieneLosCamposDelDto() throws Exception {
		given(service.obtener(ID)).willReturn(tenantConId("nutri-ana"));

		mvc.perform(get(RUTA + "/" + ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.*", hasSize(6)))
				.andExpect(jsonPath("$.estado").value("ACTIVO"));
	}

	@Test
	void unCuerpoInvalidoDevuelve400ConElDetallePorCampo() throws Exception {
		mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
				{"slug": "Nutri Ana", "nombre": ""}
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("Datos inválidos"))
				.andExpect(jsonPath("$.instance").value(RUTA))
				.andExpect(jsonPath("$.errores[?(@.campo == 'nombre')].mensaje").value("es obligatorio"))
				.andExpect(jsonPath("$.errores[?(@.campo == 'slug')]").isNotEmpty());

		verifyNoInteractions(service);
	}

	@Test
	void unJsonMalFormadoDevuelve400() throws Exception {
		mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"slug\":"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void unSlugDuplicadoDevuelve409() throws Exception {
		given(service.crear(any(), any())).willThrow(new SlugDuplicadoException(Slug.of("nutri-ana")));

		mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
				{"slug": "nutri-ana", "nombre": "Consultorio Ana"}
				"""))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Slug duplicado"))
				.andExpect(jsonPath("$.slug").value("nutri-ana"));
	}

	@Test
	void unIdInexistenteDevuelve404() throws Exception {
		given(service.obtener(ID)).willThrow(new TenantNoEncontradoException(ID));

		mvc.perform(get(RUTA + "/" + ID))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Consultorio no encontrado"));
	}

	@Test
	void unIdQueNoEsUuidDevuelve400() throws Exception {
		mvc.perform(get(RUTA + "/no-es-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void cambiarEstadoDevuelveElTenantActualizado() throws Exception {
		Tenant suspendido = tenantConId("nutri-ana");
		suspendido.suspender();
		given(service.cambiarEstado(ID, EstadoTenant.SUSPENDIDO)).willReturn(suspendido);

		mvc.perform(patch(RUTA + "/" + ID + "/estado").contentType(MediaType.APPLICATION_JSON).content("""
				{"estado": "SUSPENDIDO"}
				"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("SUSPENDIDO"));
	}

	@Test
	void unaTransicionNoPermitidaDevuelve409() throws Exception {
		given(service.cambiarEstado(eq(ID), any()))
				.willThrow(new TransicionDeEstadoInvalidaException(EstadoTenant.CANCELADO, EstadoTenant.ACTIVO));

		mvc.perform(patch(RUTA + "/" + ID + "/estado").contentType(MediaType.APPLICATION_JSON).content("""
				{"estado": "ACTIVO"}
				"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.estadoActual").value("CANCELADO"))
				.andExpect(jsonPath("$.estadoSolicitado").value("ACTIVO"));
	}

	@Test
	void unEstadoDesconocidoDevuelve400() throws Exception {
		mvc.perform(patch(RUTA + "/" + ID + "/estado").contentType(MediaType.APPLICATION_JSON).content("""
				{"estado": "BORRADO"}
				"""))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(service);
	}

	@Test
	void listarDevuelveUnaPaginaConMetadatos() throws Exception {
		Pageable pagina = PageRequest.of(0, 20);
		given(service.listar(any())).willReturn(new PageImpl<>(List.of(tenantConId("nutri-ana")), pagina, 1));

		mvc.perform(get(RUTA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].slug").value("nutri-ana"))
				.andExpect(jsonPath("$.page.size").value(20))
				.andExpect(jsonPath("$.page.number").value(0))
				.andExpect(jsonPath("$.page.totalElements").value(1))
				.andExpect(jsonPath("$.page.totalPages").value(1));
	}

}
