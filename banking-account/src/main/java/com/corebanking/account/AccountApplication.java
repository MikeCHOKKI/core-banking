package com.corebanking.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.corebanking.account", "com.corebanking.common"})
@EntityScan(basePackages = {"com.corebanking.account.entity", "com.corebanking.common.idempotency"})
@EnableJpaRepositories(basePackages = {"com.corebanking.account.repository"})
public class AccountApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}
