package org.group10.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Exige el secreto compartido en las rutas {@code /internal/**}, que solo usan los servicios entre sí (US-PAY-14).
 * El api-gateway no las reenvía, pero la red sola no alcanza: quien llegue al puerto de un servicio no puede cobrar,
 * reintegrar ni cambiar una venta sin el secreto.
 * <p>
 * Corre en {@code preHandle}, antes de leer el body. Compara en tiempo constante, para no dar pistas del secreto por
 * cuánto tarda en responder.
 */
public class InternalSecretInterceptor implements HandlerInterceptor {

    /** Header con el secreto compartido entre los servicios. */
    public static final String HEADER = "X-Internal-Secret";

    private final byte[] secret;

    public InternalSecretInterceptor(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String received = request.getHeader(HEADER);
        if (received == null || !MessageDigest.isEqual(secret, received.getBytes(StandardCharsets.UTF_8))) {
            throw new TicketYaException(CommonErrorCode.UNAUTHORIZED,
                    "Esta ruta es solo para los servicios de TicketYa: falta la credencial de servicio o no es válida");
        }
        return true;
    }
}
