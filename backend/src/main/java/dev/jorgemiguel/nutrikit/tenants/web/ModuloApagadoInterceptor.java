package dev.jorgemiguel.nutrikit.tenants.web;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import dev.jorgemiguel.nutrikit.shared.TenantContext;
import dev.jorgemiguel.nutrikit.tenants.ModuloDelConsultorio;
import dev.jorgemiguel.nutrikit.tenants.application.ModulosEncendidos;

/**
 * Si el controlador destino pertenece a un módulo que el consultorio tiene apagado, responde 404
 * como si la ruta no existiera.
 *
 * <p>Es un {@link HandlerInterceptor} y no un filtro porque necesita saber qué controlador va a
 * atender la petición, y eso solo se conoce después de que Spring MVC resuelve la ruta. El módulo
 * se deduce del paquete del controlador ({@link ModuloDelConsultorio#deClase}).
 *
 * <p>Lanza la misma excepción que Spring MVC usa para una ruta inexistente, así la respuesta es
 * idéntica y no revela que el módulo existe.
 */
class ModuloApagadoInterceptor implements HandlerInterceptor {

	private final ModulosEncendidos encendidos;

	ModuloApagadoInterceptor(ModulosEncendidos encendidos) {
		this.encendidos = encendidos;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws NoResourceFoundException {
		if (!(handler instanceof HandlerMethod metodo)) {
			return true;
		}
		var modulo = ModuloDelConsultorio.deClase(metodo.getBeanType());
		var tenantId = TenantContext.actual();
		if (modulo.isEmpty() || modulo.get().nucleo() || tenantId.isEmpty()) {
			return true;
		}
		if (!encendido(tenantId.get(), modulo.get())) {
			String ruta = request.getRequestURI().substring(request.getContextPath().length());
			throw new NoResourceFoundException(HttpMethod.valueOf(request.getMethod()), ruta, ruta);
		}
		return true;
	}

	private boolean encendido(UUID tenantId, ModuloDelConsultorio modulo) {
		return encendidos.codigosEncendidos(tenantId).contains(modulo.codigo());
	}

}
