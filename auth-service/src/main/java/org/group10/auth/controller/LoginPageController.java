package org.group10.auth.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Página mínima para entrar con Google mientras no hay front (RFC-001): muestra el botón de Google Sign-In, canjea el
 * ID token con {@code POST /api/auth/google} y muestra el JWT de TicketYa para copiarlo.
 * <p>
 * Solo con el perfil {@code dev}, como {@code dev-login}. Vive en el auth-service para que el canje sea del mismo
 * origen: Google tiene que tener {@code http://localhost:8081} como origen autorizado del client id.
 */
@RestController
@Profile("dev")
@Hidden
public class LoginPageController {

    private static final String MISSING_CLIENT_ID = """
            <!doctype html><html lang="es"><head><meta charset="utf-8"><title>TicketYa · Login con Google</title></head>
            <body style="font-family: system-ui, sans-serif; max-width: 640px; margin: 40px auto; padding: 0 16px">
            <h1>Falta GOOGLE_CLIENT_ID</h1>
            <p>Configurá la variable GOOGLE_CLIENT_ID con el client id de OAuth del proyecto de Google Cloud y reiniciá
            el auth-service. Mientras tanto, para probar con un usuario del seeder, usá POST /api/auth/dev-login.</p>
            </body></html>
            """;

    private final String page;

    public LoginPageController(@Value("${ticketya.google.client-id:}") String clientId) {
        this.page = clientId == null || clientId.isBlank()
                ? MISSING_CLIENT_ID
                : template().replace("{{CLIENT_ID}}", HtmlUtils.htmlEscape(clientId.strip()));
    }

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage() {
        return page;
    }

    private static String template() {
        try {
            return new ClassPathResource("login-page/login.html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se encontró la página de login", e);
        }
    }
}
