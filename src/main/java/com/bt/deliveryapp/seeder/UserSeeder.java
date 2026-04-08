package com.bt.deliveryapp.seeder;

import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * UserSeeder — automatically creates the default admin account on app startup.
 *
 * --- What is a CommandLineRunner? ---
 * CommandLineRunner is a Spring Boot interface. Any class that implements it
 * will have its run() method called automatically every time the application
 * starts. We used the same pattern in TimeSlotSeeder to create time slots.
 *
 * --- Why do we need this? ---
 * The ADMIN account cannot be created through the /register page (that's
 * customer-only). There is no "Create Admin" button anywhere in the UI.
 * So the first admin account must be created programmatically — by the code
 * itself — the very first time the app runs.
 *
 * --- Is this safe? ---
 * For a student project, yes. In production you would:
 *   1. Hash the password (using BCrypt)
 *   2. Read the credentials from environment variables, not hardcode them
 *   3. Only run the seeder in a controlled setup script, not every startup
 * For our demo, hardcoding is fine and expected.
 *
 * --- Admin credentials ---
 * Email:    admin@deliveriq.com
 * Password: admin123
 * These are the credentials you share with your professor for the demo.
 */
@Component  // @Component tells Spring to manage this class as a bean (like @Service)
public class UserSeeder implements CommandLineRunner {

    private final UserRepository userRepository;

    public UserSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * This method runs automatically every time the Spring Boot app starts.
     *
     * The logic is simple:
     * - Check if ANY admin user already exists in the database
     * - If yes: do nothing (don't create duplicates)
     * - If no: create the default admin account
     *
     * This "check first, then create" pattern is called an IDEMPOTENT operation.
     * Idempotent means: running it once or ten times gives the same result.
     * No duplicates, no errors, safe to run every startup.
     */
    @Override
    public void run(String... args) throws Exception {

        // Check if an admin account already exists
        // findByRole() returns a list — if the list is empty, no admins exist yet
        boolean adminExists = !userRepository.findByRole(UserRole.ADMIN).isEmpty();

        if (adminExists) {
            // Admin already created in a previous run — do nothing
            System.out.println("[UserSeeder] Admin account already exists. Skipping.");
            return;
        }

        // No admin found — create the default one now
        User admin = new User(
                "Admin",                  // name
                "admin@deliveriq.com",    // email  ← share this with your professor
                "admin123",               // password  ← share this with your professor
                null,                     // phone (not required for admin)
                UserRole.ADMIN            // role
        );

        userRepository.save(admin);

        System.out.println("[UserSeeder] Default admin account created.");
        System.out.println("[UserSeeder] Email: admin@deliveriq.com | Password: admin123");
    }
}
