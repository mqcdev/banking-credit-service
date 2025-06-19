package com.nttdata.banking.credits;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

/**
 * Class MsCreditApplication Main.
 * Credit microservice class MsCreditApplication.
 */
@SpringBootApplication
@EnableEurekaClient
public class MsCreditApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsCreditApplication.class, args);
    }
}