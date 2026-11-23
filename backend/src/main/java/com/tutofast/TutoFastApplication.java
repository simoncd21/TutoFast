package com.tutofast;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TutoFastApplication {

    public static void main(String[] args) {
        SpringApplication.run(TutoFastApplication.class, args);
    }
}
