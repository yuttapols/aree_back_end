package com.roti5dao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Roti5DaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(Roti5DaoApplication.class, args);
    }
}
