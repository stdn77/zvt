package com.zvit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZvitApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZvitApplication.class, args);
        System.out.println("🌟 ZVIT Backend Started!");
    }
}
