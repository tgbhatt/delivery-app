package com.bt.deliveryapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a delivery agent's operational profile.
 *
 * --- Why separate from User? ---
 * A User with role AGENT logs in and authenticates. But agents also have
 * operational data — are they currently available? How many deliveries
 * are they handling right now? That operational data belongs here in Agent,
 * not in User (which is just login/identity info).
 *
 * This is the OOP principle of SINGLE RESPONSIBILITY — each class has
 * one job. User handles identity, Agent handles delivery operations.
 *
 * --- Relationship ---
 * @OneToOne with User: each Agent profile belongs to exactly one User,
 * and each User can have at most one Agent profile.
 *
 * --- Used by ---
 * Feature 3 (Shared): route optimisation reads currentDeliveryCount and availability
 * Feature 4 (Bhavya): tracking shows which agent is handling an order
 * Feature 5 (Bhavya): dashboard shows agent workload side by side
 */
@Entity
@Table(name = "agents")
public class Agent {

    // ---- Primary Key ----
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- Link to the User account ----
    // One Agent profile per User. The foreign key column in the DB is "user_id".
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // ---- Is this agent available to take new deliveries right now? ----
    // Set to false when the agent is off duty or at maximum capacity
    @Column(nullable = false)
    private boolean available;

    // ---- How many active deliveries does this agent currently have? ----
    // Used by the dashboard and route optimisation to balance workloads.
    // "Active" means ASSIGNED or OUT_FOR_DELIVERY — not yet DELIVERED or FAILED.
    @Column(nullable = false)
    private int currentDeliveryCount;

    // ---- Which zone does this agent operate in? ----
    // e.g. "NORTH", "SOUTH", "CENTRAL", "EAST", "WEST"
    // Used by RouteOptimisationService to match agents to nearby orders.
    // Nullable — zone might not be assigned yet.
    @Column(length = 50)
    private String zone;

    // ---- When did this agent last update a delivery status? ----
    // Useful for the admin dashboard to spot idle agents
    private LocalDateTime lastActiveAt;

    // ---- Constructors ----
    public Agent() {
    }

    public Agent(User user) {
        this.user = user;
        this.available = true;
        this.currentDeliveryCount = 0;
        this.lastActiveAt = LocalDateTime.now();
    }

    // ---- Getters and Setters ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getCurrentDeliveryCount() {
        return currentDeliveryCount;
    }

    public void setCurrentDeliveryCount(int currentDeliveryCount) {
        this.currentDeliveryCount = currentDeliveryCount;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    @Override
    public String toString() {
        return "Agent{id=" + id + ", user=" + (user != null ? user.getName() : "null") +
               ", available=" + available +
               ", activeDeliveries=" + currentDeliveryCount + "}";
    }
}
