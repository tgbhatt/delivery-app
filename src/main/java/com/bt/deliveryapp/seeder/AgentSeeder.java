package com.bt.deliveryapp.seeder;

import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.Agent;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.AgentRepository;
import com.bt.deliveryapp.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AgentSeeder — automatically creates Agent profiles for any User with role AGENT.
 *
 * ─── Why do we need this? ────────────────────────────────────────────────────
 * In this app there are TWO separate tables:
 *   1. "users" table → stores login info (name, email, password, role)
 *   2. "agents" table → stores delivery profile info (is available, delivery count)
 *
 * When a user registers and is given the AGENT role, they get a row in "users".
 * But they do NOT automatically get a row in "agents" — someone has to create
 * that Agent profile too. Without it, the dashboard can't show their workload
 * and route optimisation can't assign orders to them.
 *
 * This seeder fixes that gap: every time the app starts, it checks if every
 * AGENT user has an Agent profile, and creates one if not.
 *
 * ─── What is a CommandLineRunner? ────────────────────────────────────────────
 * Any class that implements CommandLineRunner will have its run() method called
 * automatically when the Spring Boot application starts. This is perfect for
 * "setup on first run" code like database seeding.
 *
 * ─── What is @Order? ─────────────────────────────────────────────────────────
 * @Order(2) tells Spring to run this seeder AFTER UserSeeder (which runs at @Order(1)
 * by default). Agent profiles need User records to exist first — order matters.
 *
 * ─── Is this idempotent? ─────────────────────────────────────────────────────
 * Yes. For each AGENT user, we first check if an Agent profile already exists.
 * If it does, we skip it. If it doesn't, we create one.
 * Running this 100 times gives the same result as running it once.
 *
 * ─── OOP concept: Single Responsibility ─────────────────────────────────────
 * This class has one job: ensure Agent profiles exist for all AGENT users.
 * Nothing else. Business logic stays in the service, data access in repositories.
 */
@Component       // Makes Spring manage this class as a bean and auto-run it
@Order(2)        // Run AFTER UserSeeder — agents can't exist without users
public class AgentSeeder implements CommandLineRunner {

    // We need both repositories: UserRepository to find AGENT users,
    // AgentRepository to check if a profile exists and to save new ones.
    private final UserRepository userRepository;
    private final AgentRepository agentRepository;

    // Constructor injection — Spring passes both repos in automatically.
    // Using constructor injection (not @Autowired on fields) is the modern
    // recommended approach because it makes dependencies obvious and testable.
    public AgentSeeder(UserRepository userRepository, AgentRepository agentRepository) {
        this.userRepository = userRepository;
        this.agentRepository = agentRepository;
    }

    /**
     * Runs automatically on every app startup.
     *
     * Algorithm:
     * 1. Find all users in the database who have role = AGENT
     * 2. For each agent user, check: does an Agent profile already exist for them?
     *    - agentRepository.findByUser(user) returns Optional.empty() if no profile
     *    - Optional.isPresent() returns true if a profile already exists
     * 3. If no profile exists → create a new Agent object and save it
     * 4. If a profile already exists → do nothing (idempotent)
     *
     * @param args command-line arguments (we don't use these — Spring passes them in)
     */
    @Override
    public void run(String... args) throws Exception {

        // Step 1: Find every user who has the AGENT role in the users table
        // findByRole() runs: SELECT * FROM users WHERE role = 'AGENT'
        List<User> agentUsers = userRepository.findByRole(UserRole.AGENT);

        // Step 2: Loop through each agent user
        int profilesCreated = 0;

        for (User user : agentUsers) {

            // Step 3: Check if this user already has an Agent profile
            // findByUser() runs: SELECT * FROM agents WHERE user_id = ?
            // It returns Optional<Agent> — the box is empty if no profile exists.
            boolean profileExists = agentRepository.findByUser(user).isPresent();

            if (!profileExists) {
                // Step 4: No profile exists — create one now.
                //
                // The Agent(User user) constructor sets:
                //   available = true           → the agent starts as on-duty and ready
                //   currentDeliveryCount = 0   → no active deliveries yet
                //   lastActiveAt = now()       → their profile was just created
                Agent newAgentProfile = new Agent(user);

                // save() runs: INSERT INTO agents (user_id, available, current_delivery_count, ...)
                agentRepository.save(newAgentProfile);
                profilesCreated++;

                System.out.println("[AgentSeeder] Created Agent profile for: " + user.getName());
            }
        }

        // Log a summary so you can see what happened in the console when the app starts
        if (profilesCreated == 0) {
            System.out.println("[AgentSeeder] All agent profiles already exist — nothing to create.");
        } else {
            System.out.println("[AgentSeeder] Created " + profilesCreated + " new agent profile(s).");
        }
    }
}
