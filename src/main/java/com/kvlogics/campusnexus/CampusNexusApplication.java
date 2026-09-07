package com.kvlogics.campusnexus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CampusNexusApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusNexusApplication.class, args);
    }
}
