package com.bt.deliveryapp.seeder;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * AgentSeeder — disabled.
 * Agent accounts are created by the Admin through the admin dashboard.
 */
@Component
public class AgentSeeder implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        // No-op: agents are created by the admin through the UI, not auto-seeded.
    }
}
