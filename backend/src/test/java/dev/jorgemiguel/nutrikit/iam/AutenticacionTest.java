package dev.jorgemiguel.nutrikit.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import dev.jorgemiguel.nutrikit.TestcontainersConfiguration;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Autenticación de punta a punta con tokens reales: login, JWT, roles, aislamiento entre
 * consultorios, rotación de refresh tokens (en su cookie HttpOnly) y límite de intentos.
 *
 * <p>Cada prueba usa su propia IP de origen, porque el límite de intentos por IP se comparte
 * entre las pruebas del mismo contexto.
 */
@SpringBootTest(properties = {
		"nutrikit.tenancy.cabecera-habilitada=true",
		"nutrikit.seguridad.operador.email=operador@nutrikit.test",
		// operador-dev
		"nutrikit.seguridad.operador.password-hash={bcrypt}$2a$10$qdeesc9rka5wcWdzdenUJ.GVQV0P3l0bwOfMhpa2wJtJp7ClWEOu2" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AutenticacionTest {

	private static final String PASSWORD = "contraseña-de-prueba-ana";
	private static final String COOKIE = "nutrikit_refresh";
	private static final int CATORCE_DIAS = (int) Duration.ofDays(14).toSeconds();
	private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate tx;

	private final List<UUID> consultoriosCreados = new ArrayList<>();
	private String ip;
	private String tokenOperador;

	@BeforeEach
	void preparar() throws Exception {
		ip = "10.0.%d.%d".formatted(SIGUIENTE_IP.get() / 250, SIGUIENTE_IP.getAndIncrement() % 250 + 1);
		tokenOperador = campo(loginOperador("operador@nutrikit.test", "operador-dev").andExpect(status().isOk()),
				"$.accessToken");
	}

	@AfterEach
	void borrarConsultorios() {
		// Borra en cascada sus usuarios y refresh tokens.
		consultoriosCreados.forEach(id -> jdbc.sql("DELETE FROM tenant WHERE id = ?").param(id).update());
	}

	// --- utilidades ---------------------------------------------------------------------------

	private UUID consultorioConUsuario(String slug, String email, String rol) throws Exception {
		String respuesta = mvc.perform(post("/api/operador/tenants").header(HttpHeaders.AUTHORIZATION, bearer(tokenOperador))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"slug\": \"%s\", \"nombre\": \"Consultorio %s\"}".formatted(slug, slug)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		UUID id = UUID.fromString(JsonPath.read(respuesta, "$.id"));
		consultoriosCreados.add(id);
		crearUsuario(id, email, rol).andExpect(status().isCreated());
		return id;
	}

	private ResultActions crearUsuario(UUID tenantId, String email, String rol) throws Exception {
		return mvc.perform(post("/api/operador/tenants/%s/usuarios".formatted(tenantId))
				.header(HttpHeaders.AUTHORIZATION, bearer(tokenOperador))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "nombre": "Usuario de prueba", "rol": "%s", "password": "%s"}
						""".formatted(email, rol, PASSWORD)));
	}

	private MockHttpServletRequestBuilder desde(MockHttpServletRequestBuilder peticion) {
		return peticion.with(r -> {
			r.setRemoteAddr(ip);
			return r;
		});
	}

	private ResultActions login(String slug, String email, String password) throws Exception {
		return mvc.perform(desde(post("/api/auth/login")).header("X-Tenant", slug)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
	}

	private ResultActions loginOperador(String email, String password) throws Exception {
		return mvc.perform(desde(post("/api/operador/auth/login"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
	}

	private ResultActions renovar(String slug, String refreshToken) throws Exception {
		return mvc.perform(post("/api/auth/refresh").header("X-Tenant", slug).cookie(cookieDeRefresh(refreshToken)));
	}

	private ResultActions cerrarSesion(String slug, String refreshToken) throws Exception {
		return mvc.perform(post("/api/auth/logout").header("X-Tenant", slug).cookie(cookieDeRefresh(refreshToken)));
	}

	private static Cookie cookieDeRefresh(String refreshToken) {
		return new Cookie(COOKIE, refreshToken);
	}

	/** El refresh token que dejó la respuesta en su cookie. */
	private static String refresh(ResultActions resultado) {
		Cookie cookie = resultado.andReturn().getResponse().getCookie(COOKIE);
		assertThat(cookie).as("cookie " + COOKIE).isNotNull();
		return cookie.getValue();
	}

	private ResultActions consultorioActual(String slug, String accessToken) throws Exception {
		var peticion = get("/api/consultorio").header("X-Tenant", slug);
		if (accessToken != null) {
			peticion.header(HttpHeaders.AUTHORIZATION, bearer(accessToken));
		}
		return mvc.perform(peticion);
	}

	private static String campo(ResultActions resultado, String ruta) throws Exception {
		return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), ruta);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	// --- pruebas ------------------------------------------------------------------------------

	@Nested
	class InicioDeSesion {

		@Test
		void devuelveUnAccessTokenDe15MinutosYElRefreshTokenSoloEnUnaCookieHttpOnly() throws Exception {
			UUID ana = consultorioConUsuario("auth-ana-1", "ana@ana.mx", "TITULAR");

			var sesion = login("auth-ana-1", "ana@ana.mx", PASSWORD)
					.andExpect(status().isOk())
					.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
					.andExpect(jsonPath("$.tipo").value("Bearer"))
					.andExpect(jsonPath("$.refreshToken").doesNotExist())
					.andExpect(cookie().httpOnly(COOKIE, true))
					.andExpect(cookie().secure(COOKIE, true))
					.andExpect(cookie().sameSite(COOKIE, "Strict"))
					.andExpect(cookie().path(COOKIE, "/api/auth"))
					.andExpect(cookie().maxAge(COOKIE, both(greaterThan(CATORCE_DIAS - 60)).and(lessThanOrEqualTo(CATORCE_DIAS))));
			assertThat(refresh(sesion)).isNotBlank();

			Jwt jwt = jwtDecoder.decode(campo(sesion, "$.accessToken"));
			assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isLessThanOrEqualTo(Duration.ofMinutes(15));
			assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo(ana.toString());
			assertThat(jwt.getClaimAsString("rol")).isEqualTo("TITULAR");
			assertThat(jwt.getClaims()).doesNotContainKeys("email", "nombre", "password");
		}

		@Test
		void elAccessTokenDaAccesoASuConsultorio() throws Exception {
			consultorioConUsuario("auth-ana-2", "ana@ana.mx", "ASISTENTE");
			String token = campo(login("auth-ana-2", "ana@ana.mx", PASSWORD), "$.accessToken");

			consultorioActual("auth-ana-2", token)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.slug").value("auth-ana-2"));
		}

		@Test
		void elCorreoNoDistingueMayusculas() throws Exception {
			consultorioConUsuario("auth-ana-3", "ana@ana.mx", "TITULAR");

			login("auth-ana-3", "ANA@Ana.MX", PASSWORD).andExpect(status().isOk());
		}

		@Test
		void credencialesInvalidasDevuelven401SinRevelarQueFallo() throws Exception {
			consultorioConUsuario("auth-ana-4", "ana@ana.mx", "TITULAR");

			String malaPassword = campo(login("auth-ana-4", "ana@ana.mx", "no-es-la-contraseña")
					.andExpect(status().isUnauthorized()), "$.detail");
			String correoInexistente = campo(login("auth-ana-4", "nadie@ana.mx", PASSWORD)
					.andExpect(status().isUnauthorized()), "$.detail");

			assertThat(malaPassword).isEqualTo(correoInexistente).isEqualTo("Correo o contraseña incorrectos");
		}

		@Test
		void unUsuarioInactivoNoPuedeEntrar() throws Exception {
			UUID ana = consultorioConUsuario("auth-ana-5", "ana@ana.mx", "TITULAR");
			jdbcComo(ana, "UPDATE usuario SET activo = false WHERE email = 'ana@ana.mx'");

			login("auth-ana-5", "ana@ana.mx", PASSWORD).andExpect(status().isUnauthorized());
		}

		@Test
		void unUsuarioDeOtroConsultorioNoExisteAqui() throws Exception {
			consultorioConUsuario("auth-ana-6", "ana@ana.mx", "TITULAR");
			consultorioConUsuario("auth-beto-6", "beto@beto.mx", "TITULAR");

			login("auth-beto-6", "ana@ana.mx", PASSWORD).andExpect(status().isUnauthorized());
		}

	}

	@Nested
	class Contrasenas {

		@Test
		void seGuardanConHashNuncaEnClaro() throws Exception {
			UUID ana = consultorioConUsuario("auth-hash", "ana@ana.mx", "TITULAR");

			String hash = jdbcComoConsulta(ana, "SELECT password_hash FROM usuario WHERE email = 'ana@ana.mx'");

			assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
		}

		@Test
		void nuncaAparecenEnLosLogs(CapturedOutput salida) throws Exception {
			consultorioConUsuario("auth-logs", "ana@ana.mx", "TITULAR");

			login("auth-logs", "ana@ana.mx", PASSWORD).andExpect(status().isOk());
			login("auth-logs", "ana@ana.mx", "otra-contraseña-secreta").andExpect(status().isUnauthorized());

			assertThat(salida.getAll()).doesNotContain(PASSWORD).doesNotContain("otra-contraseña-secreta");
		}

	}

	@Nested
	class AccessToken {

		@Test
		void sinTokenDevuelve401() throws Exception {
			consultorioConUsuario("auth-sin-token", "ana@ana.mx", "TITULAR");

			consultorioActual("auth-sin-token", null)
					.andExpect(status().isUnauthorized())
					.andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}

		@Test
		void unTokenExpiradoDevuelve401() throws Exception {
			UUID ana = consultorioConUsuario("auth-expirado", "ana@ana.mx", "TITULAR");
			Instant hace1Hora = Instant.now().minus(Duration.ofHours(1));
			String expirado = firmar(jwtEncoder, ana, hace1Hora, hace1Hora.plus(Duration.ofMinutes(15)));

			consultorioActual("auth-expirado", expirado).andExpect(status().isUnauthorized());
		}

		@Test
		void unTokenFirmadoConOtraClaveDevuelve401() throws Exception {
			UUID ana = consultorioConUsuario("auth-falso", "ana@ana.mx", "TITULAR");
			byte[] otraClave = new byte[32];
			new SecureRandom().nextBytes(otraClave);
			var falsificador = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(otraClave, "HmacSHA256")).build();
			String falso = firmar(falsificador, ana, Instant.now(), Instant.now().plus(Duration.ofMinutes(15)));

			consultorioActual("auth-falso", falso).andExpect(status().isUnauthorized());
		}

		@Test
		void elTokenDeUnConsultorioNoSirveEnOtro() throws Exception {
			consultorioConUsuario("auth-ana-7", "ana@ana.mx", "TITULAR");
			consultorioConUsuario("auth-beto-7", "beto@beto.mx", "TITULAR");
			String tokenDeAna = campo(login("auth-ana-7", "ana@ana.mx", PASSWORD), "$.accessToken");

			consultorioActual("auth-beto-7", tokenDeAna)
					.andExpect(status().isForbidden())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}

		private static String firmar(JwtEncoder encoder, UUID tenantId, Instant emitido, Instant expira) {
			var claims = JwtClaimsSet.builder().issuer("nutrikit").subject(UUID.randomUUID().toString())
					.issuedAt(emitido).expiresAt(expira)
					.claim("tenant_id", tenantId.toString()).claim("rol", "TITULAR").build();
			return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
					.getTokenValue();
		}

	}

	@Nested
	class Roles {

		@Test
		void unRolSinPermisoRecibe403() throws Exception {
			consultorioConUsuario("auth-rol", "ana@ana.mx", "TITULAR");
			String tokenDeTitular = campo(login("auth-rol", "ana@ana.mx", PASSWORD), "$.accessToken");

			mvc.perform(get("/api/operador/tenants").header(HttpHeaders.AUTHORIZATION, bearer(tokenDeTitular)))
					.andExpect(status().isForbidden());
		}

		@Test
		void elOperadorAdministraConsultoriosPeroNoEntraAUnoComoUsuario() throws Exception {
			consultorioConUsuario("auth-operador", "ana@ana.mx", "TITULAR");

			mvc.perform(get("/api/operador/tenants").header(HttpHeaders.AUTHORIZATION, bearer(tokenOperador)))
					.andExpect(status().isOk());
			consultorioActual("auth-operador", tokenOperador).andExpect(status().isForbidden());
		}

		@Test
		void elOperadorRecibeSoloAccessTokenConRolOperador() throws Exception {
			var sesion = loginOperador("operador@nutrikit.test", "operador-dev")
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.refreshToken").doesNotExist());

			Jwt jwt = jwtDecoder.decode(campo(sesion, "$.accessToken"));
			assertThat(jwt.getClaimAsString("rol")).isEqualTo("OPERADOR");
			assertThat(jwt.getClaims()).doesNotContainKey("tenant_id");
		}

		@Test
		void credencialesDeOperadorInvalidasDevuelven401() throws Exception {
			loginOperador("operador@nutrikit.test", "no-es").andExpect(status().isUnauthorized());
		}

	}

	@Nested
	class RefreshTokens {

		@Test
		void renovarEntregaUnParNuevoYElAnteriorYaNoSirve() throws Exception {
			consultorioConUsuario("auth-rot-1", "ana@ana.mx", "TITULAR");
			String primero = refresh(login("auth-rot-1", "ana@ana.mx", PASSWORD));

			var renovada = renovar("auth-rot-1", primero)
					.andExpect(status().isOk())
					.andExpect(cookie().value(COOKIE, not(primero)))
					.andExpect(cookie().httpOnly(COOKIE, true));
			String segundo = refresh(renovada);

			consultorioActual("auth-rot-1", campo(renovada, "$.accessToken")).andExpect(status().isOk());
			renovar("auth-rot-1", segundo).andExpect(status().isOk());
		}

		@Test
		void reutilizarUnTokenRotadoInvalidaTodasLasSesiones() throws Exception {
			consultorioConUsuario("auth-rot-2", "ana@ana.mx", "TITULAR");
			String robado = refresh(login("auth-rot-2", "ana@ana.mx", PASSWORD));
			String otraSesion = refresh(login("auth-rot-2", "ana@ana.mx", PASSWORD));
			String legitimo = refresh(renovar("auth-rot-2", robado).andExpect(status().isOk()));

			// El atacante usa el token que ya se rotó.
			renovar("auth-rot-2", robado)
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.title").value("Sesión inválida"));

			// Se cerraron todas las sesiones del usuario, también las legítimas.
			renovar("auth-rot-2", legitimo).andExpect(status().isUnauthorized());
			renovar("auth-rot-2", otraSesion).andExpect(status().isUnauthorized());
		}

		@Test
		void cerrarSesionRevocaElRefreshTokenYBorraLaCookie() throws Exception {
			consultorioConUsuario("auth-logout", "ana@ana.mx", "TITULAR");
			String refresh = refresh(login("auth-logout", "ana@ana.mx", PASSWORD));

			cerrarSesion("auth-logout", refresh)
					.andExpect(status().isNoContent())
					.andExpect(cookie().maxAge(COOKIE, 0))
					.andExpect(cookie().path(COOKIE, "/api/auth"));

			renovar("auth-logout", refresh).andExpect(status().isUnauthorized());
		}

		@Test
		void cerrarSesionSinCookieTambienResponde204() throws Exception {
			consultorioConUsuario("auth-logout-2", "ana@ana.mx", "TITULAR");

			mvc.perform(post("/api/auth/logout").header("X-Tenant", "auth-logout-2"))
					.andExpect(status().isNoContent())
					.andExpect(cookie().maxAge(COOKIE, 0));
		}

		@Test
		void renovarSinCookieDevuelve401() throws Exception {
			consultorioConUsuario("auth-sin-cookie", "ana@ana.mx", "TITULAR");

			mvc.perform(post("/api/auth/refresh").header("X-Tenant", "auth-sin-cookie"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.title").value("Sesión inválida"));
		}

		@Test
		void unRefreshTokenEnElCuerpoYaNoSeAcepta() throws Exception {
			consultorioConUsuario("auth-cuerpo", "ana@ana.mx", "TITULAR");
			String refresh = refresh(login("auth-cuerpo", "ana@ana.mx", PASSWORD));

			mvc.perform(post("/api/auth/refresh").header("X-Tenant", "auth-cuerpo")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"refreshToken\": \"%s\"}".formatted(refresh)))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void unRefreshTokenExpiradoNoSirve() throws Exception {
			UUID ana = consultorioConUsuario("auth-rot-exp", "ana@ana.mx", "TITULAR");
			String refresh = refresh(login("auth-rot-exp", "ana@ana.mx", PASSWORD));
			jdbcComo(ana, "UPDATE refresh_token SET expira_en = now() - interval '1 minute'");

			renovar("auth-rot-exp", refresh).andExpect(status().isUnauthorized());
		}

		@Test
		void elRefreshTokenDeUnConsultorioNoSirveEnOtro() throws Exception {
			consultorioConUsuario("auth-ana-8", "ana@ana.mx", "TITULAR");
			consultorioConUsuario("auth-beto-8", "beto@beto.mx", "TITULAR");
			String deAna = refresh(login("auth-ana-8", "ana@ana.mx", PASSWORD));

			renovar("auth-beto-8", deAna).andExpect(status().isUnauthorized());
			// Y no se gastó: en su consultorio sigue sirviendo.
			renovar("auth-ana-8", deAna).andExpect(status().isOk());
		}

		@Test
		void soloSeGuardaElHashDelRefreshToken() throws Exception {
			UUID ana = consultorioConUsuario("auth-rot-hash", "ana@ana.mx", "TITULAR");
			String refresh = refresh(login("auth-rot-hash", "ana@ana.mx", PASSWORD));

			String guardado = jdbcComoConsulta(ana, "SELECT token_hash FROM refresh_token");

			assertThat(guardado).hasSize(64).isNotEqualTo(refresh);
		}

	}

	@Nested
	class LimiteDeIntentos {

		@Test
		void trasCincoFallosConElMismoCorreoResponde429AunqueLaContrasenaSeaCorrecta() throws Exception {
			consultorioConUsuario("auth-limite", "ana@ana.mx", "TITULAR");
			for (int i = 0; i < 5; i++) {
				login("auth-limite", "ana@ana.mx", "incorrecta-" + i).andExpect(status().isUnauthorized());
			}

			login("auth-limite", "ana@ana.mx", PASSWORD)
					.andExpect(status().isTooManyRequests())
					.andExpect(header().exists(HttpHeaders.RETRY_AFTER))
					.andExpect(jsonPath("$.title").value("Demasiados intentos"));
		}

		@Test
		void elLimiteDeUnCorreoNoBloqueaAOtro() throws Exception {
			UUID id = consultorioConUsuario("auth-limite-2", "ana@ana.mx", "TITULAR");
			crearUsuario(id, "beto@ana.mx", "COLABORADOR").andExpect(status().isCreated());
			for (int i = 0; i < 5; i++) {
				login("auth-limite-2", "ana@ana.mx", "incorrecta-" + i);
			}

			login("auth-limite-2", "beto@ana.mx", PASSWORD).andExpect(status().isOk());
		}

	}

	@Nested
	class AltaDeUsuarios {

		@Test
		void unCorreoRepetidoEnElMismoConsultorioDevuelve409() throws Exception {
			UUID id = consultorioConUsuario("auth-alta-1", "ana@ana.mx", "TITULAR");

			crearUsuario(id, "ANA@ana.mx", "COLABORADOR").andExpect(status().isConflict());
		}

		@Test
		void elMismoCorreoPuedeExistirEnDosConsultorios() throws Exception {
			consultorioConUsuario("auth-alta-2", "ana@ana.mx", "TITULAR");

			consultorioConUsuario("auth-alta-3", "ana@ana.mx", "TITULAR");
		}

		@Test
		void unConsultorioInexistenteDevuelve404() throws Exception {
			crearUsuario(UUID.randomUUID(), "ana@ana.mx", "TITULAR").andExpect(status().isNotFound());
		}

		@Test
		void laRespuestaNoIncluyeElHash() throws Exception {
			UUID id = consultorioConUsuario("auth-alta-4", "ana@ana.mx", "TITULAR");

			crearUsuario(id, "beto@ana.mx", "ASISTENTE")
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.passwordHash").doesNotExist())
					.andExpect(content().string(not(containsString("bcrypt"))));
		}

	}

	// --- SQL directo dentro de un consultorio: el mismo mecanismo que la app (contexto + transacción) ---

	private void jdbcComo(UUID tenantId, String sql) {
		TenantContext.ejecutarComo(tenantId, () -> tx.executeWithoutResult(estado -> jdbc.sql(sql).update()));
	}

	private String jdbcComoConsulta(UUID tenantId, String sql) {
		return TenantContext.ejecutarComo(tenantId, () -> tx.execute(estado -> jdbc.sql(sql).query(String.class).single()));
	}

}
