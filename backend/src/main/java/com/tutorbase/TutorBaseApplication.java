package com.tutorbase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TutorBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(TutorBaseApplication.class, args);
    }
}
