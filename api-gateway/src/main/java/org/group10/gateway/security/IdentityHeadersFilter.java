package org.group10.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.security.GatewayHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Convierte el JWT de TicketYa en los headers de identidad que leen los servicios (ADR-003).
 * <ul>
 *   <li>Siempre borra los {@code X-User-*} y {@code X-Organization-Id} que mande el cliente: solo el gateway los pone.</li>
 *   <li>Sin {@code Authorization: Bearer}, el request pasa sin identidad; cada servicio decide si el endpoint es
 *       público.</li>
 *   <li>Con un token inválido (firma, vencimiento, {@code iss} o {@code aud}) responde {@code 401 unauthorized}.</li>
 *   <li>Si no puede obtener la clave pública del auth-service, responde {@code 503 service_unavailable}: el token puede
 *       ser válido.</li>
 * </ul>
 */
public class IdentityHeadersFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdentityHeadersFilter.class);
    /** Sin espacio: un cliente puede mandar {@code "Bearer"} solo, y eso también es un token inválido. */
    private static final String BEARER = "Bearer";
    private static final Set<String> IDENTITY_HEADERS = Set.of(
            GatewayHeaders.USER_ID.toLowerCase(Locale.ROOT),
            GatewayHeaders.ROLE.toLowerCase(Locale.ROOT),
            GatewayHeaders.PERMISSIONS.toLowerCase(Locale.ROOT),
            GatewayHeaders.ORGANIZATION_ID.toLowerCase(Locale.ROOT));

    private final JwtDecoder jwtDecoder;
    private final JsonMapper jsonMapper;

    public IdentityHeadersFilter(JwtDecoder jwtDecoder, JsonMapper jsonMapper) {
        this.jwtDecoder = jwtDecoder;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            chain.doFilter(new IdentityRequest(request, Map.of()), response);
            return;
        }
        String token = authorization.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            writeError(response, CommonErrorCode.UNAUTHORIZED, "Falta el token después de 'Bearer'");
            return;
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (BadJwtException e) {
            writeError(response, CommonErrorCode.UNAUTHORIZED, "El token es inválido o está vencido");
            return;
        } catch (JwtException e) {
            log.warn("No se pudo validar el token: {}", e.getMessage());
            writeError(response, CommonErrorCode.SERVICE_UNAVAILABLE,
                    "No se pudo validar el token en este momento. Intentá de nuevo en unos segundos");
            return;
        }
        chain.doFilter(new IdentityRequest(request, identityOf(jwt)), response);
    }

    private static Map<String, String> identityOf(Jwt jwt) {
        Map<String, String> identity = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        identity.put(GatewayHeaders.USER_ID, jwt.getSubject());
        identity.put(GatewayHeaders.ROLE, jwt.getClaimAsString("role"));
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        identity.put(GatewayHeaders.PERMISSIONS, permissions == null ? "" : String.join(",", permissions));
        Object organizationId = jwt.getClaim("organizationId");
        if (organizationId != null) {
            identity.put(GatewayHeaders.ORGANIZATION_ID, organizationId.toString());
        }
        return identity;
    }

    private void writeError(HttpServletResponse response, CommonErrorCode code, String message) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), code.toErrorDto(message, null));
    }

    /**
     * El request sin los headers de identidad del cliente y con los que salen del token, si hay.
     */
    private static final class IdentityRequest extends HttpServletRequestWrapper {

        private final Map<String, String> identity;

        IdentityRequest(HttpServletRequest request, Map<String, String> identity) {
            super(request);
            this.identity = identity;
        }

        @Override
        public String getHeader(String name) {
            if (isIdentity(name)) {
                return identity.get(name);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (isIdentity(name)) {
                String value = identity.get(name);
                return value == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(value));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames()).stream()
                    .filter(name -> !isIdentity(name))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            names.addAll(identity.keySet());
            return Collections.enumeration(names);
        }

        private static boolean isIdentity(String name) {
            return name != null && IDENTITY_HEADERS.contains(name.toLowerCase(Locale.ROOT));
        }
    }
}
