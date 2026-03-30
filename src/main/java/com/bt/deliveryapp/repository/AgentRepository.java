package com.bt.deliveryapp.repository;

import com.bt.deliveryapp.model.Agent;
import com.bt.deliveryapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Agent — handles all database reads and writes for agent profiles.
 *
 * Used by: Feature 3 (route optimisation), Feature 5 (dashboard),
 *          DataLoader (temp testing).
 *
 * --- This is a shared repository used across multiple features ---
 */
@Repository
public interface AgentRepository extends JpaRepository<Agent, Long> {

    // Find the Agent profile linked to a specific User account
    Optional<Agent> findByUser(User user);

    // Find all available agents (available = true means they can take new orders)
    List<Agent> findByAvailableTrue();
}
