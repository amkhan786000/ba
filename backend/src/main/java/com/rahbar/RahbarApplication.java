package com.rahbar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // daily payment reminders (PaymentReminderService)
public class RahbarApplication {
    public static void main(String[] args) {
        SpringApplication.run(RahbarApplication.class, args);
    }
}
