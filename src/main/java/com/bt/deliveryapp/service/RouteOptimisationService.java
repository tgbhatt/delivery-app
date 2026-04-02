package com.bt.deliveryapp.service;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.DeliveryRequestRepository;
import com.bt.deliveryapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    // DAY 2 METHODS: CORE ASSIGNMENT ALGORITHM
    // Now that we can FETCH the data (Day 1), we build the logic that ACTS on it.
    // Two methods today:
    //   assignAgentToOrder() — admin manually picks which agent gets which order
    //   runAutoAssignment()  — system automatically assigns all unassigned orders
    // =========================================================================

    /**
     * Manually assigns a specific agent to a specific order.
     *
     * This is for the admin use case: the admin looks at the dashboard, sees an
     * unassigned order, and manually selects an agent from a dropdown to handle it.
     * The system trusts the admin's choice — no algorithm involved here.
     *
     * State transition enforced here: SCHEDULED → ASSIGNED
     * If the order is not in SCHEDULED status, we reject the request. You cannot
     * assign an agent to an order that is already ASSIGNED, DELIVERED, etc.
     *
     * @Transactional means: both the order update AND the save happen together.
     * If anything goes wrong halfway through, the entire operation is rolled back.
     * The database is never left in a half-updated state.
     *
     * @param orderId  the ID of the delivery order to assign
     * @param agentId  the ID of the agent who will handle it
     * @return the updated DeliveryRequest with agent set and status = ASSIGNED
     */
    @Transactional
    public DeliveryRequest assignAgentToOrder(Long orderId, Long agentId) {

        // Step 1: Find the order — if it does not exist, throw an error immediately
        // findById() returns an Optional<DeliveryRequest>
        // .orElseThrow() says "if Optional is empty, throw this exception"
        DeliveryRequest order = deliveryRequestRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // Step 2: Find the agent — same pattern as above
        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found: " + agentId));

        // Step 3: Confirm the agent actually has the AGENT role
        // We do not want to accidentally assign a CUSTOMER user as a delivery agent
        if (agent.getRole() != UserRole.AGENT) {
            throw new RuntimeException("User " + agentId + " is not a delivery agent");
        }

        // Step 4: Confirm the order is in SCHEDULED status
        // Only SCHEDULED orders are ready to receive an agent.
        // PLACED means no time slot yet. ASSIGNED means already has an agent.
        if (order.getStatus() != DeliveryStatusEnum.SCHEDULED) {
            throw new RuntimeException(
                "Order " + orderId + " is not in SCHEDULED status. Current status: " + order.getStatus()
            );
        }

        // Step 5: Perform the assignment — update two fields on the order
        order.setAgent(agent);                          // link the agent to this order
        order.setStatus(DeliveryStatusEnum.ASSIGNED);   // advance the state machine

        // Step 6: Save and return the updated order
        // Spring Data JPA generates: UPDATE delivery_requests SET agent_id=?, status=? WHERE id=?
        return deliveryRequestRepository.save(order);
    }

    /**
     * Automatically assigns agents to ALL unassigned (SCHEDULED) orders at once.
     *
     * This is the GREEDY ALGORITHM — a classic computer science approach.
     * "Greedy" means: at each step, make the locally best decision available
     * right now, without looking ahead at future orders.
     *
     * How it works:
     * 1. Get the full list of unassigned orders (SCHEDULED, no agent yet)
     * 2. Get the full list of all agents
     * 3. Loop through every unassigned order one by one
     * 4. For each order, loop through agents and pick the first one who has
     *    fewer than 3 active orders (the max workload cap)
     * 5. Assign that agent, update the order status to ASSIGNED, save
     * 6. Move on to the next order
     *
     * Why is this "greedy"? Because we assign the first available agent we find,
     * rather than trying every possible combination to find the globally optimal
     * assignment. The greedy approach is much faster (O(n×m) instead of O(n!))
     * and good enough for a real-world delivery system where speed matters.
     *
     * Why 3 as the max? An agent with more than 3 active deliveries at once is
     * overloaded and delivery quality drops. This cap enforces fairness.
     *
     * @return a list of all orders that were successfully assigned this run
     */
    @Transactional
    public List<DeliveryRequest> runAutoAssignment() {

        // Step 1: Get all orders waiting for an agent
        List<DeliveryRequest> unassignedOrders = getOrdersAwaitingAssignment();

        // Step 2: Get all agents in the system
        List<User> allAgents = getAllAgents();

        // Step 3: This list will collect every order we successfully assign
        // We return it at the end so the caller knows what happened
        List<DeliveryRequest> assignedOrders = new java.util.ArrayList<>();

        // Step 4: The greedy loop — process one order at a time
        for (DeliveryRequest order : unassignedOrders) {

            // For this order, find the best available agent
            User chosenAgent = findAvailableAgent(allAgents);

            // If no agent is available (all are at max capacity), skip this order
            // It will remain SCHEDULED and be picked up in the next run
            if (chosenAgent == null) {
                continue;  // 'continue' skips the rest of this loop iteration
            }

            // Assign the chosen agent to this order
            order.setAgent(chosenAgent);
            order.setStatus(DeliveryStatusEnum.ASSIGNED);
            deliveryRequestRepository.save(order);

            // Record this assignment in our results list
            assignedOrders.add(order);
        }

        // Step 5: Return the list of all orders that got assigned this run
        return assignedOrders;
    }

    /**
     * Private helper: finds the first agent who has fewer than 3 active orders.
     *
     * "Private" means this method can only be called from inside this class.
     * It is an internal helper — the outside world does not need to know it exists.
     * Only runAutoAssignment() calls it.
     *
     * Why count ASSIGNED orders? Because ASSIGNED means "this agent currently has
     * this order in their active workload". Orders that are DELIVERED or FAILED
     * are finished — they no longer count towards the agent's workload.
     *
     * @param agents the list of all agents to search through
     * @return the first agent with capacity available, or null if all are full
     */
    private User findAvailableAgent(List<User> agents) {
        // MAX_ACTIVE_ORDERS: an agent should not carry more than 3 orders at once
        final int MAX_ACTIVE_ORDERS = 3;

        for (User agent : agents) {
            // Count how many orders this agent currently has in ASSIGNED status
            // findByAgentAndStatus() is already defined in DeliveryRequestRepository
            long activeCount = deliveryRequestRepository
                    .findByAgentAndStatus(agent, DeliveryStatusEnum.ASSIGNED)
                    .size();

            // If this agent has room, return them immediately (greedy: first fit)
            if (activeCount < MAX_ACTIVE_ORDERS) {
                return agent;
            }
        }

        // If we checked every agent and all are full, return null
        return null;
    }

    // =========================================================================
    // COMING IN FUTURE DAYS:
    //
    // April 2 → getAgentWorkload(Long agentId)
    // April 2 → runAutoAssignmentWithCap(int maxOrdersPerAgent)
    // April 3 → suggestBestAgent(Long orderId)
    // April 3 → getZoneStats()
    // =========================================================================
}
