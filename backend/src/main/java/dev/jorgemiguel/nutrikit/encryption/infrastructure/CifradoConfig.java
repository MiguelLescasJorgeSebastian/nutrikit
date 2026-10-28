package dev.jorgemiguel.nutrikit.encryption.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Sin llave maestra configurada, la app no arranca. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CifradoProperties.class)
class CifradoConfig {

	@Bean
	LlavesMaestras llavesMaestras(CifradoProperties properties) {
		return LlavesMaestras.desde(properties);
	}

}
