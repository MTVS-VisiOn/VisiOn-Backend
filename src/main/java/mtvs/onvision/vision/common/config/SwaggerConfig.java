package mtvs.onvision.vision.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import mtvs.onvision.vision.common.swagger.SwaggerOrderTransformer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "OnVision",
                version = "0.0.1",
                description = "OnVision API 명세"
        )
)
@Profile("!prod")
public class SwaggerConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "Bearer Authentication",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                )
                );
    }

    @Bean
    public SwaggerIndexTransformer swaggerIndexTransformer(
            SwaggerUiConfigProperties a, SwaggerUiOAuthProperties b,
            SwaggerWelcomeCommon c, ObjectMapperProvider d) {
        return new SwaggerOrderTransformer(a, b, c, d);
    }

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder().group("user API").pathsToMatch("/api/users/**").build();
    }

    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder().group("auth API").pathsToMatch("/api/auth/**").build();
    }

    @Bean
    public GroupedOpenApi presenceApi() {
        return GroupedOpenApi.builder().group("presence API").pathsToMatch("/api/presence/**").build();
    }

    @Bean
    public GroupedOpenApi locationApi() {
        return GroupedOpenApi.builder().group("location API").pathsToMatch("/api/locations/**").build();
    }
}
