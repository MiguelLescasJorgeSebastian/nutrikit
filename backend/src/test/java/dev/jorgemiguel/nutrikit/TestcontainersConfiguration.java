package dev.jorgemiguel.nutrikit;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Postgres real para las pruebas, con los mismos dos usuarios que en desarrollo y producción:
 * Flyway migra como el dueño del contenedor y la app se conecta como {@code nutrikit_app},
 * así Row-Level Security aplica igual que fuera de las pruebas.
 *
 * <p>No usa {@code @ServiceConnection} porque esta configuraría la app con el usuario dueño
 * (superusuario), que se salta RLS.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	public static final String USUARIO_APP = "nutrikit_app";

	@Bean
	public PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"))
				.withCopyFileToContainer(MountableFile.forHostPath("docker/postgres/init/01-rol-app.sql"),
						"/docker-entrypoint-initdb.d/01-rol-app.sql");
	}

	@Bean
	DynamicPropertyRegistrar postgresProperties(PostgreSQLContainer postgres) {
		return registry -> {
			registry.add("spring.datasource.url", postgres::getJdbcUrl);
			registry.add("spring.datasource.username", () -> USUARIO_APP);
			registry.add("spring.datasource.password", () -> USUARIO_APP);
			registry.add("spring.flyway.user", postgres::getUsername);
			registry.add("spring.flyway.password", postgres::getPassword);
			registry.add("nutrikit.seguridad.jwt.secreto", TestcontainersConfiguration::claveAleatoria);
			registry.add("nutrikit.cifrado.llaves-maestras", () -> "prueba:" + claveAleatoria());
		};
	}

	/** Clave aleatoria de 256 bits por contexto de pruebas (firma de JWT, llave maestra); nunca una fija en el repo. */
	private static String claveAleatoria() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}

}
