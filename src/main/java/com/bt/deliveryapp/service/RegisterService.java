package com.bt.deliveryapp.service;

import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * RegisterService — handles new customer account creation.
 *
 * --- Who can register? ---
 * Only CUSTOMERS self-register through this page.
 * Agent and Admin accounts are created differently (admin creates agents
 * via the admin panel; the first admin is seeded automatically by UserSeeder).
 *
 * --- What does this service validate? ---
 * 1. The email must not already be in use (no duplicate accounts)
 * 2. The two password fields must match
 * 3. If both checks pass, create and save the new User with role = CUSTOMER
 *
 * --- Why validate in the service and not the controller? ---
 * The service layer owns business rules. "Is this email taken?" is a business
 * rule. The controller just passes data in and gets a result back — it does
 * not decide what's valid or invalid.
 */
@Service
public class RegisterService {

    private final UserRepository userRepository;

    public RegisterService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Creates a new CUSTOMER account.
     *
     * @param name            full name entered on the registration form
     * @param email           email address (will be used to log in)
     * @param phone           phone number (optional — can be blank)
     * @param password        password entered on the form
     * @param confirmPassword the "confirm password" field — must match password
     * @return the newly created and saved User object
     * @throws RuntimeException if email already exists or passwords do not match
     */
    public User register(String name, String email, String phone,
                         String password, String confirmPassword) {

        // Rule 1: Email must not already be registered
        // existsByEmail() runs: SELECT COUNT(*) > 0 FROM users WHERE email = ?
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("An account with this email already exists.");
        }

        // Rule 2: Both password fields must be identical
        // The user types their password twice to prevent typos
        if (!password.equals(confirmPassword)) {
            throw new RuntimeException("Passwords do not match.");
        }

        // Rule 3: Password must not be blank
        if (password.isBlank()) {
            throw new RuntimeException("Password cannot be empty.");
        }

        // All checks passed — create the new user
        // Role is hardcoded to CUSTOMER — this form never creates agents or admins
        User newUser = new User(
                name.trim(),    // .trim() removes accidental leading/trailing spaces
                email.trim().toLowerCase(),  // lowercase so "User@Email.com" = "user@email.com"
                password,
                phone.isBlank() ? null : phone.trim(),  // store null if phone left empty
                UserRole.CUSTOMER
        );

        // Save to the database and return the saved entity (now has an ID assigned)
        return userRepository.save(newUser);
    }
}
