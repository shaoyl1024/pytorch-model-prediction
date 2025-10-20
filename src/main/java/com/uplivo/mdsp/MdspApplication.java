package com.uplivo.mdsp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MdspApplication {
    public static void main(String[] args) {
        SpringApplication.run(MdspApplication.class, args);
    }
}