package com.bt.deliveryapp.service;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.DeliveryRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * DeliveryAgentService — the Business Logic Layer for the Agent side of Feature 1.
 *
 * ─── What does this class cover? ────────────────────────────────────────────
 * DeliveryBookingService handled the CUSTOMER side: placing and cancelling orders.
 * This class handles the AGENT side: viewing assigned orders and updating their status
 * as the delivery progresses through the real world.
 *
 * ─── The Delivery State Machine ─────────────────────────────────────────────
 * An order's status changes in a fixed sequence — this is called a State Machine.
 * Not every transition is allowed. The rules are:
 *
 *   PLACED / SCHEDULED
 *       ↓  (agent is assigned by the system — Feature 3 does this)
 *   ASSIGNED
 *       ↓  (agent physically picks up the food from the restaurant)
 *   OUT_FOR_DELIVERY
 *       ↓              ↘
 *   DELIVERED         FAILED
 *                       ↓
 *                   RESCHEDULED  (admin can reschedule a failed delivery)
 *
 * This class enforces those rules. An agent cannot mark something as DELIVERED
 * if it hasn't been picked up yet. This is the OOP principle of Encapsulation:
 * the rules live here, not scattered across the codebase.
 *
 * ─── Why is this a separate class from DeliveryBookingService? ───────────────
 * Single Responsibility Principle (SRP) — each class should do one thing well.
 * DeliveryBookingService = customer placing orders.
 * DeliveryAgentService   = agent managing deliveries.
 * Keeping them separate makes the code easier to read, test, and maintain.
 */
@Service
public class DeliveryAgentService {

    @Autowired
    private DeliveryRequestRepository deliveryRequestRepository;

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 1: Get all orders currently assigned to this agent
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all orders that have been assigned to a specific delivery agent.
     * Used to show the agent their active workload.
     *
     * --- What does "assigned" mean? ---
     * When Feature 3 (Route Optimisation) runs, it picks the nearest available
     * agent for each order and sets the order's status to ASSIGNED.
     * Until then, the order sits in PLACED or SCHEDULED.
     *
     * @param agent the logged-in delivery agent
     * @return list of orders assigned to this agent
     */
    public List<DeliveryRequest> getAssignedOrders(User agent) {
        // DeliveryRequest has an 'agent' field (a User with role AGENT).
        // This query finds all orders where agent_id = this agent's id
        // AND status is ASSIGNED (ready to pick up) or OUT_FOR_DELIVERY (already picked up).
        // We use the status-based query from our repository and filter by agent below.
        // For now we use findByStatus — Feature 3 will assign agents properly.
        // findByAgentOrderByCreatedAtDesc is defined in DeliveryRequestRepository.
        // Spring generates: SELECT * FROM delivery_requests WHERE agent_id = ? ORDER BY created_at DESC
        return deliveryRequestRepository.findByAgentOrderByCreatedAtDesc(agent);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 2: Agent picks up the food from the restaurant → OUT_FOR_DELIVERY
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Marks an order as picked up by the agent.
     * This moves the status from ASSIGNED → OUT_FOR_DELIVERY.
     *
     * --- State machine rule ---
     * This transition is only valid if the current status is ASSIGNED.
     * If the order is in any other state, we reject the update.
     *
     * @param orderId the order being picked up
     * @param agent   the agent claiming they picked it up (for ownership check)
     * @return the updated order, or empty if the transition was not allowed
     */
    @Transactional
    public Optional<DeliveryRequest> markPickedUp(Long orderId, User agent) {
        Optional<DeliveryRequest> orderOpt = deliveryRequestRepository.findById(orderId);

        if (orderOpt.isEmpty()) {
            return Optional.empty();
        }

        DeliveryRequest order = orderOpt.get();

        // Ownership check: only the assigned agent can mark their own order
        if (order.getAgent() == null || !order.getAgent().getId().equals(agent.getId())) {
            return Optional.empty();
        }

        // State machine check: can only pick up if currently ASSIGNED
        if (order.getStatus() != DeliveryStatusEnum.ASSIGNED) {
            return Optional.empty();
        }

        order.setStatus(DeliveryStatusEnum.OUT_FOR_DELIVERY);
        return Optional.of(deliveryRequestRepository.save(order));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 3: Agent delivers the order → DELIVERED
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Marks an order as successfully delivered.
     * This moves the status from OUT_FOR_DELIVERY → DELIVERED.
     *
     * --- Why must it be OUT_FOR_DELIVERY first? ---
     * A real delivery can only be "delivered" once the agent has physically
     * picked it up and is on the way. Skipping steps would be incorrect.
     * The state machine prevents that.
     *
     * @param orderId the order that was delivered
     * @param agent   the agent confirming delivery
     * @return the updated order, or empty if the transition was not allowed
     */
    @Transactional
    public Optional<DeliveryRequest> markDelivered(Long orderId, User agent) {
        Optional<DeliveryRequest> orderOpt = deliveryRequestRepository.findById(orderId);

        if (orderOpt.isEmpty()) {
            return Optional.empty();
        }

        DeliveryRequest order = orderOpt.get();

        // Ownership check
        if (order.getAgent() == null || !order.getAgent().getId().equals(agent.getId())) {
            return Optional.empty();
        }

        // State machine check: can only mark delivered if currently out for delivery
        if (order.getStatus() != DeliveryStatusEnum.OUT_FOR_DELIVERY) {
            return Optional.empty();
        }

        order.setStatus(DeliveryStatusEnum.DELIVERED);
        return Optional.of(deliveryRequestRepository.save(order));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 4: Agent marks delivery as failed → FAILED
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Marks an order as failed (e.g. customer not home, address wrong).
     * This moves the status from OUT_FOR_DELIVERY → FAILED.
     *
     * After this, an admin can reschedule the delivery (→ RESCHEDULED).
     * That rescheduling logic will be added in Feature 2 / admin features.
     *
     * @param orderId the order that could not be delivered
     * @param agent   the agent reporting the failure
     * @return the updated order, or empty if the transition was not allowed
     */
    @Transactional
    public Optional<DeliveryRequest> markFailed(Long orderId, User agent) {
        Optional<DeliveryRequest> orderOpt = deliveryRequestRepository.findById(orderId);

        if (orderOpt.isEmpty()) {
            return Optional.empty();
        }

        DeliveryRequest order = orderOpt.get();

        // Ownership check
        if (order.getAgent() == null || !order.getAgent().getId().equals(agent.getId())) {
            return Optional.empty();
        }

        // State machine check: can only fail from OUT_FOR_DELIVERY
        if (order.getStatus() != DeliveryStatusEnum.OUT_FOR_DELIVERY) {
            return Optional.empty();
        }

        order.setStatus(DeliveryStatusEnum.FAILED);
        return Optional.of(deliveryRequestRepository.save(order));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 5: Get completed deliveries for this agent (history)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all orders this agent has successfully delivered.
     * Used to show the agent their delivery history and stats.
     *
     * @param agent the logged-in agent
     * @return list of DELIVERED orders for this agent
     */
    public List<DeliveryRequest> getDeliveryHistory(User agent) {
        return deliveryRequestRepository.findByAgentAndStatus(agent, DeliveryStatusEnum.DELIVERED);
    }
}
