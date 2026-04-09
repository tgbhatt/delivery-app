package com.bt.deliveryapp.service;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.enums.Priority;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.TimeSlot;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.DeliveryRequestRepository;
import com.bt.deliveryapp.repository.TimeSlotRepository;
import com.bt.deliveryapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * DeliveryBookingService — the Business Logic Layer for Feature 1.
 *
 * ─── What is a Service class? ───────────────────────────────────────────────
 * In Spring Boot, we separate code into three layers:
 *
 *   Controller  →  Service  →  Repository  →  Database
 *
 * The Controller receives the request from the browser (e.g. "place order").
 * The Repository talks directly to the database (read/write rows).
 * The Service sits in the MIDDLE — it contains the actual business rules.
 *
 * Think of it like a bank:
 *   - The bank teller (Controller) takes your request
 *   - The back office (Service) checks: do you have enough money? Is the account valid?
 *   - The vault system (Repository) actually moves the data
 *
 * ─── Why @Service? ──────────────────────────────────────────────────────────
 * @Service tells Spring "this is a service class — create one instance of it
 * automatically and make it available wherever it is needed."
 * This is called Dependency Injection — Spring manages the objects for you.
 *
 * ─── What does this class do? ───────────────────────────────────────────────
 * It handles everything related to placing and managing delivery orders:
 *   1. placeImmediateOrder()  — Order Now (goes straight to priority queue)
 *   2. placeScheduledOrder()  — Schedule for Later (picks a time slot)
 *   3. getOrdersForCustomer() — fetch all orders placed by one customer
 *   4. cancelOrder()          — cancel an order (only if it hasn't been assigned yet)
 *   5. getAvailableSlots()    — list time slots the customer can choose from
 */
@Service
public class DeliveryBookingService {

    // ─── Dependencies (injected automatically by Spring) ─────────────────────

    /**
     * @Autowired tells Spring: "find the DeliveryRequestRepository bean and
     * inject it here automatically." We do not create it ourselves with 'new'.
     * This is the OOP principle of Dependency Injection — classes depend on
     * abstractions, not on creating their own concrete instances.
     */
    @Autowired
    private DeliveryRequestRepository deliveryRequestRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    /**
     * We need UserRepository to re-fetch the User from the database.
     *
     * --- Why re-fetch? ---
     * The User object comes from the HTTP session (stored during login).
     * Objects in the session are "detached" — they are like photocopies
     * of the database record, not live connections. Hibernate (our ORM)
     * needs a "managed" (live) entity when saving a new DeliveryRequest
     * with a @ManyToOne relationship to User. Re-fetching by ID gives
     * us a fresh, managed User that Hibernate can work with properly.
     */
    @Autowired
    private UserRepository userRepository;

    /**
     * We use RouteOptimisationService to automatically pick the best agent
     * when a new order is placed — instead of leaving it unassigned for the admin.
     *
     * --- Why @Lazy? ---
     * @Lazy tells Spring: "don't inject this bean at startup — wait until it's
     * first actually used." This is a safety measure to prevent a circular
     * dependency problem where two classes each try to load the other on startup.
     * It is a common pattern when two service classes need to reference each other.
     */
    @Autowired
    @org.springframework.context.annotation.Lazy
    private RouteOptimisationService routeOptimisationService;

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 1: Place an Immediate Order (Order Now)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Places an "Order Now" delivery request.
     *
     * --- What happens here? ---
     * 1. We validate that the essential fields are filled in (not blank).
     * 2. We create a DeliveryRequest using the "immediate" constructor.
     * 3. We set the status to PLACED and priority to HIGH (immediate orders are urgent).
     * 4. We save it to the database via the repository.
     * 5. We return the saved object (it now has an id from the database).
     *
     * --- What is @Transactional? ---
     * @Transactional means: if anything goes wrong during this method
     * (e.g. a database error halfway through), ALL changes are rolled back.
     * Nothing is half-saved. It is an all-or-nothing operation.
     *
     * @param customer          the logged-in user placing the order
     * @param restaurantAddress where to pick up the food from
     * @param customerAddress   where to deliver the food
     * @param orderDescription  what food was ordered (e.g. "2x Biryani, 1x Coke")
     * @return the saved DeliveryRequest with its new database id
     * @throws IllegalArgumentException if required fields are missing
     */
    @Transactional
    public DeliveryRequest placeImmediateOrder(User customer,
                                               String restaurantAddress,
                                               String pickupZone,
                                               String customerAddress,
                                               String orderDescription) {

        // --- Validation ---
        // We check the inputs before doing anything. If something is wrong,
        // we throw an IllegalArgumentException which will be caught by the Controller.
        validateOrderInputs(restaurantAddress, customerAddress, orderDescription);

        // --- Re-fetch the User from the database ---
        // The 'customer' parameter comes from the HTTP session and is "detached"
        // (not connected to the current database session). We re-fetch it by ID
        // so Hibernate gets a "managed" entity it can properly link via foreign key.
        User managedCustomer = userRepository.findById(customer.getId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found. Please log in again."));

        // --- Create the delivery request ---
        // We use the "immediate" constructor from DeliveryRequest.java.
        // We pass Priority.HIGH because Order Now is always urgent,
        // and null for specialInstructions (the UI can add this later).
        DeliveryRequest order = new DeliveryRequest(
                managedCustomer,
                restaurantAddress,
                customerAddress,
                orderDescription,
                Priority.HIGH,     // immediate orders are always high priority
                null               // no special instructions at this stage
        );

        // --- Save the pickup zone ---
        // This tells RouteOptimisationService which zone this order is in,
        // so it can prefer agents whose zone matches (e.g. NORTH agent for NORTH pickup).
        order.setPickupZone(pickupZone);

        // --- Set status to PLACED ---
        order.setStatus(DeliveryStatusEnum.PLACED);

        // --- Save to database (INSERT) ---
        // After this save, order.getId() is populated — the database assigned it an ID.
        // We need that ID before we can call suggestBestAgent(orderId).
        DeliveryRequest savedOrder = deliveryRequestRepository.save(order);

        // --- Auto-assign the best available agent ---
        // tryAutoAssign() asks RouteOptimisationService to pick the best agent
        // and immediately assigns them, changing the status from PLACED → ASSIGNED.
        // If no agents are available, the order stays PLACED for the admin to assign manually.
        tryAutoAssign(savedOrder);

        return savedOrder;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 2: Place a Scheduled Order (Schedule for Later)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Places a "Schedule for Later" delivery request.
     *
     * --- What is different from immediate? ---
     * The customer has chosen a specific time slot (e.g. "tomorrow 10am–12pm").
     * We need to:
     *   1. Check the chosen slot is still available (not fully booked).
     *   2. Create the order with that slot linked.
     *   3. Mark the slot as one booking consumed (incrementBookedCount).
     *   4. Set status to SCHEDULED (not PLACED, since it has a future slot).
     *
     * --- Why do we need @Transactional here? ---
     * Two things are saved: the new order AND the updated time slot.
     * @Transactional makes sure both succeed together, or neither is saved.
     * Without it, you could end up with an order saved but the slot count not updated.
     *
     * @param customer          the logged-in user
     * @param restaurantAddress pickup location
     * @param customerAddress   delivery location
     * @param orderDescription  food items
     * @param timeSlotId        the id of the slot the customer chose
     * @return the saved DeliveryRequest
     * @throws IllegalArgumentException if the slot does not exist or is already full
     */
    @Transactional
    public DeliveryRequest placeScheduledOrder(User customer,
                                               String restaurantAddress,
                                               String pickupZone,
                                               String customerAddress,
                                               String orderDescription,
                                               Long timeSlotId) {

        // --- Validate text inputs ---
        validateOrderInputs(restaurantAddress, customerAddress, orderDescription);

        // --- Re-fetch the User from the database ---
        // Same reason as in placeImmediateOrder() — the session User is detached,
        // so we get a fresh managed copy from the database.
        User managedCustomer = userRepository.findById(customer.getId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found. Please log in again."));

        // --- Find the chosen time slot ---
        // Optional<TimeSlot> is like a box that may or may not contain a TimeSlot.
        // It prevents NullPointerException if the slot id doesn't exist in the database.
        Optional<TimeSlot> slotOpt = timeSlotRepository.findById(timeSlotId);

        if (slotOpt.isEmpty()) {
            throw new IllegalArgumentException("The selected time slot does not exist. Please choose again.");
        }

        TimeSlot slot = slotOpt.get();

        // --- Check the slot is still bookable ---
        // isBookable() is the business logic method we wrote in TimeSlot.java:
        //   return available && bookedCount < capacity;
        if (!slot.isBookable()) {
            throw new IllegalArgumentException(
                    "Sorry, the slot '" + slot.getLabel() + "' is now full. Please choose a different slot.");
        }

        // --- Create the order with the slot attached ---
        // We use the "scheduled" constructor from DeliveryRequest.java.
        // It sets: immediate = false, timeSlot = slot, createdAt = now.
        // Scheduled orders are MEDIUM priority — they have a planned window,
        // so less urgent than immediate orders.
        DeliveryRequest order = new DeliveryRequest(
                managedCustomer,   // use the re-fetched managed User, not the session copy
                restaurantAddress,
                customerAddress,
                orderDescription,
                Priority.MEDIUM,   // scheduled orders are medium priority
                slot,              // the time slot the customer chose
                null               // no special instructions at this stage
        );

        // --- Save the pickup zone ---
        // Same as immediate orders: store the zone so the auto-assign algorithm
        // can match this order with an agent who operates in the same area.
        order.setPickupZone(pickupZone);

        // --- Override status to SCHEDULED ---
        // The constructor sets PLACED by default, but scheduled orders
        // should start as SCHEDULED to distinguish them clearly.
        order.setStatus(DeliveryStatusEnum.SCHEDULED);

        // --- Update the slot's booked count ---
        slot.incrementBookedCount();
        timeSlotRepository.save(slot);

        // --- Save to database ---
        // Same pattern as immediate orders: save first to get the ID, then auto-assign.
        DeliveryRequest savedOrder = deliveryRequestRepository.save(order);

        // --- Auto-assign the best available agent ---
        // For scheduled orders, we assign right away so the agent can plan ahead.
        // If no agent is available, the order stays SCHEDULED for the admin to assign later.
        tryAutoAssign(savedOrder);

        return savedOrder;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 3: Get all orders placed by a specific customer
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all delivery requests made by a customer, newest first.
     * Used to show the customer their order history / tracking page.
     *
     * --- No @Transactional needed here ---
     * This is a read-only operation. @Transactional is only needed when
     * we are writing (INSERT or UPDATE) to the database.
     *
     * @param customer the logged-in customer
     * @return list of their orders, ordered by most recent first
     */
    public List<DeliveryRequest> getOrdersForCustomer(User customer) {
        // This calls the custom query method we wrote in DeliveryRequestRepository:
        //   findByCustomerOrderByCreatedAtDesc(User customer)
        // Spring generates: SELECT * FROM delivery_requests
        //                   WHERE customer_id = ? ORDER BY created_at DESC
        return deliveryRequestRepository.findByCustomerOrderByCreatedAtDesc(customer);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 4: Cancel an order
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cancels a delivery order — but only if it has not been assigned yet.
     *
     * --- Business rule ---
     * An order can only be cancelled if it is in PLACED or SCHEDULED status.
     * Once an agent has been ASSIGNED, or the order is OUT_FOR_DELIVERY,
     * it is too late to cancel — the food is already being picked up.
     *
     * This is an example of a STATE MACHINE — the order status controls
     * what actions are allowed at each stage of the delivery lifecycle.
     *
     * --- What is Optional? ---
     * Optional<DeliveryRequest> means: "this might return a value or might not."
     * We return Optional.empty() when the cancellation was not allowed,
     * and Optional.of(order) when it succeeded.
     * The Controller will check which one it got and show the right message.
     *
     * @param orderId  the id of the order to cancel
     * @param customer the customer requesting the cancellation (for ownership check)
     * @return Optional containing the cancelled order if successful, or empty if not allowed
     */
    @Transactional
    public Optional<DeliveryRequest> cancelOrder(Long orderId, User customer) {

        // Find the order by id
        Optional<DeliveryRequest> orderOpt = deliveryRequestRepository.findById(orderId);

        if (orderOpt.isEmpty()) {
            return Optional.empty();   // order doesn't exist
        }

        DeliveryRequest order = orderOpt.get();

        // Security check: make sure the order belongs to this customer.
        // A customer should never be able to cancel someone else's order.
        if (!order.getCustomer().getId().equals(customer.getId())) {
            return Optional.empty();   // not their order
        }

        // State machine check: can we cancel at this point?
        DeliveryStatusEnum currentStatus = order.getStatus();
        boolean canCancel = (currentStatus == DeliveryStatusEnum.PLACED ||
                             currentStatus == DeliveryStatusEnum.SCHEDULED);

        if (!canCancel) {
            return Optional.empty();   // too late to cancel
        }

        // --- If the order had a slot, release it back ---
        // When a scheduled order is cancelled, the slot capacity should be freed
        // so another customer can book it.
        if (!order.isImmediate() && order.getTimeSlot() != null) {
            TimeSlot slot = order.getTimeSlot();
            slot.setBookedCount(Math.max(0, slot.getBookedCount() - 1));  // decrement (min 0)
            slot.setAvailable(true);                                        // mark available again
            timeSlotRepository.save(slot);
        }

        // --- Mark the order as FAILED (cancelled) ---
        // We reuse FAILED status for cancelled orders for simplicity.
        // In a real app you would add a CANCELLED status enum value.
        order.setStatus(DeliveryStatusEnum.FAILED);
        deliveryRequestRepository.save(order);

        return Optional.of(order);   // return the cancelled order
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 5: Get available time slots for a given date
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the list of bookable time slots for a given date.
     * Used to populate the dropdown/list when a customer is scheduling an order.
     *
     * @param date the date the customer wants to schedule for
     * @return list of available slots on that date
     */
    public List<TimeSlot> getAvailableSlots(LocalDate date) {
        // Calls the repository method we wrote in TimeSlotRepository:
        //   findBySlotDateAndAvailableTrue(LocalDate date)
        // Spring generates: SELECT * FROM time_slots
        //                   WHERE slot_date = ? AND available = true
        return timeSlotRepository.findBySlotDateAndAvailableTrue(date);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 6: Permanently delete an order (completing CRUD — Delete)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Permanently deletes a delivery order from the database.
     *
     * --- Why is this different from cancelOrder()? ---
     * cancelOrder() changes the status to FAILED — the record stays in the database.
     * deleteOrder() physically removes the row — it is gone permanently.
     * This completes the CRUD requirement: Create, Read, Update, DELETE.
     *
     * --- When is delete allowed? ---
     * Only orders in PLACED status can be deleted.
     * Once an order is SCHEDULED or beyond, it has slot allocations and agent
     * assignments attached — deleting it would leave orphaned data.
     * The customer must own the order (security check).
     *
     * @param orderId  the id of the order to delete
     * @param customer the customer requesting deletion
     * @return true if deleted successfully, false if not allowed
     */
    @Transactional
    public boolean deleteOrder(Long orderId, User customer) {
        Optional<DeliveryRequest> orderOpt = deliveryRequestRepository.findById(orderId);

        if (orderOpt.isEmpty()) {
            return false;   // order does not exist
        }

        DeliveryRequest order = orderOpt.get();

        // Ownership check: only the customer who placed it can delete it
        if (!order.getCustomer().getId().equals(customer.getId())) {
            return false;
        }

        // Only allow delete if still in PLACED status (not yet scheduled or assigned)
        if (order.getStatus() != DeliveryStatusEnum.PLACED) {
            return false;
        }

        // deleteById() is provided free by JpaRepository — no need to write it ourselves
        deliveryRequestRepository.deleteById(orderId);
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PRIVATE HELPER — Auto-assign best available agent
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tries to automatically assign the best available agent to an order.
     *
     * This is called right after every new order is saved. It asks the
     * RouteOptimisationService "who is the best agent for this order?"
     * and if one is found, assigns them immediately.
     *
     * --- What "best" means ---
     * RouteOptimisationService.suggestBestAgent() uses zone preference:
     *   Step A: try agents whose zone matches the order's pickup zone
     *   Step B: if none found, fall back to any available agent under the workload cap
     *
     * --- What happens if no agent is available? ---
     * Nothing — the order stays in its current status (PLACED or SCHEDULED).
     * The admin can assign manually from the dashboard later.
     * We never throw an error here — a booking succeeding WITHOUT an agent
     * assigned is perfectly valid; it just needs follow-up.
     *
     * --- Why private? ---
     * This is an internal helper only used within this class.
     * Making it private follows the Encapsulation principle — hide internal
     * details that no other class needs to know about.
     *
     * @param order the newly saved order to assign an agent to
     */
    private void tryAutoAssign(DeliveryRequest order) {
        try {
            // Ask RouteOptimisationService to suggest the best agent
            com.bt.deliveryapp.model.Agent bestAgent =
                    routeOptimisationService.suggestBestAgent(order.getId());

            if (bestAgent != null) {
                // An agent was found — link them to the order
                order.setAgent(bestAgent);

                // Only advance to ASSIGNED for IMMEDIATE (Order Now) orders.
                // A scheduled order already has a status of SCHEDULED — it is not
                // being picked up right now, just assigned for a future time slot.
                // Changing it to ASSIGNED would make it incorrectly appear in the
                // agent's "Right Now" section instead of "Today's Schedule".
                if (order.isImmediate()) {
                    order.setStatus(DeliveryStatusEnum.ASSIGNED);
                }

                deliveryRequestRepository.save(order);
            }
            // If bestAgent is null, no agents are available — order stays unassigned
        } catch (Exception e) {
            // Temporarily print the error so we can diagnose why assignment is failing.
            // Check the terminal where you ran the app — look for "AUTO-ASSIGN FAILED".
            System.err.println("AUTO-ASSIGN FAILED for order #" + order.getId()
                    + " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            // The order is already saved successfully — we just skip assignment.
            // The admin can assign manually from the dashboard.
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PRIVATE HELPER — Input Validation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates that the three required text fields are not empty.
     *
     * --- Why private? ---
     * This is internal to the service class — no other class needs to call it.
     * Making it private is Encapsulation: hiding internal helper logic.
     *
     * --- What does trim() do? ---
     * "  " (spaces only) would pass a simple null check but is still useless.
     * trim() removes leading/trailing spaces first, so "   ".trim() becomes ""
     * and the isEmpty() check correctly catches it.
     */
    private void validateOrderInputs(String restaurantAddress,
                                     String customerAddress,
                                     String orderDescription) {
        if (restaurantAddress == null || restaurantAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Restaurant address cannot be empty.");
        }
        if (customerAddress == null || customerAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Delivery address cannot be empty.");
        }
        if (orderDescription == null || orderDescription.trim().isEmpty()) {
            throw new IllegalArgumentException("Order description cannot be empty.");
        }
    }
}
