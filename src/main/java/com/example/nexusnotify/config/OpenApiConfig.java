package com.example.nexusnotify.config;

import com.example.nexusnotify.admin.AdminKeyFilter;
import com.example.nexusnotify.exception.GlobalExceptionHandler.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        Components components = new Components();
        ModelConverters.getInstance().read(ApiError.class).forEach(components::addSchemas);
        return new OpenAPI()
                .servers(List.of(new Server().url("/")))
                .info(new Info()
                        .title("NexUs Notify API")
                        .version("1.0.0")
                        .description("Histórico de notificações e instalações push. "
                                + "O perfil é obtido do JWT; o cliente não escolhe outro destinatário. "
                                + "O token é validado localmente até expirar, sem revogação imediata pelo logout."))
                .components(components
                        .addSecuritySchemes("AdminKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                                .name(AdminKeyFilter.HEADER))
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"));
    }

    @Bean
    public OperationCustomizer documentNotificationContract() {
        return (operation, handler) -> {
            if (handler.getBeanType().getPackageName().endsWith(".admin")) {
                if (operation.getParameters() != null) {
                    operation.getParameters().forEach(parameter -> parameter.setDescription(
                            "Janela em minutos, limitada à retenção configurada (720 por padrão)."));
                }
                operation.getResponses().addApiResponse("401",
                        new ApiResponse().description("Chave administrativa ausente ou inválida"));
                operation.getResponses().addApiResponse("503",
                        new ApiResponse().description("Chave administrativa não configurada"));
                return operation;
            }
            if (!handler.getBeanType().getPackageName().endsWith(".controller")) {
                return operation;
            }
            if (operation.getParameters() != null) {
                operation.getParameters().forEach(parameter -> parameter.setDescription(switch (parameter.getName()) {
                    case "page" -> "Índice da página, começando em zero.";
                    case "size" -> "Quantidade por página, entre 1 e 100. O padrão é 20.";
                    case "id" -> "Identificador MongoDB da instalação, com 24 caracteres hexadecimais.";
                    default -> parameter.getDescription();
                }));
            }
            operation.getResponses().addApiResponse("400", new ApiResponse().description("Entrada inválida")
                    .content(new Content().addMediaType("application/json",
                            new io.swagger.v3.oas.models.media.MediaType()
                                    .schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
            operation.getResponses().addApiResponse("401",
                    new ApiResponse().description("JWT ausente, inválido ou expirado; o corpo pode estar vazio"));
            operation.getResponses().addApiResponse("403",
                    new ApiResponse().description("Acesso não permitido ao recurso"));
            operation.getResponses().addApiResponse("429",
                    new ApiResponse().description("Limite atingido; aguarde os segundos de Retry-After"));
            operation.getResponses().addApiResponse("500",
                    new ApiResponse().description("Falha interna de persistência ou processamento"));
            return operation;
        };
    }
}
