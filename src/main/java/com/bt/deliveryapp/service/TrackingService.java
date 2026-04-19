package com.bt.deliveryapp.service;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.TrackingEvent;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.DeliveryRequestRepository;
import com.bt.deliveryapp.repository.TrackingEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
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
public class TrackingService {

    // Spring automatically creates and injects these — we never call "new" on them
    @Autowired
    private TrackingEventRepository trackingEventRepository;

    @Autowired
    private DeliveryRequestRepository deliveryRequestRepository;

    // SecureRandom is better than Math.random() for generating OTPs because it uses
    // a cryptographically strong algorithm — harder to predict than plain Math.random().
    private final SecureRandom secureRandom = new SecureRandom();

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
            // PLACED → ASSIGNED → OUT_FOR_DELIVERY → ARRIVED → DELIVERED or FAILED
            return switch (from) {
                case PLACED           -> to == DeliveryStatusEnum.ASSIGNED;
                case ASSIGNED         -> to == DeliveryStatusEnum.OUT_FOR_DELIVERY;
                case OUT_FOR_DELIVERY -> to == DeliveryStatusEnum.ARRIVED;
                case ARRIVED          -> to == DeliveryStatusEnum.DELIVERED
                                      || to == DeliveryStatusEnum.FAILED;
                default -> false; // DELIVERED, FAILED — terminal states, no further transitions
            };
        } else {
            // --- Scheduled order path ---
            // PLACED → SCHEDULED → ASSIGNED → OUT_FOR_DELIVERY → ARRIVED → DELIVERED or FAILED → RESCHEDULED
            return switch (from) {
                case PLACED           -> to == DeliveryStatusEnum.SCHEDULED;
                case SCHEDULED        -> to == DeliveryStatusEnum.ASSIGNED;
                case ASSIGNED         -> to == DeliveryStatusEnum.OUT_FOR_DELIVERY;
                case OUT_FOR_DELIVERY -> to == DeliveryStatusEnum.ARRIVED;
                case ARRIVED          -> to == DeliveryStatusEnum.DELIVERED
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
            // The agent must click "I've Arrived" first — no longer going directly to DELIVERED.
            case OUT_FOR_DELIVERY -> List.of(DeliveryStatusEnum.ARRIVED);
            // ARRIVED: agent enters customer OTP (via /confirm-otp endpoint) to mark DELIVERED,
            // OR marks FAILED if nobody is home. FAILED is still a regular status button.
            case ARRIVED          -> List.of(DeliveryStatusEnum.FAILED);
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

    // -------------------------------------------------------------------------
    // OTP METHODS: Arrival confirmation and delivery validation
    // -------------------------------------------------------------------------

    /**
     * Called when an agent clicks "I've Arrived".
     *
     * What this does step by step:
     *   1. Finds the order in the database
     *   2. Validates the transition OUT_FOR_DELIVERY → ARRIVED is allowed
     *   3. Generates a random 4-digit OTP using SecureRandom
     *   4. Records a TrackingEvent so the history timeline shows the ARRIVED step
     *   5. Saves the OTP and new status to the database
     *
     * Why SecureRandom instead of Math.random()?
     *   Math.random() uses a predictable algorithm — with enough observations
     *   someone could guess the next number. SecureRandom uses the OS's entropy
     *   source, making it much harder to predict. Good habit for anything security-related.
     *
     * @param orderId the ID of the order being delivered
     * @param agent   the agent who has arrived
     * @return the updated DeliveryRequest with OTP stored inside it
     */
    public DeliveryRequest arriveAndGenerateOtp(Long orderId, User agent) {

        // Step 1 — Find the order or throw a clear error
        DeliveryRequest order = deliveryRequestRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No delivery request found with ID: " + orderId));

        // Step 2 — Check the state machine allows this transition
        if (!isValidTransition(order.getStatus(), DeliveryStatusEnum.ARRIVED, order.isImmediate())) {
            throw new IllegalStateException(
                    "Cannot mark as Arrived — order is currently: " + order.getStatus()
                    + ". Order must be OUT_FOR_DELIVERY first.");
        }

        // Step 3 — Generate a 4-digit OTP (0000 to 9999)
        // nextInt(10000) gives a number from 0 to 9999.
        // String.format("%04d", ...) pads with leading zeros so "7" becomes "0007".
        int otpInt = secureRandom.nextInt(10000);
        String otp = String.format("%04d", otpInt);

        // Step 4 — Record the ARRIVED event in the tracking timeline
        TrackingEvent event = new TrackingEvent(
                order,
                order.getStatus(),
                DeliveryStatusEnum.ARRIVED,
                agent,
                "Agent has arrived at the delivery address. Awaiting OTP confirmation."
        );
        trackingEventRepository.save(event);

        // Step 5 — Update the order: new status, store OTP, record when it was generated
        order.setStatus(DeliveryStatusEnum.ARRIVED);
        order.setDeliveryOtp(otp);
        order.setOtpGeneratedAt(LocalDateTime.now());
        deliveryRequestRepository.save(order);

        return order;
    }

    /**
     * Called when an agent submits the OTP they got from the customer.
     *
     * What this does step by step:
     *   1. Finds the order in the database
     *   2. Checks the order is in ARRIVED status (OTP only makes sense then)
     *   3. Compares the entered OTP with the stored OTP — must match exactly
     *   4. If correct: records DELIVERED event, clears the OTP, marks order as DELIVERED
     *   5. If wrong: throws an error so the agent can try again
     *
     * @param orderId     the order being confirmed
     * @param enteredOtp  the 4-digit code the agent typed in (what the customer told them)
     * @param agent       the agent confirming the delivery
     * @return the updated DeliveryRequest now in DELIVERED status
     * @throws IllegalStateException if the OTP is wrong, or the order isn't in ARRIVED state
     */
    public DeliveryRequest confirmDeliveryWithOtp(Long orderId, String enteredOtp, User agent) {

        // Step 1 — Find the order
        DeliveryRequest order = deliveryRequestRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No delivery request found with ID: " + orderId));

        // Step 2 — OTP confirmation only works when the order is in ARRIVED status
        if (order.getStatus() != DeliveryStatusEnum.ARRIVED) {
            throw new IllegalStateException(
                    "OTP confirmation is only available when the order status is ARRIVED.");
        }

        // Step 3 — Validate the OTP
        // .trim() removes any accidental spaces the agent might have typed.
        // .equals() does an exact character-by-character comparison — must match perfectly.
        String storedOtp = order.getDeliveryOtp();
        if (storedOtp == null || !storedOtp.equals(enteredOtp.trim())) {
            throw new IllegalStateException(
                    "❌ Incorrect OTP. Please ask the customer to read their code again.");
        }

        // Step 4 — OTP matched! Record the DELIVERED event in the timeline
        TrackingEvent event = new TrackingEvent(
                order,
                DeliveryStatusEnum.ARRIVED,
                DeliveryStatusEnum.DELIVERED,
                agent,
                "Delivery confirmed via OTP. Package handed to recipient."
        );
        trackingEventRepository.save(event);

        // Step 5 — Mark as DELIVERED and clear the OTP (no longer needed)
        order.setStatus(DeliveryStatusEnum.DELIVERED);
        order.setDeliveryOtp(null);         // Clear so it can't be reused
        order.setOtpGeneratedAt(null);
        deliveryRequestRepository.save(order);

        return order;
    }
}
