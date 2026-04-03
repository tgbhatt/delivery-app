package com.bt.deliveryapp.service;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.model.Agent;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.repository.AgentRepository;
import com.bt.deliveryapp.repository.DeliveryRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DashboardService — the business logic layer for Feature 5: Admin & Agent Dashboard.
 *
 * --- What does this service do? ---
 * It assembles everything the dashboard pages need:
 *   - For admins: live order feeds, scheduled queues, summary counts, agent lists, filters
 *   - For agents: their personal workload — what they're doing right now and their schedule
 *   - For both: the assign-agent operation that links an agent to an order
 *
 * --- Key OOP concept: AGGREGATION ---
 * This service AGGREGATES data from multiple repositories: DeliveryRequestRepository
 * and AgentRepository. Neither repository knows about the other — DashboardService
 * is the one that pulls data from both and combines it into a single, usable picture.
 * This is the classic Service Layer pattern: all cross-repository business logic lives
 * here, not scattered across controllers or repositories.
 *
 * --- Why not put this logic in the controller? ---
 * Controllers are supposed to be thin — receive a request, call a service, return a view.
 * If the controller was doing all this filtering, counting, and repository calls, it would
 * become hundreds of lines long and impossible to test or reuse. The service keeps it clean.
 *
 * --- This is Bhavya's service (Feature 5: Admin/Agent Dashboard) ---
 */
@Service
public class DashboardService {

    // Spring auto-injects both repositories — we never call "new"
    @Autowired
    private DeliveryRequestRepository deliveryRequestRepository;

    @Autowired
    private AgentRepository agentRepository;

    // =========================================================================
    // ADMIN DASHBOARD — order feed methods
    // =========================================================================

    /**
     * Returns all active IMMEDIATE (Order Now) orders.
     *
     * "Active" means not yet finished — status is not DELIVERED or FAILED.
     * This is the left panel of the admin dashboard: the live feed of
     * urgent, on-demand orders that need immediate attention.
     *
     * We use Java Streams to filter in memory — for a student project this
     * is clean and readable. A production system would push the filter
     * to the database with a @Query, but this approach makes the logic clear.
     */
    public List<DeliveryRequest> getLiveOrders() {
        return deliveryRequestRepository.findByIsImmediate(true)
                .stream()
                .filter(order -> !isTerminalStatus(order.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Returns all active SCHEDULED orders.
     *
     * "Active" means not yet finished — status is not DELIVERED or FAILED.
     * This is the right panel of the admin dashboard: the scheduled queue
     * of orders that have a time slot assigned.
     *
     * Sorted by time slot date so the earliest deliveries appear first.
     * Orders without a time slot (edge case) go to the end of the list.
     */
    public List<DeliveryRequest> getScheduledOrders() {
        return deliveryRequestRepository.findByIsImmediate(false)
                .stream()
                .filter(order -> !isTerminalStatus(order.getStatus()))
                .sorted(Comparator.comparing(order ->
                        order.getTimeSlot() != null
                                ? order.getTimeSlot().getSlotDate()
                                : LocalDate.MAX))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // ADMIN DASHBOARD — summary count methods
    // =========================================================================

    /**
     * Counts all orders that are currently active (not yet delivered or failed).
     * Shown as "Total Active" in the summary bar at the top of the admin dashboard.
     */
    public long countTotalActive() {
        return deliveryRequestRepository.findAll()
                .stream()
                .filter(order -> !isTerminalStatus(order.getStatus()))
                .count();
    }

    /**
     * Counts orders that reached DELIVERED status today.
     *
     * We check updatedAt against today's date — setStatus() in DeliveryRequest
     * always refreshes updatedAt, so the timestamp is reliable.
     * Shown as "Delivered Today" in the summary bar.
     */
    public long countDeliveredToday() {
        LocalDate today = LocalDate.now();
        return deliveryRequestRepository.findByStatus(DeliveryStatusEnum.DELIVERED)
                .stream()
                .filter(order -> order.getUpdatedAt() != null
                        && order.getUpdatedAt().toLocalDate().equals(today))
                .count();
    }

    /**
     * Counts orders that reached FAILED status today.
     * Shown as "Failed Today" in the summary bar.
     */
    public long countFailedToday() {
        LocalDate today = LocalDate.now();
        return deliveryRequestRepository.findByStatus(DeliveryStatusEnum.FAILED)
                .stream()
                .filter(order -> order.getUpdatedAt() != null
                        && order.getUpdatedAt().toLocalDate().equals(today))
                .count();
    }

    /**
     * Counts orders in PLACED status — these have no agent assigned yet.
     * Shown as "Pending Assignment" in the summary bar.
     * This tells the admin how many orders still need to be actioned.
     */
    public long countPendingAssignment() {
        return deliveryRequestRepository.findByStatus(DeliveryStatusEnum.PLACED).size();
    }

    // =========================================================================
    // ADMIN DASHBOARD — agent list methods
    // =========================================================================

    /**
     * Returns every agent in the system.
     * Used to populate the "filter by agent" dropdown and the "assign agent" form.
     */
    public List<Agent> getAllAgents() {
        return agentRepository.findAll();
    }

    /**
     * Returns only agents with available = true.
     * Used to populate the assign-agent dropdown — no point showing
     * agents who are off duty or at capacity.
     */
    public List<Agent> getAvailableAgents() {
        return agentRepository.findByAvailableTrue();
    }

    // =========================================================================
    // ADMIN DASHBOARD — filter method
    // =========================================================================

    /**
     * Filters all orders by any combination of status, order type, and agent.
     *
     * All three parameters are optional — passing null means "don't filter by this".
     * The Java Stream .filter() chain applies each check independently:
     *   - If status is null or empty, no status filtering happens
     *   - If isImmediate is null, both immediate and scheduled orders are included
     *   - If agentId is null, all agents (and unassigned orders) are included
     *
     * This is called when the admin submits the filter form on the dashboard.
     *
     * @param status     status name as a string (e.g. "PLACED") — null means no filter
     * @param isImmediate  true = immediate only, false = scheduled only, null = both
     * @param agentId    ID of the agent to filter by — null means all agents
     * @return           filtered list of orders matching all provided criteria
     */
    public List<DeliveryRequest> filterOrders(String status, Boolean isImmediate, Long agentId) {
        return deliveryRequestRepository.findAll()
                .stream()
                // Filter by status if one was provided
                .filter(order -> status == null || status.isEmpty()
                        || order.getStatus().name().equals(status))
                // Filter by immediate/scheduled if one was provided
                .filter(order -> isImmediate == null
                        || order.isImmediate() == isImmediate)
                // Filter by agent if one was provided
                // Also checks agent is not null before accessing its ID (null-safe)
                .filter(order -> agentId == null
                        || (order.getAgent() != null
                            && order.getAgent().getId().equals(agentId)))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // AGENT DASHBOARD — personal workload methods
    // =========================================================================

    /**
     * Returns an agent's orders that are currently active — either ASSIGNED
     * (picked up and on the way to pick up food) or OUT_FOR_DELIVERY (food collected,
     * heading to customer). These are the deliveries the agent is handling right now.
     *
     * This is the "Right Now" section of the agent dashboard.
     */
    public List<DeliveryRequest> getAgentCurrentOrders(Agent agent) {
        return deliveryRequestRepository.findByAgent(agent)
                .stream()
                .filter(order -> order.getStatus() == DeliveryStatusEnum.ASSIGNED
                        || order.getStatus() == DeliveryStatusEnum.OUT_FOR_DELIVERY)
                .collect(Collectors.toList());
    }

    /**
     * Returns an agent's upcoming scheduled deliveries — orders that are in SCHEDULED
     * status and assigned to this agent, sorted by time slot date.
     *
     * This is the "Today's Schedule" section of the agent dashboard.
     * Sorted chronologically so the next delivery appears at the top.
     */
    public List<DeliveryRequest> getAgentScheduledOrders(Agent agent) {
        return deliveryRequestRepository.findByAgent(agent)
                .stream()
                .filter(order -> !order.isImmediate()
                        && order.getStatus() == DeliveryStatusEnum.SCHEDULED)
                .sorted(Comparator.comparing(order ->
                        order.getTimeSlot() != null
                                ? order.getTimeSlot().getSlotDate()
                                : LocalDate.MAX))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // ADMIN DASHBOARD — assign agent to order
    // =========================================================================

    /**
     * Assigns a delivery agent to an order.
     *
     * This is called when the admin clicks "Assign" next to an unassigned order.
     *
     * What happens step by step:
     *   1. Fetch the order and agent from the database
     *   2. Set the agent field on the order
     *   3. If the order is still in PLACED status, advance it to ASSIGNED
     *      (the agent has been told about it — it's no longer "just placed")
     *   4. Increment the agent's currentDeliveryCount so the dashboard
     *      knows their workload went up
     *   5. Save both the order and the agent back to the database
     *
     * @param orderId   the ID of the order to assign
     * @param agentId   the ID of the agent to assign to it
     * @throws IllegalArgumentException if the order or agent ID does not exist
     */
    public void assignAgentToOrder(Long orderId, Long agentId) {
        // Fetch the order — throw a clear error if not found
        DeliveryRequest order = deliveryRequestRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order #" + orderId + " not found."));

        // Fetch the agent — throw a clear error if not found
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Agent #" + agentId + " not found."));

        // Link the agent to the order
        order.setAgent(agent);

        // Advance status from PLACED → ASSIGNED now that an agent has been assigned
        // Only do this if the order is still in PLACED — don't touch orders already
        // further along the state machine (e.g. SCHEDULED orders stay SCHEDULED)
        if (order.getStatus() == DeliveryStatusEnum.PLACED) {
            order.setStatus(DeliveryStatusEnum.ASSIGNED);
            // Bump the agent's active delivery count
            agent.setCurrentDeliveryCount(agent.getCurrentDeliveryCount() + 1);
            agentRepository.save(agent);
        }

        // Persist the updated order
        deliveryRequestRepository.save(order);
    }

    // =========================================================================
    // Helper method
    // =========================================================================

    /**
     * Returns true if the given status is a terminal state — i.e. the delivery
     * is complete and no further action is needed.
     *
     * Used by getLiveOrders() and getScheduledOrders() to exclude finished deliveries
     * from the active feed. DELIVERED and FAILED are terminal.
     * RESCHEDULED is NOT terminal — the order is still in play.
     */
    private boolean isTerminalStatus(DeliveryStatusEnum status) {
        return status == DeliveryStatusEnum.DELIVERED
                || status == DeliveryStatusEnum.FAILED;
    }
}
