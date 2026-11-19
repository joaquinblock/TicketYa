package org.group10.common.security;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Del lado de quien llama: agrega el secreto compartido a las llamadas a {@code /internal/**} de otro servicio. A una
 * ruta pública no se lo manda.
 */
public class InternalSecretRequestInterceptor implements ClientHttpRequestInterceptor {

    private final String secret;

    public InternalSecretRequestInterceptor(String secret) {
        this.secret = secret;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String path = request.getURI().getPath();
        if (path != null && path.startsWith("/internal/")) {
            request.getHeaders().set(InternalSecretInterceptor.HEADER, secret);
        }
        return execution.execute(request, body);
    }
}
