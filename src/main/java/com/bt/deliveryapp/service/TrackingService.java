package com.bt.deliveryapp.service;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.TrackingEvent;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.DeliveryRequestRepository;
import com.bt.deliveryapp.repository.TrackingEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * TrackingService — the core logic engine for Feature 4: Live Status Tracking.
 *
 * --- What does this class do? ---
 * It implements a STATE MACHINE for delivery orders. A state machine is a design
 * pattern where an object (here: a DeliveryRequest) can only be in one state at a
 * time, and can only move to certain states from its current state.
 *
 * Think of a traffic light: it can go Green → Yellow → Red → Green.
 * It CANNOT go Red → Green directly — Yellow must come first.
 * Our deliveries work the same way.
 *
 * --- The two valid paths ---
 *
 *   Immediate order (isImmediate = true) — "Order Now":
 *   PLACED → ASSIGNED → OUT_FOR_DELIVERY → DELIVERED
 *                                        → FAILED
 *
 *   Scheduled order (isImmediate = false) — "Schedule for Later":
 *   PLACED → SCHEDULED → ASSIGNED → OUT_FOR_DELIVERY → DELIVERED
 *                                                     → FAILED → RESCHEDULED
 *
 * --- OOP concepts used ---
 * @Service    — marks this as a Spring-managed service bean
 * @Autowired  — Spring automatically injects the repository dependencies
 * switch expressions — clean, readable state machine logic
 * Encapsulation — the state machine rules live only here, not scattered across the app
 *
 * --- This is Bhavya's service (Feature 4) ---
 */
@Service
public class  TrackingService {

    // Spring automatically creates and injects these — we never call "new" on them
    @Autowired
    private TrackingEventRepository trackingEventRepository;

    @Autowired
    private DeliveryRequestRepository deliveryRequestRepository;

    // -------------------------------------------------------------------------
    // MAIN METHOD: Update a delivery's status
    // -------------------------------------------------------------------------

    /**
     * Updates the status of a delivery request.
     *
     * This method is called when an agent clicks a status update button
     * (e.g. "Mark as Out for Delivery"). It does three things:
     *   1. Checks the transition is valid (state machine enforcement)
     *   2. Saves a TrackingEvent so the history is permanently recorded
     *   3. Updates the DeliveryRequest's current status in the database
     *
     * @param deliveryRequestId  the ID of the order being updated
     * @param newStatus          the status the agent wants to move it to
     * @param updatedBy          the User (agent) making the change — can be null for system updates
     * @param note               optional note, e.g. "Nobody home" when marking as FAILED
     * @return the updated DeliveryRequest
     * @throws IllegalArgumentException if the order ID doesn't exist
     * @throws IllegalStateException    if the transition is not allowed
     */
    public DeliveryRequest updateStatus(Long deliveryRequestId,
                                        DeliveryStatusEnum newStatus,
                                        User updatedBy,
                                        String note) {

        // Step 1 — Find the order. If it doesn't exist, throw an error immediately.
        DeliveryRequest request = deliveryRequestRepository.findById(deliveryRequestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No delivery request found with ID: " + deliveryRequestId));

        DeliveryStatusEnum currentStatus = request.getStatus();

        // Step 2 — Check if this transition is allowed by the state machine
        if (!isValidTransition(currentStatus, newStatus, request.isImmediate())) {
            throw new IllegalStateException(
                    "Invalid status transition: " + currentStatus + " → " + newStatus
                    + " (order type: " + (request.isImmediate() ? "immediate" : "scheduled") + ")"
            );
        }

        // Step 3 — Record this change as a TrackingEvent (the permanent history log)
        TrackingEvent event = new TrackingEvent(request, currentStatus, newStatus, updatedBy, note);
        trackingEventRepository.save(event);

        // Step 4 — Update the order's current status and save it
        request.setStatus(newStatus);
        deliveryRequestRepository.save(request);

        return request;
    }

    // -------------------------------------------------------------------------
    // STATE MACHINE: Which transitions are valid?
    // -------------------------------------------------------------------------

    /**
     * The heart of the state machine — decides if a transition is allowed.
     *
     * Rules differ based on whether the order is immediate or scheduled.
     * If a transition is not listed here, it is BLOCKED.
     *
     * @param from        the current status
     * @param to          the requested new status
     * @param isImmediate true = Order Now, false = Schedule for Later
     * @return true if the transition is allowed, false if it should be blocked
     */
    public boolean isValidTransition(DeliveryStatusEnum from,
                                     DeliveryStatusEnum to,
                                     boolean isImmediate) {
        if (isImmediate) {
            // --- Immediate order path ---
            // PLACED → ASSIGNED → OUT_FOR_DELIVERY → DELIVERED or FAILED
            return switch (from) {
                case PLACED           -> to == DeliveryStatusEnum.ASSIGNED;
                case ASSIGNED         -> to == DeliveryStatusEnum.OUT_FOR_DELIVERY;
                case OUT_FOR_DELIVERY -> to == DeliveryStatusEnum.DELIVERED
                                      || to == DeliveryStatusEnum.FAILED;
                default -> false; // DELIVERED, FAILED — terminal states, no further transitions
            };
        } else {
            // --- Scheduled order path ---
            // PLACED → SCHEDULED → ASSIGNED → OUT_FOR_DELIVERY → DELIVERED or FAILED → RESCHEDULED
            return switch (from) {
                case PLACED           -> to == DeliveryStatusEnum.SCHEDULED;
                case SCHEDULED        -> to == DeliveryStatusEnum.ASSIGNED;
                case ASSIGNED         -> to == DeliveryStatusEnum.OUT_FOR_DELIVERY;
                case OUT_FOR_DELIVERY -> to == DeliveryStatusEnum.DELIVERED
                                      || to == DeliveryStatusEnum.FAILED;
                case FAILED           -> to == DeliveryStatusEnum.RESCHEDULED;
                default -> false; // DELIVERED, RESCHEDULED — terminal states
            };
        }
    }

    // -------------------------------------------------------------------------
    // HELPER: What can this order move to next?
    // -------------------------------------------------------------------------

    /**
     * Returns the list of valid next statuses for a given delivery request.
     *
     * The TrackingController uses this to decide which buttons to show the agent.
     * If an order is ASSIGNED, only "Mark as Out for Delivery" should appear.
     * If it's OUT_FOR_DELIVERY, both "Mark as Delivered" and "Mark as Failed" appear.
     *
     * @param request the current delivery request
     * @return list of statuses the agent is allowed to move this order to
     */
    public List<DeliveryStatusEnum> getValidNextStatuses(DeliveryRequest request) {
        return switch (request.getStatus()) {
            case PLACED ->
                    request.isImmediate()
                            ? List.of(DeliveryStatusEnum.ASSIGNED)
                            : List.of(DeliveryStatusEnum.SCHEDULED);
            case SCHEDULED        -> List.of(DeliveryStatusEnum.ASSIGNED);
            case ASSIGNED         -> List.of(DeliveryStatusEnum.OUT_FOR_DELIVERY);
            case OUT_FOR_DELIVERY -> List.of(DeliveryStatusEnum.DELIVERED, DeliveryStatusEnum.FAILED);
            case FAILED           ->
                    request.isImmediate()
                            ? List.of()   // Immediate failed orders are terminal
                            : List.of(DeliveryStatusEnum.RESCHEDULED);
            default -> List.of(); // DELIVERED, RESCHEDULED — nothing left to do
        };
    }

    // -------------------------------------------------------------------------
    // QUERY HELPERS: Fetch data for the controller and UI
    // -------------------------------------------------------------------------

    /**
     * Returns the full tracking timeline for a delivery — every status change in order.
     * This is what populates the timeline on the customer's tracking page.
     *
     * @param deliveryRequestId the order to get history for
     * @return list of TrackingEvents, oldest first
     */
    public List<TrackingEvent> getTrackingTimeline(Long deliveryRequestId) {
        return trackingEventRepository.findByDeliveryRequestIdOrderByTimestampAsc(deliveryRequestId);
    }

    /**
     * Fetches a single delivery request by its ID.
     * Returns Optional — the controller checks if it's empty and shows a 404 if so.
     */
    public Optional<DeliveryRequest> getOrderById(Long id) {
        return deliveryRequestRepository.findById(id);
    }

    /**
     * Returns all orders placed by a specific customer.
     * Used on the "My Orders" page.
     */
    public List<DeliveryRequest> getOrdersByCustomer(User customer) {
        return deliveryRequestRepository.findByCustomer(customer);
    }
}
