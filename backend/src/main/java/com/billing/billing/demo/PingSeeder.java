package com.billing.billing.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class PingSeeder implements CommandLineRunner {

    private final PingRepository pingRepository;

    // Spring passes in the repository automatically (constructor injection)
    public PingSeeder(PingRepository pingRepository) {
        this.pingRepository = pingRepository;
    }

    @Override
    public void run(String... args) {
        if (pingRepository.count() == 0) {
            pingRepository.save(new Ping("Hello from PostgreSQL"));
        }
    }
}