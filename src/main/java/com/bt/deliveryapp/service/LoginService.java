package com.bt.deliveryapp.service;

import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * LoginService — handles user authentication.
 *
 * --- What does "authentication" mean? ---
 * Authentication = confirming that a person is who they say they are.
 * In our app: "does this email exist in the database, and is the password correct?"
 *
 * --- Why is this in the Service layer and not the Controller? ---
 * The service layer contains business logic. "Is this password correct?" is
 * business logic — it's a rule the app enforces. The controller's job is only
 * to receive the web request and send a response. Keeping logic here makes the
 * code cleaner and easier to test or change later.
 *
 * --- What about Spring Security? ---
 * We are NOT using Spring Security. Spring Security is a large framework that
 * handles login, roles, and permissions automatically. For this project, we do
 * it manually using HttpSession so you can see exactly what is happening at
 * every step — no magic hidden from you.
 */
@Service
public class LoginService {

    // UserRepository is injected via constructor — so we can look up users by email
    private final UserRepository userRepository;

    public LoginService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Authenticates a user by email and password.
     *
     * Step by step:
     * 1. Look up the user by email in the database
     * 2. If no user found → throw an exception ("Invalid email or password")
     * 3. If user found but password doesn't match → throw an exception
     * 4. If both match → return the User object (login successful)
     *
     * Why do we say "Invalid email or password" instead of "Email not found"?
     * Security reason: if we say "Email not found", an attacker knows a valid
     * email doesn't exist and can keep trying others. A vague message reveals
     * less information. For a student project this is a nice detail to mention.
     *
     * Note on passwords: we are using plain String comparison (no hashing) as
     * specified for this project. In production apps passwords are always hashed
     * using BCrypt or similar — never stored as plain text.
     *
     * @param email    the email address entered on the login form
     * @param password the password entered on the login form
     * @return the matching User object if credentials are valid
     * @throws RuntimeException if email is not found or password is wrong
     */
    public User authenticate(String email, String password) {

        // Step 1: Find user by email
        // findByEmail returns Optional<User> — it might be empty if no match found
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        // Step 2: Check password matches
        // .equals() compares the two strings character by character
        if (!user.getPassword().equals(password)) {
            throw new RuntimeException("Invalid email or password");
        }

        // Step 3: Both checks passed — return the valid user
        return user;
    }
}
