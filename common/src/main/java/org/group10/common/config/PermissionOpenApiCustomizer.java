package org.group10.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.group10.common.security.Permission;
import org.group10.common.security.RequiresPermission;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Lleva {@link RequiresPermission} a Swagger, así la documentación no se separa del permiso que de verdad se exige:
 * declara el token ({@code bearerAuth}) y, en cada endpoint anotado, pone el candado, el permiso en la descripción y
 * las respuestas 401 y 403 si el controller no las documentó. Los endpoints sin la anotación quedan sin candado.
 * <p>
 * El token se pega en <i>Authorize</i> y viaja como {@code Authorization: Bearer}. Lo valida el api-gateway
 * (ADR-003): hay que probar desde el Swagger del gateway, no directo contra el servicio.
 */
final class PermissionOpenApiCustomizer implements OpenApiCustomizer, OperationCustomizer {

    static final String SCHEME = "bearerAuth";

    @Override
    public void customise(OpenAPI openApi) {
        // Relativo a la página: desde el Swagger del gateway (8080), "Try it out" pasa por el gateway, que valida el
        // token y pone los headers de identidad. Absoluto saldría el host del servicio, que ignora el token.
        openApi.setServers(List.of(new Server().url("/")));
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        openApi.getComponents().addSecuritySchemes(SCHEME, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT de TicketYa: con Google (`POST /api/auth/google`) o, en desarrollo, con "
                        + "`POST /api/auth/dev-login`."));
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        RequiresPermission required = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (required == null) {
            return operation;
        }
        String permissions = Arrays.stream(required.value())
                .map(Permission::name)
                .map(name -> "`" + name + "`")
                .collect(Collectors.joining(", "));
        String rule = required.value().length == 1
                ? "Requiere el permiso " + permissions + "."
                : "Requiere uno de estos permisos: " + permissions + ".";

        operation.addSecurityItem(new SecurityRequirement().addList(SCHEME));
        operation.setDescription(operation.getDescription() == null || operation.getDescription().isBlank()
                ? rule
                : operation.getDescription() + "\n\n" + rule);

        ApiResponses responses = operation.getResponses() == null ? new ApiResponses() : operation.getResponses();
        responses.putIfAbsent("401", new ApiResponse().description("unauthorized: sin token o con un token inválido"));
        responses.putIfAbsent("403", new ApiResponse().description("forbidden: sin " + permissions.replace("`", "")));
        operation.setResponses(responses);
        return operation;
    }
}
