package com.corebanking.transfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = "com.corebanking")
@EnableJpaRepositories(basePackages = "com.corebanking")
@EntityScan(basePackages = "com.corebanking")
public class TransferApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransferApplication.class, args);
    }
}
