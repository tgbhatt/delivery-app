package com.bt.deliveryapp.service;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.model.Agent;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.repository.AgentRepository;
import com.bt.deliveryapp.repository.DeliveryRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RouteOptimisationService — Feature 3: Smart Agent Assignment.
 *
 * ─── What does this feature do? ─────────────────────────────────────────────
 * Once orders have been placed and time slots assigned, somebody needs to
 * decide WHICH delivery agent picks up WHICH order. That is this service's job.
 *
 * Think of it like a taxi dispatch centre:
 * - They see all the unassigned ride requests
 * - They look at which drivers are available and how busy they are
 * - They use logic to match each request to the most suitable driver
 *
 * ─── Why "Route Optimisation"? ───────────────────────────────────────────────
 * We are not randomly assigning agents. We try to OPTIMISE (make more efficient)
 * the deliveries. The smarter the assignment, the fewer total kilometres agents
 * travel, and the faster customers receive their orders.
 *
 * ─── How this works with the Agent model ────────────────────────────────────
 * On this branch, the app has two separate tables:
 *   users  → login identity (name, email, password, role)
 *   agents → delivery profile (available, currentDeliveryCount)
 *
 * DeliveryRequest.agent is of type Agent (not User).
 * So this service works with Agent objects from AgentRepository.
 * To get the agent's name for display, we call agent.getUser().getName().
 *
 * ─── The 4-day build plan (already complete) ─────────────────────────────────
 * Day 1: Data fetching — getOrdersAwaitingAssignment(), getAllAgents()
 * Day 2: Core algorithm — assignAgentToOrder(), runAutoAssignment()
 * Day 3: Workload management — getAgentWorkload(), getAllAgentsWorkload()
 * Day 4: Zone-based optimisation — suggestBestAgent(), getZoneStats()
 */
@Service
public class RouteOptimisationService {

    // ─── Dependencies ────────────────────────────────────────────────────────
    // Constructor injection — Spring passes these in automatically at startup.
    // Both are final, which means they can only be set here in the constructor.
    // "final" + constructor injection is the recommended modern Spring approach.

    private final DeliveryRequestRepository deliveryRequestRepository;
    private final AgentRepository agentRepository;

    public RouteOptimisationService(DeliveryRequestRepository deliveryRequestRepository,
                                    AgentRepository agentRepository) {
        this.deliveryRequestRepository = deliveryRequestRepository;
        this.agentRepository = agentRepository;
    }

    // =========================================================================
    // DAY 1: DATA FETCHING
    // Before we can assign anyone to anything, we need to know:
    //   (a) which orders still need an agent?
    //   (b) who are all the available agents?
    // =========================================================================

    /**
     * Returns all delivery orders that are waiting to be assigned to an agent.
     *
     * An order is "awaiting assignment" when its status is SCHEDULED.
     * Why SCHEDULED? Because PLACED means the order just came in and may
     * not have a time slot yet. SCHEDULED means a slot was confirmed — now
     * it makes sense to also decide WHO will carry out the delivery.
     *
     * @return List of DeliveryRequest objects with status = SCHEDULED
     */
    public List<DeliveryRequest> getOrdersAwaitingAssignment() {
        // Spring generates: SELECT * FROM delivery_requests WHERE status = 'SCHEDULED'
        return deliveryRequestRepository.findByStatus(DeliveryStatusEnum.SCHEDULED);
    }

    /**
     * Returns all Agent profiles in the system, sorted by ID.
     *
     * We use AgentRepository here (not UserRepository) because the
     * DeliveryRequest.agent field expects an Agent object, not a User.
     * Each Agent has a .getUser() method to access the linked User record.
     *
     * @return List of all Agent profiles
     */
    public List<Agent> getAllAgents() {
        // JpaRepository provides findAll() for free — no custom method needed
        // SELECT * FROM agents
        return agentRepository.findAll();
    }

    /**
     * Returns all agents that are currently marked as available.
     *
     * "Available" means the agent is on duty and open to receiving new orders.
     * Used as a convenience filter in auto-assignment — we prefer available agents.
     *
     * @return List of Agent objects where available = true
     */
    public List<Agent> getAvailableAgents() {
        // SELECT * FROM agents WHERE available = true
        return agentRepository.findByAvailableTrue();
    }

    /**
     * Returns agents in a specific zone.
     *
     * --- Note on zone tracking ---
     * In a full production app, agents would have a GPS location or a
     * "current zone" field that updates as they move around the city.
     * This branch does not have that field, so this method returns ALL
     * available agents as a safe fallback.
     * The zone-based suggestion in suggestBestAgent() still works — it just
     * prefers available agents without the extra zone refinement.
     *
     * @param zone a zone name like "NORTH", "SOUTH", "EAST", "WEST", "CENTRAL"
     * @return list of agents (all available agents on this branch)
     */
    public List<Agent> getAgentsInZone(String zone) {
        // Filter available agents to only those whose zone matches the requested zone.
        // zone.equalsIgnoreCase() handles case differences (e.g. "central" vs "CENTRAL").
        // If an agent has no zone set (null), they are skipped — only matched agents appear.
        // The fallback in suggestBestAgent() handles the case where no zone-matched agent is found.
        return agentRepository.findByAvailableTrue()
                .stream()
                .filter(agent -> zone.equalsIgnoreCase(agent.getZone()))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // DAY 2: CORE ASSIGNMENT ALGORITHM
    // Now that we can fetch data, we build the logic that acts on it.
    // =========================================================================

    /**
     * Manually assigns a specific Agent to a specific order.
     *
     * This is for the admin use case: the admin sees an unassigned order on
     * the dashboard, picks an agent from the dropdown, and confirms.
     * The system trusts the admin's choice — no algorithm here.
     *
     * State transition enforced: SCHEDULED → ASSIGNED
     * If the order is already ASSIGNED, DELIVERED, etc., the assignment is rejected.
     *
     * @Transactional: if anything goes wrong, the entire operation rolls back.
     * The order and agent records are never left half-updated.
     *
     * @param orderId  the ID of the order to assign (from delivery_requests table)
     * @param agentId  the ID of the Agent profile to assign (from agents table)
     * @return the updated DeliveryRequest with agent set and status = ASSIGNED
     * @throws RuntimeException if the order or agent is not found, or status is wrong
     */
    @Transactional
    public DeliveryRequest assignAgentToOrder(Long orderId, Long agentId) {

        // Step 1: Find the order — fail immediately if not found
        // orElseThrow() says "if the Optional is empty, throw this exception"
        DeliveryRequest order = deliveryRequestRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // Step 2: Find the Agent profile — this is the Agent row, not the User row
        // agentId here is the Agent table's primary key
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found: " + agentId));

        // Step 3: Check the order is in SCHEDULED status
        // Only SCHEDULED orders are ready to have an agent assigned.
        if (order.getStatus() != DeliveryStatusEnum.SCHEDULED) {
            throw new RuntimeException(
                "Order " + orderId + " is not in SCHEDULED status. " +
                "Current status: " + order.getStatus()
            );
        }

        // Step 4: Assign the agent and advance the status
        order.setAgent(agent);                          // link the Agent profile to this order
        order.setStatus(DeliveryStatusEnum.ASSIGNED);   // advance through the state machine

        // Step 5: Save and return
        // Spring Data JPA generates: UPDATE delivery_requests SET agent_id=?, status=? WHERE id=?
        return deliveryRequestRepository.save(order);
    }

    /**
     * Automatically assigns agents to ALL unassigned (SCHEDULED) orders at once.
     *
     * This is the GREEDY ALGORITHM — at each step, pick the locally best available
     * agent without looking ahead. It is not perfect, but it is fast and practical.
     *
     * How it works:
     * 1. Get all SCHEDULED orders with no agent yet
     * 2. Get all agents
     * 3. For each unassigned order: find the first agent with fewer than 3 active orders
     * 4. If found: assign them, advance status to ASSIGNED, save
     * 5. If not found: skip (the order stays SCHEDULED for the next run)
     *
     * The cap of 3 means an agent won't take a 4th order until another finishes.
     * This prevents any one agent from being overloaded.
     *
     * @return list of all orders that were successfully assigned in this run
     */
    @Transactional
    public List<DeliveryRequest> runAutoAssignment() {

        List<DeliveryRequest> unassignedOrders = getOrdersAwaitingAssignment();
        List<Agent> allAgents = getAllAgents();
        List<DeliveryRequest> assignedOrders = new ArrayList<>();

        for (DeliveryRequest order : unassignedOrders) {

            // findAvailableAgent() uses the default cap of 3
            Agent chosenAgent = findAvailableAgent(allAgents);

            if (chosenAgent == null) {
                continue;  // all agents are at max capacity — skip this order
            }

            order.setAgent(chosenAgent);
            order.setStatus(DeliveryStatusEnum.ASSIGNED);
            deliveryRequestRepository.save(order);
            assignedOrders.add(order);
        }

        return assignedOrders;
    }

    /**
     * Private helper: finds the first agent with fewer than 3 active (ASSIGNED) orders.
     *
     * "Private" means only this class can call it — it is an internal tool.
     * We check ASSIGNED orders because those are the ones actively in the agent's queue.
     * DELIVERED and FAILED orders are finished — they no longer count.
     *
     * @param agents the list of agents to search through
     * @return the first agent with capacity, or null if everyone is full
     */
    private Agent findAvailableAgent(List<Agent> agents) {
        final int MAX_ACTIVE_ORDERS = 3;

        for (Agent agent : agents) {
            // COUNT query — faster than loading all objects just to count them
            // SELECT COUNT(*) FROM delivery_requests WHERE agent_id = ? AND status = 'ASSIGNED'
            long activeCount = deliveryRequestRepository
                    .countByAgentAndStatus(agent, DeliveryStatusEnum.ASSIGNED);

            if (activeCount < MAX_ACTIVE_ORDERS) {
                return agent;  // first fit — greedy approach
            }
        }

        return null;  // every agent is full
    }

    // =========================================================================
    // DAY 3: WORKLOAD MANAGEMENT
    // Expose agent workload data and make the cap configurable.
    // =========================================================================

    /**
     * Returns how many ACTIVE (ASSIGNED) orders a specific agent currently has.
     *
     * Useful for an admin checking: "Is this agent too busy for another order?"
     * Uses a COUNT query (not loading full objects) for efficiency.
     *
     * @param agentId the database ID of the Agent profile
     * @return the number of currently ASSIGNED orders for this agent
     * @throws RuntimeException if no Agent with that ID exists
     */
    public long getAgentWorkload(Long agentId) {

        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("No agent found with ID: " + agentId));

        // SELECT COUNT(*) FROM delivery_requests WHERE agent_id = ? AND status = 'ASSIGNED'
        return deliveryRequestRepository.countByAgentAndStatus(agent, DeliveryStatusEnum.ASSIGNED);
    }

    /**
     * Returns a snapshot of every agent's current workload, sorted busiest-first.
     *
     * The result is a Map<String, Long>:
     *   Key   → the agent's name (fetched via agent.getUser().getName())
     *   Value → how many ASSIGNED orders they currently have
     *
     * Example:
     *   { "Priya Nair" → 3,  "Ravi Sharma" → 2,  "Arjun Mehta" → 0 }
     *
     * Why LinkedHashMap? A regular HashMap doesn't guarantee order.
     * LinkedHashMap preserves INSERTION ORDER — so after we sort by value
     * and insert into this map, the order stays exactly as we inserted it.
     *
     * @return Map of agent name → active order count, sorted busiest first
     */
    public Map<String, Long> getAllAgentsWorkload() {

        List<Agent> allAgents = getAllAgents();
        Map<String, Long> workloadMap = new LinkedHashMap<>();

        for (Agent agent : allAgents) {
            long activeCount = deliveryRequestRepository
                    .countByAgentAndStatus(agent, DeliveryStatusEnum.ASSIGNED);

            // agent.getUser().getName() gives us the human-readable name
            // (Agent itself has no name field — the name lives in the linked User)
            workloadMap.put(agent.getUser().getName(), activeCount);
        }

        // Sort by count descending — busiest agent first
        // Java Streams: take entries, sort by value (highest first), rebuild map
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
     * This is an improved version of runAutoAssignment():
     * instead of the hardcoded cap of 3, the caller passes in whatever cap they want.
     *
     * Example use cases:
     *   runAutoAssignmentWithCap(3)  → standard operation
     *   runAutoAssignmentWithCap(5)  → peak hours, allow higher load
     *   runAutoAssignmentWithCap(1)  → premium express mode, one order per agent
     *
     * @param maxOrdersPerAgent the maximum ASSIGNED orders an agent can have before being skipped
     * @return list of orders assigned in this run
     * @throws IllegalArgumentException if the cap is less than 1
     */
    @Transactional
    public List<DeliveryRequest> runAutoAssignmentWithCap(int maxOrdersPerAgent) {

        if (maxOrdersPerAgent < 1) {
            throw new IllegalArgumentException(
                "maxOrdersPerAgent must be at least 1, but got: " + maxOrdersPerAgent);
        }

        List<DeliveryRequest> unassignedOrders = getOrdersAwaitingAssignment();
        List<Agent> allAgents = getAllAgents();
        List<DeliveryRequest> assignedOrders = new ArrayList<>();

        for (DeliveryRequest order : unassignedOrders) {

            Agent chosenAgent = findAgentUnderCap(allAgents, maxOrdersPerAgent);

            if (chosenAgent == null) {
                continue;
            }

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
     * This is the Day 3 version of findAvailableAgent() — same idea, but the cap
     * comes from the parameter instead of being hardcoded to 3.
     * Used by both runAutoAssignmentWithCap() and suggestBestAgent().
     *
     * @param agents            list of agents to search through
     * @param maxOrdersPerAgent the cap — agents at or above this count are skipped
     * @return the first agent with capacity, or null if all are full
     */
    private Agent findAgentUnderCap(List<Agent> agents, int maxOrdersPerAgent) {
        // Instead of picking the FIRST agent under the cap (which always assigns
        // the same person), we pick the LEAST LOADED agent — the one with the
        // fewest active orders. This spreads deliveries evenly across all agents.
        //
        // Example: Agent A has 2 orders, Agent B has 0, Agent C has 1.
        // Old logic → always Agent A (first in list, under cap of 3).
        // New logic → Agent B (fewest orders = 0), then C (1), then A (2).

        Agent leastLoaded = null;
        long leastCount   = Long.MAX_VALUE;   // start with a very high number

        for (Agent agent : agents) {
            long activeCount = deliveryRequestRepository
                    .countByAgentAndStatus(agent, DeliveryStatusEnum.ASSIGNED);

            // Only consider agents who are still under the workload cap
            // AND who have fewer orders than the current best candidate
            if (activeCount < maxOrdersPerAgent && activeCount < leastCount) {
                leastLoaded = agent;
                leastCount  = activeCount;
            }
        }

        return leastLoaded;  // null if every agent is at or above the cap
    }

    // =========================================================================
    // BULK AUTO-ASSIGN: covers ALL unassigned orders regardless of status
    // Used by the "Run Auto-Assign" button on the admin dashboard to catch
    // any orders that were placed before auto-assignment was fully wired up.
    // =========================================================================

    /**
     * Assigns agents to every order that currently has no agent,
     * regardless of whether the order is PLACED or SCHEDULED.
     *
     * --- Why is this different from runAutoAssignment()? ---
     * runAutoAssignment() only looks at SCHEDULED orders. But immediate orders
     * start with PLACED status — they would never be picked up by that method.
     * This method uses stream().filter() to find ALL agent-less orders and
     * runs the same greedy assignment algorithm on each of them.
     *
     * --- What does "greedy" mean here? ---
     * At each step we just pick the first available agent under the workload cap.
     * It is "greedy" because it makes the locally best choice right now without
     * looking ahead. It is simple, fast, and good enough for our use case.
     *
     * @return how many orders were successfully assigned in this run
     */
    @Transactional
    public int runAutoAssignAll() {

        // Fetch every order in the system and filter to the ones with no agent
        // and a status that makes sense to assign (PLACED or SCHEDULED).
        // ASSIGNED/OUT_FOR_DELIVERY/DELIVERED/FAILED orders are skipped.
        List<DeliveryRequest> unassigned = deliveryRequestRepository.findAll()
                .stream()
                .filter(o -> o.getAgent() == null)
                .filter(o -> o.getStatus() == DeliveryStatusEnum.PLACED
                          || o.getStatus() == DeliveryStatusEnum.SCHEDULED)
                .collect(Collectors.toList());

        List<Agent> allAgents = getAllAgents();
        int assignedCount = 0;

        for (DeliveryRequest order : unassigned) {

            // First try zone-matched agent; fall back to any agent under cap
            String zone = order.getPickupZone();
            Agent chosen = null;

            if (zone != null && !zone.isBlank()) {
                List<Agent> zoneAgents = getAgentsInZone(zone);
                chosen = findAgentUnderCap(zoneAgents, 3);
            }

            if (chosen == null) {
                chosen = findAgentUnderCap(allAgents, 3);
            }

            if (chosen == null) {
                continue;  // all agents at capacity — skip this order
            }

            order.setAgent(chosen);
            // Only advance to ASSIGNED for immediate orders.
            // Scheduled orders keep SCHEDULED status — they have a future time slot
            // and shouldn't appear as "active right now" on the agent dashboard.
            if (order.isImmediate()) {
                order.setStatus(DeliveryStatusEnum.ASSIGNED);
            }
            deliveryRequestRepository.save(order);
            assignedCount++;
        }

        return assignedCount;
    }

    // =========================================================================
    // DAY 4: ZONE-BASED OPTIMISATION
    // Try to match orders to agents in the same pickup zone for efficiency.
    // =========================================================================

    /**
     * Suggests the best available agent for a given order using zone preference.
     *
     * The logic has two steps (Preference With Fallback pattern):
     *
     * STEP A — Zone match (preferred):
     *   If the order has a pickupZone set, look for available agents in that zone.
     *   On this branch, getAgentsInZone() returns all available agents (no zone
     *   field on Agent yet), so this effectively finds any available agent first.
     *
     * STEP B — Any available agent (fallback):
     *   If Step A found nobody, try all agents in the system.
     *
     * This method only SUGGESTS — it does not modify the database.
     * The actual assignment still goes through assignAgentToOrder().
     *
     * @param orderId the ID of the order that needs an agent
     * @return the best available Agent, or null if all agents are at capacity
     * @throws RuntimeException if the order does not exist
     */
    public Agent suggestBestAgent(Long orderId) {

        // Load the order — fail immediately if it doesn't exist
        DeliveryRequest order = deliveryRequestRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        String zone = order.getPickupZone();

        if (zone != null && !zone.isBlank()) {
            // Step A: Try agents in the preferred zone first
            List<Agent> zoneAgents = getAgentsInZone(zone);
            Agent zoneAgent = findAgentUnderCap(zoneAgents, 3);

            if (zoneAgent != null) {
                return zoneAgent;  // found a zone-matched agent with capacity
            }
        }

        // Step B: Fallback — search all agents regardless of zone
        List<Agent> allAgents = getAllAgents();
        return findAgentUnderCap(allAgents, 3);

        // Returns null if everyone is at capacity — caller must handle this case
    }

    /**
     * Returns a snapshot of how many SCHEDULED orders are waiting in each zone.
     *
     * Always returns all 5 zones (even if count = 0), so the admin can see the
     * complete picture: where are orders piling up and where is it quiet?
     *
     * Example output:
     *   { "NORTH" → 4, "SOUTH" → 1, "EAST" → 7, "WEST" → 2, "CENTRAL" → 0 }
     *
     * @return Map of zone name → count of SCHEDULED orders, all 5 zones always present
     */
    public Map<String, Long> getZoneStats() {

        String[] zones = { "NORTH", "SOUTH", "EAST", "WEST", "CENTRAL" };
        Map<String, Long> stats = new LinkedHashMap<>();

        for (String zone : zones) {
            // SELECT COUNT(*) FROM delivery_requests WHERE status = 'SCHEDULED' AND pickup_zone = ?
            long count = deliveryRequestRepository
                    .countByStatusAndPickupZone(DeliveryStatusEnum.SCHEDULED, zone);

            stats.put(zone, count);
        }

        return stats;
    }
}
