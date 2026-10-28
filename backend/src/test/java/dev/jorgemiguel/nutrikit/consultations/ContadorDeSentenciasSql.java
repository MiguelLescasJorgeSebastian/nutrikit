package dev.jorgemiguel.nutrikit.consultations;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Cuenta las sentencias SQL que prepara el hilo actual, para demostrar que no hay N+1: el número de
 * consultas a la base no debe crecer con la cantidad de datos.
 *
 * <p>Envuelve el {@code DataSource} y cada conexión que entrega. Cuenta por hilo, para no mezclar lo
 * que hacen en paralelo los listeners asíncronos o las tareas programadas.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContadorDeSentenciasSql {

	private static final Set<String> PREPARAN_SENTENCIA = Set.of("prepareStatement", "createStatement", "prepareCall");
	private static final ThreadLocal<int[]> CONTEO = ThreadLocal.withInitial(() -> new int[1]);

	public static void reiniciar() {
		CONTEO.get()[0] = 0;
	}

	public static int sentencias() {
		return CONTEO.get()[0];
	}

	@Bean
	static BeanPostProcessor contarSentenciasSql() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String nombre) {
				return bean instanceof DataSource dataSource && !(bean instanceof Contador) ? new Contador(dataSource) : bean;
			}
		};
	}

	private static final class Contador extends DelegatingDataSource {

		Contador(DataSource destino) {
			super(destino);
		}

		@Override
		public Connection getConnection() throws SQLException {
			return contar(super.getConnection());
		}

		@Override
		public Connection getConnection(String usuario, String password) throws SQLException {
			return contar(super.getConnection(usuario, password));
		}

		private static Connection contar(Connection real) {
			return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
					(proxy, metodo, args) -> {
						if (PREPARAN_SENTENCIA.contains(metodo.getName())) {
							CONTEO.get()[0]++;
						}
						try {
							return metodo.invoke(real, args);
						}
						catch (InvocationTargetException e) {
							throw e.getCause();
						}
					});
		}

	}

}
