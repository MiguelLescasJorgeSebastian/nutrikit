package dev.jorgemiguel.nutrikit.iam.infrastructure;

import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import dev.jorgemiguel.nutrikit.iam.application.ClaimsJwt;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Autoriza solo si el consultorio del token es el consultorio de la petición. Así un usuario de
 * {@code nutri-ana} no puede usar su token en {@code nutri-beto.nutrikit.mx}.
 *
 * <p>Funciona porque el filtro de resolución de consultorio corre antes que Spring Security y
 * {@link TenantContext} ya tiene valor al autorizar.
 */
class MismoConsultorio implements AuthorizationManager<RequestAuthorizationContext> {

	@Override
	public AuthorizationResult authorize(Supplier<? extends Authentication> autenticacion,
			RequestAuthorizationContext contexto) {
		if (!(autenticacion.get() instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
			return new AuthorizationDecision(false);
		}
		String delToken = token.getToken().getClaimAsString(ClaimsJwt.TENANT_ID);
		boolean coincide = delToken != null
				&& TenantContext.actual().map(id -> id.toString().equals(delToken)).orElse(false);
		return new AuthorizationDecision(coincide);
	}

}
