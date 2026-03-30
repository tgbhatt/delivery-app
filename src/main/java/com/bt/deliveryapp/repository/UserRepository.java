package com.bt.deliveryapp.repository;

import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for User — handles all database reads and writes for users.
 *
 * Used by: LoginController (to be built), DataLoader (temp testing),
 *          Feature 5 Dashboard (to list agents and customers).
 *
 * --- This is a shared repository used across multiple features ---
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Find a user by their email — used for login (email is the unique identifier)
    Optional<User> findByEmail(String email);

    // Find all users with a specific role — used by admin dashboard
    List<User> findByRole(UserRole role);
}
