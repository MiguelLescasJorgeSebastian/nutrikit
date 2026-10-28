package dev.jorgemiguel.nutrikit.tenants.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.jorgemiguel.nutrikit.tenants.application.ModulosEncendidos;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantRepository;

/**
 * Registra el filtro de resolución de consultorio y el interceptor de módulos apagados. Va en un {@code FilterRegistrationBean} y no
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
		// Antes de Spring Security: al autorizar, el consultorio de la petición ya está en
		// TenantContext y se puede comparar con el del JWT. El inicio de sesión también lo necesita
		// para buscar al usuario en su consultorio.
		registro.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10);
		return registro;
	}

	/**
	 * Registra el interceptor de módulos apagados. Se declara como {@code @Bean} aquí, y no con una
	 * clase que implemente {@code WebMvcConfigurer}, para que las pruebas {@code @WebMvcTest} no lo
	 * carguen sin la base de datos que necesita.
	 */
	@Bean
	WebMvcConfigurer interceptorDeModulosApagados(ModulosEncendidos encendidos) {
		return new WebMvcConfigurer() {
			@Override
			public void addInterceptors(InterceptorRegistry registro) {
				registro.addInterceptor(new ModuloApagadoInterceptor(encendidos)).addPathPatterns("/api/**");
			}
		};
	}

}
