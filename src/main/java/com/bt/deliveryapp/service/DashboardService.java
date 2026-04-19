package com.bt.deliveryapp.service;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.Agent;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.model.TrackingEvent;
import com.bt.deliveryapp.repository.AgentRepository;
import com.bt.deliveryapp.repository.DeliveryRequestRepository;
import com.bt.deliveryapp.repository.TrackingEventRepository;
import com.bt.deliveryapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
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

    // Spring auto-injects all three repositories — we never call "new"
    @Autowired
    private DeliveryRequestRepository deliveryRequestRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrackingEventRepository trackingEventRepository;

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
    public List<DeliveryRequest> filterOrders(String status, Boolean isImmediate, Long agentId, String zone) {
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
                // Filter by zone if one was provided.
                // Zone is stored on the Agent entity, so we check:
                //   1. Agent is not null (order has been assigned)
                //   2. Agent has a zone set
                //   3. That zone matches the requested zone
                .filter(order -> zone == null || zone.isEmpty()
                        || (order.getAgent() != null
                            && zone.equals(order.getAgent().getZone())))
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
        LocalDate today = LocalDate.now();
        LocalTime now   = LocalTime.now();

        return deliveryRequestRepository.findByAgent(agent)
                .stream()
                .filter(order -> {

                    // --- Case 1: Immediate order being actively handled ---
                    // ASSIGNED means the agent has been told to go pick it up.
                    // OUT_FOR_DELIVERY means they are already on the way to the customer.
                    // ARRIVED means they are at the door waiting for OTP confirmation.
                    if (order.isImmediate()) {
                        return order.getStatus() == DeliveryStatusEnum.ASSIGNED
                                || order.getStatus() == DeliveryStatusEnum.OUT_FOR_DELIVERY
                                || order.getStatus() == DeliveryStatusEnum.ARRIVED;
                    }

                    // --- Case 2: Scheduled order whose time window is happening right now ---
                    // We check three things:
                    //   1. The order isn't already delivered/failed (not a terminal state)
                    //   2. The slot is for TODAY's date
                    //   3. The current clock time is inside the slot window (e.g. 11:00–13:00)
                    // If all three are true, this order belongs in "Right Now".
                    com.bt.deliveryapp.model.TimeSlot slot = order.getTimeSlot();
                    if (slot != null && !isTerminalStatus(order.getStatus())) {
                        boolean isToday        = today.equals(slot.getSlotDate());
                        boolean isWithinWindow = !now.isBefore(slot.getStartTime())
                                              && !now.isAfter(slot.getEndTime());
                        return isToday && isWithinWindow;
                    }

                    return false;
                })
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
        LocalDate today = LocalDate.now();
        LocalTime now   = LocalTime.now();

        return deliveryRequestRepository.findByAgent(agent)
                .stream()
                // Only non-immediate, non-terminal orders
                .filter(order -> !order.isImmediate()
                        && !isTerminalStatus(order.getStatus()))
                // Exclude orders currently showing in "Right Now" (slot is active right now)
                // so the same order never appears in both sections simultaneously.
                .filter(order -> {
                    com.bt.deliveryapp.model.TimeSlot slot = order.getTimeSlot();
                    if (slot == null) return true;   // no slot — keep in Upcoming
                    boolean isToday        = today.equals(slot.getSlotDate());
                    boolean isWithinWindow = !now.isBefore(slot.getStartTime())
                                          && !now.isAfter(slot.getEndTime());
                    // If the slot is active right now, it's in "Right Now" — exclude it here
                    return !(isToday && isWithinWindow);
                })
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
    // ADMIN DASHBOARD — create a new delivery agent
    // =========================================================================

    /**
     * Creates a new delivery agent account.
     *
     * This does TWO things in one go:
     *   1. Creates a User account (the login credentials — name, email, password, phone)
     *      with role set to AGENT
     *   2. Creates an Agent profile (the operational data — availability, zone)
     *      linked to that User via @OneToOne
     *
     * --- Why @Transactional? ---
     * We are saving TWO things: a User and an Agent. If one save succeeds but
     * the other fails, we would have a User without an Agent profile — broken data.
     * @Transactional makes it all-or-nothing: either BOTH are saved, or NEITHER.
     *
     * --- Why check for duplicate email? ---
     * Email is marked unique = true in the User table. If we try to save a duplicate,
     * MySQL would throw an ugly SQL error. Checking first gives a clean message.
     *
     * @param name     the agent's full name
     * @param email    their login email (must be unique)
     * @param password their login password
     * @param phone    their phone number
     * @param zone     which area they operate in (e.g. "NORTH", "SOUTH")
     * @return the created Agent object (with its User linked)
     * @throws IllegalArgumentException if the email is already taken or inputs are invalid
     */
    @Transactional
    public Agent createAgent(String name, String email, String password,
                             String phone, String zone) {

        // --- Validate inputs ---
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent name cannot be empty.");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty.");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }

        // --- Check for duplicate email ---
        if (userRepository.existsByEmail(email.trim())) {
            throw new IllegalArgumentException("An account with email '" + email + "' already exists.");
        }

        // --- Step 1: Create the User account with AGENT role ---
        User agentUser = new User(
                name.trim(),
                email.trim(),
                password,          // In a real app, this would be encrypted with BCrypt
                phone != null ? phone.trim() : null,
                UserRole.AGENT     // This user is a delivery agent
        );
        userRepository.save(agentUser);

        // --- Step 2: Create the Agent profile linked to this User ---
        Agent agent = new Agent(agentUser);     // Constructor sets available=true, count=0
        if (zone != null && !zone.trim().isEmpty()) {
            agent.setZone(zone.trim().toUpperCase());
        }
        agentRepository.save(agent);

        return agent;
    }

    // =========================================================================
    // ADMIN DASHBOARD — delete a delivery agent
    // =========================================================================

    /**
     * Deletes a delivery agent from the system.
     *
     * This is more involved than a simple delete because:
     *   1. We must NOT delete agents who have active orders — that would leave
     *      deliveries with no one responsible for them.
     *   2. Completed orders (DELIVERED / FAILED) still reference this agent in
     *      the delivery_requests table. Before deleting the Agent row, we must
     *      set those references to null — otherwise the database will refuse
     *      to delete (it protects referential integrity).
     *   3. We delete TWO rows: the Agent profile AND the linked User account.
     *      @Transactional ensures both succeed, or neither does.
     *
     * --- What is referential integrity? ---
     * The delivery_requests table has a column agent_id that points at the
     * agents table. The database enforces that you cannot delete an agent row
     * while other rows still point to it — like removing a contact from your
     * phone book while messages still reference their name. We "unlink" first.
     *
     * @param agentId  the database ID of the Agent profile to delete
     * @throws IllegalArgumentException if the agent doesn't exist or has active orders
     */
    @Transactional
    public void deleteAgent(Long agentId) {

        // Step 1: Find the agent — fail clearly if they don't exist
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Agent #" + agentId + " not found."));

        // Step 2: Check for active orders — block deletion if any exist
        // An "active" order is one that is not yet DELIVERED or FAILED.
        // If an agent has active orders, we refuse — the admin must reassign first.
        List<DeliveryRequest> agentOrders = deliveryRequestRepository.findByAgent(agent);
        boolean hasActiveOrders = agentOrders.stream()
                .anyMatch(order -> !isTerminalStatus(order.getStatus()));

        if (hasActiveOrders) {
            throw new IllegalArgumentException(
                "Cannot delete agent '" + agent.getUser().getName() + "' — "
                + "they have active orders. Please reassign or complete their "
                + "orders first before deleting this agent.");
        }

        // Step 3: Unlink the agent from completed (terminal) orders
        // These are DELIVERED or FAILED orders — history records.
        // We set agent = null on each so the DB FK constraint doesn't block deletion.
        for (DeliveryRequest order : agentOrders) {
            order.setAgent(null);
            deliveryRequestRepository.save(order);
        }

        // Step 4: Unlink tracking events that reference this user.
        // The tracking_events table has a column "updated_by_user_id" pointing to users.
        // If the agent ever updated a delivery status, those tracking events still reference
        // their User ID. We must set updatedBy = null BEFORE deleting the user, or the
        // database will block the deletion with a foreign key constraint error.
        // The tracking history is preserved — we just remove "who" made the update.
        User user = agent.getUser();
        List<TrackingEvent> userTrackingEvents = trackingEventRepository.findByUpdatedBy(user);
        for (TrackingEvent event : userTrackingEvents) {
            event.setUpdatedBy(null);
            trackingEventRepository.save(event);
        }

        // Step 5: Delete the Agent profile first, then the User account.
        // Order matters: Agent holds the foreign key to User, so Agent must go first.
        agentRepository.delete(agent);
        userRepository.delete(user);
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
