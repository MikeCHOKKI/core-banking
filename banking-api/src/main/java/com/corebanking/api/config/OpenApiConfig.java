package com.corebanking.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI coreBankingOpenAPI(@Value("${server.port:8080}") String port) {
        return new OpenAPI()
                .info(new Info()
                        .title("Core Banking API")
                        .description("API bancaire modulaire avec Event Sourcing, CQRS, Kafka et Redis. " +
                                "Gère les comptes, les virements ACID avec idempotence, " +
                                "et fournit un historique complet des transactions.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Core Banking Team")
                                .email("team@core-banking.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://core-banking.com/license")))
                .servers(List.of(
                        new Server().url("http://localhost:" + port).description("Développement local"),
                        new Server().url("https://api.core-banking.com").description("Production")
                ));
    }
}
