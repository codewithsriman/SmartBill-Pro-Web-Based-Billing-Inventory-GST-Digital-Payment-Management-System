package com.smartbillpro.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SmartBillProApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartBillProApplication.class, args);
    }
}
