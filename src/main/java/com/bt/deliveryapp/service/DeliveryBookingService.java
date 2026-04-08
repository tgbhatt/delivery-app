package com.bt.deliveryapp.service;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.enums.Priority;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.TimeSlot;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.DeliveryRequestRepository;
import com.bt.deliveryapp.repository.TimeSlotRepository;
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
                                               String customerAddress,
                                               String orderDescription,
                                               String priorityStr,
                                               String specialInstructions) {

        // --- Validation ---
        validateOrderInputs(restaurantAddress, customerAddress, orderDescription);

        // --- Parse priority (default HIGH for immediate if not provided) ---
        Priority priority = parsePriority(priorityStr, Priority.HIGH);

        // --- Create the delivery request ---
        DeliveryRequest order = new DeliveryRequest(
                customer,
                restaurantAddress,
                customerAddress,
                orderDescription,
                priority,
                specialInstructions
        );

        order.setStatus(DeliveryStatusEnum.PLACED);

        return deliveryRequestRepository.save(order);
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
                                               String customerAddress,
                                               String orderDescription,
                                               String priorityStr,
                                               String specialInstructions,
                                               Long timeSlotId) {

        // --- Validate text inputs ---
        validateOrderInputs(restaurantAddress, customerAddress, orderDescription);

        // --- Parse priority (default MEDIUM for scheduled if not provided) ---
        Priority priority = parsePriority(priorityStr, Priority.MEDIUM);

        // --- Find the chosen time slot ---
        Optional<TimeSlot> slotOpt = timeSlotRepository.findById(timeSlotId);

        if (slotOpt.isEmpty()) {
            throw new IllegalArgumentException("The selected time slot does not exist. Please choose again.");
        }

        TimeSlot slot = slotOpt.get();

        // --- Check the slot is still bookable ---
        if (!slot.isBookable()) {
            throw new IllegalArgumentException(
                    "Sorry, the slot '" + slot.getLabel() + "' is now full. Please choose a different slot.");
        }

        // --- Create the order with the slot attached ---
        DeliveryRequest order = new DeliveryRequest(
                customer,
                restaurantAddress,
                customerAddress,
                orderDescription,
                priority,
                slot,
                specialInstructions
        );

        order.setStatus(DeliveryStatusEnum.SCHEDULED);

        slot.incrementBookedCount();
        timeSlotRepository.save(slot);

        return deliveryRequestRepository.save(order);
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

    /**
     * Returns all available time slots from today onwards (for the booking dropdown).
     * Used by the booking form to show customers what they can choose from.
     */
    public List<TimeSlot> getUpcomingAvailableSlots() {
        return timeSlotRepository.findBySlotDateGreaterThanEqualAndAvailableTrue(LocalDate.now());
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

    /**
     * Safely converts a priority string (e.g. "HIGH", "MEDIUM", "LOW") from the form
     * into the Priority enum. Falls back to the supplied default if the string is
     * null, blank, or unrecognised.
     */
    private Priority parsePriority(String priorityStr, Priority defaultPriority) {
        if (priorityStr == null || priorityStr.trim().isEmpty()) {
            return defaultPriority;
        }
        try {
            return Priority.valueOf(priorityStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultPriority;
        }
    }
}
