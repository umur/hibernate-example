package com.cinetrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class CineTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(CineTrackApplication.class, args);
    }
}
