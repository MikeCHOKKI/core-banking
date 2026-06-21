package com.corebanking.api;

import com.corebanking.api.controller.LedgerController;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Configuration ultra-légère pour les tests de contrôleurs REST.
 *
 * Seulement les beans web (controllers + @ControllerAdvice) sont scannés.
 * Toute configuration JPA / DataSource est exclue.
 * Les services sont mockés via @MockitoBean dans chaque test.
 */
@Configuration
@EnableAutoConfiguration(
    exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    }
)
@ComponentScan(
    basePackages = "com.corebanking.api",
    useDefaultFilters = false,
    includeFilters = {
        @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = RestController.class),
        @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Controller.class),
        @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = RestControllerAdvice.class),
        @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = ControllerAdvice.class)
    },
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = LedgerController.class)
    }
)
public class TestMvcConfig {
}
