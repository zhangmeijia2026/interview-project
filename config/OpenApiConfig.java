package com.group5.interview.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc OpenAPI 配置：接口文档 http://localhost:8080/swagger-ui.html
 * 携带 Bearer 令牌的全局安全说明（受自定义 JwtAuthFilter 保护，此处仅为文档展示）。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI interviewOpenApi() {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("智能面试问答系统 API")
                        .description("基于 Spring Boot 与大语言模型的智能面试问答系统（5班-5组）")
                        .version("v0.1.0"))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName));
    }
}
