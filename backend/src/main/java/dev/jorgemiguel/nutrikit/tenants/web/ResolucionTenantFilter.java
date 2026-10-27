package dev.jorgemiguel.nutrikit.tenants.web;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import dev.jorgemiguel.nutrikit.shared.ConsultorioNoDisponibleException;
import dev.jorgemiguel.nutrikit.shared.ConsultorioNoIdentificadoException;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Identifica el consultorio de cada petición a {@code /api/**} y ejecuta el resto de la petición
 * con ese consultorio en {@link TenantContext}. Las rutas de la plataforma
 * ({@code /api/operador/**}) no pertenecen a ningún consultorio.
 *
 * <p>Si no se puede identificar, responde con Problem Details y la petición no llega al
 * controlador. Como los filtros corren antes de Spring MVC, el error se delega al
 * {@link HandlerExceptionResolver} para que lo formatee el mismo {@code @RestControllerAdvice}
 * que el resto de la API.
 */
class ResolucionTenantFilter extends OncePerRequestFilter {

	static final String RUTA_PLATAFORMA = "/api/operador/";

	private final ResolutorDeTenant resolutor;
	private final HandlerExceptionResolver manejadorDeErrores;

	ResolucionTenantFilter(ResolutorDeTenant resolutor, HandlerExceptionResolver manejadorDeErrores) {
		this.resolutor = resolutor;
		this.manejadorDeErrores = manejadorDeErrores;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String ruta = request.getRequestURI().substring(request.getContextPath().length());
		return !ruta.startsWith("/api/") || ruta.startsWith(RUTA_PLATAFORMA);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		UUID tenantId;
		try {
			tenantId = resolutor.resolver(request);
		}
		catch (ConsultorioNoIdentificadoException | ConsultorioNoDisponibleException e) {
			if (manejadorDeErrores.resolveException(request, response, null, e) == null) {
				throw e;
			}
			return;
		}
		continuarComo(tenantId, request, response, chain);
	}

	private static void continuarComo(UUID tenantId, HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		try {
			TenantContext.ejecutarComo(tenantId, () -> {
				chain.doFilter(request, response);
				return null;
			});
		}
		catch (ServletException | IOException | RuntimeException e) {
			throw e;
		}
		catch (Exception e) {
			throw new ServletException(e);
		}
	}

}
