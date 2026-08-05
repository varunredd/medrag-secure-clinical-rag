package com.medrag.api;

import com.medrag.api.config.MedRagProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MedRagProperties.class)
public class MedRagApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedRagApiApplication.class, args);
    }
}
