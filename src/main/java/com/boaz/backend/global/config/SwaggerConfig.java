package com.boaz.backend.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BOAZ 웹사이트 백엔드 API")
                        .description("BOAZ 웹사이트 백엔드 API 문서")
                        .version("v1.0.0"));
    }
}
