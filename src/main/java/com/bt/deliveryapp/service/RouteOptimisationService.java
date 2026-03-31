package com.bt.deliveryapp.service;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.DeliveryRequestRepository;
import com.bt.deliveryapp.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Feature 3: Route Optimisation Service
 *
 * --- What does this feature do? ---
 * Once orders have been placed (Feature 1) and time slots have been assigned
 * (Feature 2), somebody needs to decide WHICH delivery agent picks up WHICH order.
 * That is this service's entire job.
 *
 * Think of it like a dispatcher at a courier company:
 * - They look at all the unassigned deliveries
 * - They look at all available agents and where each agent currently is
 * - They use logic to match each order to the most suitable agent
 *
 * --- Why is this called "Route Optimisation"? ---
 * We are not just randomly assigning agents. We are trying to OPTIMISE (make as
 * efficient as possible) the routes. The smarter the assignment, the fewer total
 * kilometres agents need to travel, the faster customers get their deliveries.
 *
 * --- What is the 4-day build plan? ---
 *
 * DAY 1 (March 31) — Foundation: THIS FILE
 *   → Set up the service class, inject dependencies, write the data-fetching methods:
 *     getOrdersAwaitingAssignment() and getAllAgents().
 *   → No algorithm yet — just "what data do we need?"
 *
 * DAY 2 (April 1) — Core Assignment Algorithm
 *   → assignAgentToOrder()     : manually assign one agent to one order
 *   → runAutoAssignment()      : greedy loop — go through all unassigned orders
 *                                and assign the first available agent to each
 *
 * DAY 3 (April 2) — Workload Management
 *   → getAgentWorkload()       : how many active orders does an agent currently have?
 *   → enforceMaxCap()          : don't assign to an agent who already has too many orders
 *
 * DAY 4 (April 3) — Zone-Based Optimisation
 *   → suggestBestAgent()       : pick the agent in the same zone as the pickup address
 *   → getZoneStats()           : admin view — how many orders per zone right now?
 *
 * --- Why build it in layers? ---
 * This is standard software engineering practice. You build:
 *   1. The "plumbing" (data access) first
 *   2. Basic functionality second
 *   3. Smarter logic on top of that
 *   4. Advanced features last
 * Each day's work builds directly on top of the previous day's — nothing is wasted.
 *
 * --- What is @Service? ---
 * This annotation tells Spring Boot: "this is a service class — create one instance
 * of it and keep it available throughout the entire application."
 * It also means other classes can @Autowired / inject this service wherever they need it.
 */
@Service
public class RouteOptimisationService {

    // ---- Dependencies (things this service needs to do its job) ----
    //
    // @Autowired (or constructor injection) tells Spring to inject these automatically.
    // We use constructor injection (shown below) — it is the recommended modern approach
    // because it makes dependencies explicit and makes testing easier.
    //
    // deliveryRequestRepository → so we can query the delivery_requests table
    // userRepository            → so we can query the users table (specifically for agents)

    private final DeliveryRequestRepository deliveryRequestRepository;
    private final UserRepository userRepository;

    // ---- Constructor Injection ----
    // Spring sees this constructor and automatically passes in the repositories.
    // The "final" keyword on the fields means they can only be set once (here, in the constructor).
    // This is called "immutability" — another good OOP practice.
    public RouteOptimisationService(DeliveryRequestRepository deliveryRequestRepository,
                                    UserRepository userRepository) {
        this.deliveryRequestRepository = deliveryRequestRepository;
        this.userRepository = userRepository;
    }

    // =========================================================================
    // DAY 1 METHODS: DATA FETCHING
    // These methods answer the question: "what data does our algorithm need?"
    // Before we can assign anyone to anything, we need to know:
    //   (a) which orders still need an agent?
    //   (b) who are all the agents in the system?
    // =========================================================================

    /**
     * Returns all delivery orders that are waiting to be assigned to an agent.
     *
     * An order is "awaiting assignment" when its status is SCHEDULED.
     * Why SCHEDULED and not PLACED? Because PLACED means the order just came in —
     * it might not have a time slot yet. SCHEDULED means a time slot was assigned
     * by Feature 2, so now we know WHEN the delivery should happen. Only at that
     * point does it make sense to also assign WHO will do the delivery.
     *
     * Think of it like a restaurant: you don't assign a waiter to a table
     * until the guests are seated (the slot is confirmed).
     *
     * @return List of DeliveryRequest objects with status = SCHEDULED
     */
    public List<DeliveryRequest> getOrdersAwaitingAssignment() {
        // findByStatus() is defined in DeliveryRequestRepository.
        // Spring generates: SELECT * FROM delivery_requests WHERE status = 'SCHEDULED'
        return deliveryRequestRepository.findByStatus(DeliveryStatusEnum.SCHEDULED);
    }

    /**
     * Returns all users in the system who have the AGENT role.
     *
     * Before we can decide which agent to assign, we need the full list of agents
     * to choose from. This method gives us that list, sorted alphabetically.
     *
     * Why alphabetically? It has no effect on the algorithm, but it makes logs
     * and admin views much easier to read during development and testing.
     *
     * @return List of User objects where role = AGENT, sorted A→Z by name
     */
    public List<User> getAllAgents() {
        // findByRoleOrderByNameAsc() is one of the new methods we just added to UserRepository.
        // Spring generates: SELECT * FROM users WHERE role = 'AGENT' ORDER BY name ASC
        return userRepository.findByRoleOrderByNameAsc(UserRole.AGENT);
    }

    /**
     * Returns all agents in a specific location zone.
     *
     * This is a helper that will be used heavily on Day 4 (zone-based optimisation).
     * We are defining it today because it has no dependencies on future algorithm code —
     * it is purely a data fetch.
     *
     * Example: getAgentsInZone("NORTH") returns every agent currently in the NORTH zone.
     * The algorithm can then prefer these agents for orders whose pickup address is
     * also in the NORTH zone.
     *
     * @param zone A zone name like "NORTH", "SOUTH", "EAST", "WEST", or "CENTRAL"
     * @return List of agents currently in that zone
     */
    public List<User> getAgentsInZone(String zone) {
        // findByRoleAndCurrentLocation() is the other new method we added to UserRepository.
        // Spring generates: SELECT * FROM users WHERE role = 'AGENT' AND current_location = ?
        return userRepository.findByRoleAndCurrentLocation(UserRole.AGENT, zone);
    }

    // =========================================================================
    // COMING IN FUTURE DAYS (stubs for reference — implementations added later):
    //
    // April 1 → assignAgentToOrder(Long orderId, Long agentId)
    // April 1 → runAutoAssignment()
    // April 2 → getAgentWorkload(Long agentId)
    // April 2 → runAutoAssignmentWithCap(int maxOrdersPerAgent)
    // April 3 → suggestBestAgent(Long orderId)
    // April 3 → getZoneStats()
    // =========================================================================
}
