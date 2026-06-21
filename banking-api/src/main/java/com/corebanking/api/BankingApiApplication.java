package com.corebanking.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "com.corebanking.api",
        "com.corebanking.account",
        "com.corebanking.transfer",
        "com.corebanking.query",
        "com.corebanking.ledger",
        "com.corebanking.common"
})
@EntityScan(basePackages = {
        "com.corebanking.account.entity",
        "com.corebanking.query.entity",
        "com.corebanking.ledger.entity",
        "com.corebanking.common.idempotency"
})
@EnableJpaRepositories(basePackages = {
        "com.corebanking.account.repository",
        "com.corebanking.query.repository",
        "com.corebanking.ledger.repository"
})
public class BankingApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingApiApplication.class, args);
    }
}
