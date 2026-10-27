package dev.jorgemiguel.nutrikit.tenants.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;

import dev.jorgemiguel.nutrikit.tenants.domain.TenantRepository;

/**
 * Registra el filtro de resolución de consultorio. Va en un {@code FilterRegistrationBean} y no
 * como {@code @Component} para controlar el orden y para que las pruebas {@code @WebMvcTest}
 * no lo carguen sin necesitarlo.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TenancyProperties.class)
class TenancyWebConfig {

	@Bean
	FilterRegistrationBean<ResolucionTenantFilter> resolucionTenantFilter(TenantRepository repository,
			TenancyProperties properties,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver manejadorDeErrores) {
		var filtro = new ResolucionTenantFilter(new ResolutorDeTenant(repository, properties), manejadorDeErrores);
		var registro = new FilterRegistrationBean<>(filtro);
		registro.addUrlPatterns("/api/*");
		// Después de Spring Security: en #8 el JWT ya estará validado al resolver el consultorio.
		registro.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 10);
		return registro;
	}

}
