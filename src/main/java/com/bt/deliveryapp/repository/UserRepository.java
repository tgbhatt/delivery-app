package com.bt.deliveryapp.repository;

import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * The repository for the User table in MySQL.
 *
 * --- What is a Repository? ---
 * A Repository is your app's "gateway" to the database for a specific table.
 * It's the layer that handles saving, finding, updating, and deleting records.
 *
 * --- Why is this an interface, not a class? ---
 * Notice we wrote "interface" instead of "class". This is a Java feature.
 * An interface just declares WHAT methods exist — it doesn't implement them.
 * By extending JpaRepository<User, Long>, Spring Boot automatically generates
 * all the implementation code for us at runtime. You never have to write SQL.
 *
 * --- What does JpaRepository<User, Long> mean? ---
 * The first type (User) tells Spring: "this repository is for the User table"
 * The second type (Long) tells Spring: "the primary key (ID) is a Long number"
 * Spring then gives us methods like: save(), findById(), findAll(), deleteById()
 * all for FREE — no code needed from us.
 *
 * --- What are the custom methods below? ---
 * Spring Data JPA lets you define queries by just naming the method correctly.
 * "findByEmail" → generates: SELECT * FROM users WHERE email = ?
 * "findByRole"  → generates: SELECT * FROM users WHERE role = ?
 * Spring reads the method name and figures out the SQL automatically.
 * This is called "derived query methods" — one of Spring Boot's most powerful features.
 */
@Repository  // Tells Spring: this is a repository bean — manage it for us
public interface UserRepository extends JpaRepository<User, Long> {

    // Find a user by their email address
    // Returns Optional<User> — this is Java's way of saying "might return null, handle it safely"
    // Example: when checking login credentials, we look up the user by email
    Optional<User> findByEmail(String email);

    // Find all users who have a specific role
    // Example: finding all AGENT users to assign deliveries to them
    List<User> findByRole(UserRole role);

    // Check if an email address is already registered — useful for signup validation
    // Returns true/false (boolean) — Spring generates: SELECT COUNT(*) FROM users WHERE email = ?
    boolean existsByEmail(String email);
}
