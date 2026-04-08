package com.bt.deliveryapp.service;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.DeliveryRequestRepository;
import com.bt.deliveryapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    // DAY 3 METHODS: WORKLOAD MANAGEMENT
    //
    // So far (Days 1 & 2) we can fetch data and run a basic greedy assignment.
    // But the cap of 3 was hardcoded inside a private helper — you couldn't
    // change it without editing the source code and redeploying the whole app.
    //
    // Today we fix that in two ways:
    //
    //   getAgentWorkload(agentId)         → look up ONE agent's active order count
    //   getAllAgentsWorkload()             → see every agent's load side-by-side
    //   runAutoAssignmentWithCap(maxCap)  → run auto-assignment with a configurable cap
    //
    // Why does this matter?
    // Imagine it's a busy Friday night. The manager wants to raise the cap to 5
    // so more orders get assigned. Or it's a quiet Monday and they lower it to 2
    // to guarantee fast deliveries. With a hardcoded cap, that would need a code
    // change. With a parameter, it's just passing in a different number.
    //
    // This is the principle of CONFIGURABILITY — making your code flexible by
    // accepting inputs instead of hardcoding values.
    // =========================================================================

    /**
     * Returns how many ACTIVE (ASSIGNED) orders a specific agent currently has.
     *
     * "Active" here means status = ASSIGNED, because that's what the agent still
     * needs to deliver. DELIVERED and FAILED orders are finished — they don't
     * add to the agent's current workload.
     *
     * This is useful for:
     * - An admin checking on a specific agent: "is Ravi overloaded?"
     * - Logging and monitoring: "which agent has the most active orders right now?"
     * - Before manually assigning: "can this agent take one more order?"
     *
     * Why use countByAgentAndStatus instead of findByAgentAndStatus().size()?
     * countByAgentAndStatus runs SQL's COUNT(*) — it just returns a number.
     * findByAgentAndStatus loads the entire list of DeliveryRequest objects into
     * memory, then .size() counts them. If an agent has 100 orders, that's
     * 100 objects loaded for no reason. COUNT(*) skips all that.
     * This difference is called EFFICIENCY — doing the same job with less work.
     *
     * @param agentId the database ID of the agent to look up
     * @return the number of ASSIGNED orders currently on this agent's plate
     * @throws RuntimeException if no user with that ID exists, or if the user is not an AGENT
     */
    public long getAgentWorkload(Long agentId) {

        // Step 1: Look up the user by ID
        // findById() returns an Optional<User> — a box that may or may not contain a user
        // orElseThrow() unwraps it, or throws an exception if it's empty
        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("No user found with ID: " + agentId));

        // Step 2: Confirm the user is actually an agent
        // We don't want to return a workload count for a CUSTOMER or ADMIN —
        // that would be a nonsensical result. Fail early with a clear message.
        if (agent.getRole() != UserRole.AGENT) {
            throw new RuntimeException(
                "User " + agentId + " is not an agent (role is " + agent.getRole() + ")");
        }

        // Step 3: Count and return their active (ASSIGNED) orders
        // This runs: SELECT COUNT(*) FROM delivery_requests WHERE agent_id = ? AND status = 'ASSIGNED'
        return deliveryRequestRepository.countByAgentAndStatus(agent, DeliveryStatusEnum.ASSIGNED);
    }

    /**
     * Returns a snapshot of EVERY agent's current workload — sorted from busiest to least busy.
     *
     * The result is a Map<String, Long>:
     *   Key   → the agent's name (e.g. "Ravi Sharma")
     *   Value → how many ASSIGNED orders they currently have (e.g. 2)
     *
     * Example output:
     *   { "Priya Nair" → 3,  "Ravi Sharma" → 2,  "Arjun Mehta" → 0 }
     *
     * Why return a Map instead of a List?
     * A Map lets you look up any agent by name in O(1) time.
     * A List would make you loop through it every time.
     * For a dashboard table (agent name | order count), a Map is perfect.
     *
     * Why LinkedHashMap specifically?
     * A regular HashMap does not guarantee any ordering — the entries could come
     * out in any random order each time. LinkedHashMap preserves the INSERTION ORDER,
     * which means the output stays sorted the way we put it in.
     *
     * @return Map of agent name → active order count, sorted busiest first
     */
    public Map<String, Long> getAllAgentsWorkload() {

        // Step 1: Get all agents, sorted A-Z by name (from Day 1)
        List<User> allAgents = getAllAgents();

        // Step 2: Build a map of name → active order count
        // We fill it with all agents first (unsorted by count),
        // then sort it before returning.
        Map<String, Long> workloadMap = new LinkedHashMap<>();

        for (User agent : allAgents) {
            // For each agent, ask the database: how many ASSIGNED orders do they have?
            long activeCount = deliveryRequestRepository
                    .countByAgentAndStatus(agent, DeliveryStatusEnum.ASSIGNED);

            // Put the name and count into the map
            workloadMap.put(agent.getName(), activeCount);
        }

        // Step 3: Sort the map by value (count) descending — busiest agent first
        // This uses Java streams, which is an advanced topic we won't go deep on now.
        // The key idea: we're saying "sort the entries by their value, highest first,
        // then put the result into a new LinkedHashMap so the order is preserved."
        Map<String, Long> sortedWorkloadMap = new LinkedHashMap<>();
        workloadMap.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> sortedWorkloadMap.put(entry.getKey(), entry.getValue()));

        return sortedWorkloadMap;
    }

    /**
     * Runs auto-assignment with a CONFIGURABLE workload cap.
     *
     * This is an improved version of runAutoAssignment() from Day 2.
     * The only difference is that the maximum orders per agent is passed in
     * as a parameter instead of being hardcoded to 3.
     *
     * Why is this better?
     * Before: the cap was buried inside findAvailableAgent() as a local constant.
     *         To change it you'd have to find the right line in the source code.
     * Now:    the caller decides the cap. You can run:
     *           runAutoAssignmentWithCap(3)  → standard operation
     *           runAutoAssignmentWithCap(5)  → peak hours, higher load allowed
     *           runAutoAssignmentWithCap(1)  → premium express mode, max one order each
     *
     * The difference between runAutoAssignment() and runAutoAssignmentWithCap():
     *   runAutoAssignment()           → always uses cap of 3 (hardcoded via findAvailableAgent)
     *   runAutoAssignmentWithCap(n)   → uses whatever cap you pass in
     *
     * The algorithm is identical to Day 2's greedy loop — we just use the parameter
     * instead of the hardcoded constant.
     *
     * @param maxOrdersPerAgent the maximum number of ASSIGNED orders an agent can hold
     *                          before they are considered full and skipped
     * @return list of orders that were successfully assigned this run
     * @throws IllegalArgumentException if maxOrdersPerAgent is less than 1
     */
    @Transactional
    public List<DeliveryRequest> runAutoAssignmentWithCap(int maxOrdersPerAgent) {

        // Guard clause: a cap below 1 makes no sense — every agent would be "full"
        // and no orders would ever get assigned. Catch this early with a clear message.
        if (maxOrdersPerAgent < 1) {
            throw new IllegalArgumentException(
                "maxOrdersPerAgent must be at least 1, but got: " + maxOrdersPerAgent);
        }

        // Step 1: Get all unassigned (SCHEDULED) orders
        List<DeliveryRequest> unassignedOrders = getOrdersAwaitingAssignment();

        // Step 2: Get all agents
        List<User> allAgents = getAllAgents();

        // Step 3: Collect successfully assigned orders to return at the end
        List<DeliveryRequest> assignedOrders = new java.util.ArrayList<>();

        // Step 4: Greedy loop — same structure as Day 2, but cap comes from parameter
        for (DeliveryRequest order : unassignedOrders) {

            // Find the first agent who has room under the configurable cap
            User chosenAgent = findAgentUnderCap(allAgents, maxOrdersPerAgent);

            // If all agents are at or above the cap, skip this order
            if (chosenAgent == null) {
                continue;
            }

            // Assign the agent and update the status
            order.setAgent(chosenAgent);
            order.setStatus(DeliveryStatusEnum.ASSIGNED);
            deliveryRequestRepository.save(order);
            assignedOrders.add(order);
        }

        return assignedOrders;
    }

    /**
     * Private helper: finds the first agent whose active order count is below the given cap.
     *
     * This is the Day 3 version of findAvailableAgent() from Day 2.
     * The key difference: instead of using the hardcoded constant MAX_ACTIVE_ORDERS = 3,
     * it accepts the cap as a parameter — making it reusable at any cap value.
     *
     * We use countByAgentAndStatus (the efficient COUNT query) instead of
     * findByAgentAndStatus().size() (which loads full objects just to count them).
     * This is the efficiency improvement from adding the new repository method today.
     *
     * @param agents           list of agents to search through
     * @param maxOrdersPerAgent the cap — agents at or above this count are skipped
     * @return the first agent with capacity, or null if all are full
     */
    private User findAgentUnderCap(List<User> agents, int maxOrdersPerAgent) {
        for (User agent : agents) {
            // Use the new COUNT query — no unnecessary object loading
            long activeCount = deliveryRequestRepository
                    .countByAgentAndStatus(agent, DeliveryStatusEnum.ASSIGNED);

            if (activeCount < maxOrdersPerAgent) {
                return agent;
            }
        }
        return null;
    }

    // =========================================================================
    // DAY 4 METHODS: ZONE-BASED OPTIMISATION
    //
    // This is the final layer of intelligence for Feature 3.
    //
    // Days 1-3 were "zone-blind" — we picked the first available agent regardless
    // of where in the city they are or where the order needs to be picked up from.
    // That works, but it's not efficient. If a NORTH zone order gets assigned to
    // a SOUTH zone agent, that agent has to cross the entire city just to pick up
    // the food. The customer waits longer and the agent wastes fuel.
    //
    // Day 4 fixes this with two methods:
    //
    //   suggestBestAgent(orderId)  → look at the order's pickup zone, try to find
    //                                an available agent already in that same zone,
    //                                fall back to any agent only if no zone match exists
    //
    //   getZoneStats()             → admin view: how many SCHEDULED orders are
    //                                currently waiting in each zone? Helps the admin
    //                                decide where to deploy agents.
    //
    // This is a classic software pattern called PREFERENCE WITH FALLBACK:
    //   Try the best option first → if it's not available, use a good-enough option.
    //   Never fail completely just because the ideal choice isn't there.
    // =========================================================================

    /**
     * Suggests the best available agent for a given order, using zone preference.
     *
     * The logic has two steps:
     *
     * STEP A — Zone match (preferred):
     *   If the order has a pickupZone set, get all agents currently in that zone,
     *   then find the first one who has room under the default cap of 3.
     *   If found → return them immediately.
     *
     * STEP B — Any available agent (fallback):
     *   If the order has no zone, or no zone-matched agent was available,
     *   fall through to the normal first-fit search across all agents.
     *   If found → return them.
     *
     * STEP C — Return null:
     *   If all agents everywhere are at capacity → return null.
     *   The caller must handle this case (no agent available right now).
     *
     * Why "suggest" and not "assign"?
     * This method only RECOMMENDS an agent — it doesn't modify any database records.
     * The actual assignment is still done by assignAgentToOrder().
     * Keeping suggestion and assignment separate means the admin can review the
     * suggestion before committing to it. Separating "decide" from "act" is good design.
     *
     * @param orderId the ID of the order that needs an agent
     * @return the best available User (agent), or null if no one is available
     * @throws RuntimeException if the order ID doesn't exist in the database
     */
    public User suggestBestAgent(Long orderId) {

        // Step 1: Load the order — fail immediately if it doesn't exist
        DeliveryRequest order = deliveryRequestRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // Step 2: Check if this order has a pickup zone set
        String zone = order.getPickupZone();

        if (zone != null && !zone.isBlank()) {

            // Step 3A: Try to find an agent already in the same zone
            // getAgentsInZone() was written on Day 1 — it queries agents by currentLocation
            List<User> zoneAgents = getAgentsInZone(zone);

            // findAgentUnderCap() was written on Day 3 — first agent under the given cap
            // We use the default cap of 3 here (same as the rest of the system)
            User zoneAgent = findAgentUnderCap(zoneAgents, 3);

            if (zoneAgent != null) {
                // Found a zone-matched agent with capacity — ideal result, return immediately
                return zoneAgent;
            }

            // No zone-matched agent had room — don't give up, fall through to Step 3B
        }

        // Step 3B: Fallback — search all agents regardless of zone
        // This runs when: (a) the order has no zone set, OR
        //                 (b) all agents in the preferred zone are full
        List<User> allAgents = getAllAgents();
        return findAgentUnderCap(allAgents, 3);

        // Note: findAgentUnderCap returns null if everyone is full.
        // The caller (e.g. a controller) must check for null and handle it.
    }

    /**
     * Returns a snapshot of how many SCHEDULED orders are waiting in each zone.
     *
     * The result is a Map<String, Long>:
     *   Key   → zone name (always all five: NORTH, SOUTH, EAST, WEST, CENTRAL)
     *   Value → number of SCHEDULED orders in that zone right now
     *
     * Example output:
     *   { "NORTH"   → 4,
     *     "SOUTH"   → 1,
     *     "EAST"    → 7,
     *     "WEST"    → 2,
     *     "CENTRAL" → 0 }
     *
     * Why is this useful?
     * An admin can look at this table and immediately see that EAST has 7 waiting
     * orders but CENTRAL has 0. They can then manually move agents from CENTRAL
     * to EAST to balance the workload before running auto-assignment.
     *
     * This is called operational visibility — giving decision-makers the data they
     * need to make smart choices quickly.
     *
     * Why are all five zones always in the output?
     * If we only returned zones that have orders, a zone with 0 orders would simply
     * be missing from the map. The admin would have to guess: "Is CENTRAL missing
     * because it has 0 orders, or because something went wrong?" Showing all five
     * zones explicitly — even with 0 — is clearer and safer.
     *
     * @return Map of zone name → count of SCHEDULED orders, all five zones always present
     */
    public Map<String, Long> getZoneStats() {

        // The five zones our city is divided into
        // These must match the values used in User.currentLocation and DeliveryRequest.pickupZone
        String[] zones = { "NORTH", "SOUTH", "EAST", "WEST", "CENTRAL" };

        // LinkedHashMap preserves insertion order — zones will always appear in the
        // same sequence above, not in some random order
        Map<String, Long> stats = new LinkedHashMap<>();

        for (String zone : zones) {
            // For each zone, count how many SCHEDULED orders have that pickup zone
            // Spring generates: SELECT COUNT(*) FROM delivery_requests
            //                   WHERE status = 'SCHEDULED' AND pickup_zone = ?
            long count = deliveryRequestRepository
                    .countByStatusAndPickupZone(DeliveryStatusEnum.SCHEDULED, zone);

            stats.put(zone, count);
        }

        return stats;
    }
}
