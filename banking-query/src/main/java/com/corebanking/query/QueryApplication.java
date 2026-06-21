package com.corebanking.query;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.corebanking.query", "com.corebanking.common"})
@EntityScan(basePackages = {"com.corebanking.query.entity", "com.corebanking.common.idempotency"})
@EnableJpaRepositories(basePackages = {"com.corebanking.query.repository"})
public class QueryApplication {
    public static void main(String[] args) {
        SpringApplication.run(QueryApplication.class, args);
    }
}
