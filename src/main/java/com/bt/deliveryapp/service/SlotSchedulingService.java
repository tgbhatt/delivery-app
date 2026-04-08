package com.bt.deliveryapp.service;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.enums.Priority;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.TimeSlot;
import com.bt.deliveryapp.repository.DeliveryRequestRepository;
import com.bt.deliveryapp.repository.TimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SlotSchedulingService — the Greedy Interval Scheduling Algorithm.
 *
 * ─── What problem does this solve? ───────────────────────────────────────────
 * When a customer places an immediate order (Order Now), no time slot is assigned.
 * The order just sits in the database with status PLACED.
 *
 * This service periodically looks at all PLACED orders and assigns each one
 * to the earliest available delivery time slot — making sure:
 *   1. Higher priority orders are processed first (URGENT before HIGH before MEDIUM before LOW)
 *   2. Each slot is not over-booked beyond its capacity
 *   3. No two orders with conflicting time windows are assigned to the same slot incorrectly
 *
 * ─── What is a Greedy Algorithm? ─────────────────────────────────────────────
 * A greedy algorithm makes the locally best choice at each step without
 * looking ahead. It is called "greedy" because it grabs the best available
 * option right now, rather than planning the globally perfect solution.
 *
 * Real world analogy: imagine you are at a buffet and filling your plate.
 * You pick the best dish available at each turn, without thinking about
 * what will be available later. That is greedy — fast, simple, good enough.
 *
 * ─── How does our greedy algorithm work? ─────────────────────────────────────
 * Step 1: Fetch all unscheduled orders (status = PLACED), sorted by priority
 *         highest first (URGENT → HIGH → MEDIUM → LOW).
 *
 * Step 2: Fetch all available time slots from today onwards, sorted by
 *         start time — earliest first.
 *
 * Step 3: For each order (in priority order), assign it to the first slot
 *         that still has capacity. Update the slot's booked count.
 *         Move on to the next order.
 *
 * This is the classic Interval Scheduling greedy approach — O(n log n) time
 * complexity due to sorting, O(n) for the assignment loop.
 *
 * ─── Why is this the right algorithm here? ────────────────────────────────────
 * The greedy approach is ideal when:
 *   - You want to maximise the number of orders served
 *   - Orders have priorities that should be respected
 *   - You do not need a globally perfect solution, just a fast good one
 *
 * For a delivery logistics app, greedy scheduling is exactly what companies
 * like Swiggy and Zomato use internally (with more complexity on top).
 */
@Service
public class SlotSchedulingService {

    @Autowired
    private DeliveryRequestRepository deliveryRequestRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 1: Run the full greedy scheduling algorithm
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Assigns time slots to all currently unscheduled (PLACED) orders.
     *
     * This is the main method — it runs the complete greedy algorithm
     * from start to finish and returns a list of orders that were
     * successfully assigned a slot.
     *
     * --- When is this called? ---
     * It can be triggered manually by an admin, or automatically on a schedule.
     * For this project, the admin dashboard (Bhavya's Feature 5) will have
     * a button that calls this method.
     *
     * --- What does @Transactional do here? ---
     * Many orders and many slots are updated in one call.
     * @Transactional wraps everything in a single database transaction —
     * if anything fails mid-way, ALL changes are rolled back so the database
     * stays consistent. No half-assigned orders, no corrupted slot counts.
     *
     * @return list of orders that were successfully assigned a slot
     */
    @Transactional
    public List<DeliveryRequest> runScheduling() {

        // ── STEP 1: Get all unscheduled orders, sorted by priority ────────────
        // findByStatusOrderByPriorityDesc gives us PLACED orders with the
        // highest priority enum value first. But enum ordering in Java is
        // by declaration order (LOW=0, MEDIUM=1, HIGH=2, URGENT=3) — we need
        // URGENT first, so we sort manually after fetching.
        List<DeliveryRequest> unscheduledOrders =
                deliveryRequestRepository.findByStatusOrderByPriorityDesc(DeliveryStatusEnum.PLACED);

        // Sort by priority: URGENT first, then HIGH, then MEDIUM, then LOW
        // Comparator.comparingInt with a custom priority rank method ensures correct order
        unscheduledOrders.sort(Comparator.comparingInt(
                order -> -priorityRank(order.getPriority())   // negative = descending
        ));

        if (unscheduledOrders.isEmpty()) {
            System.out.println("[SlotSchedulingService] No unscheduled orders found.");
            return new ArrayList<>();
        }

        System.out.println("[SlotSchedulingService] Found " + unscheduledOrders.size() + " unscheduled orders.");

        // ── STEP 2: Get all available slots from today, sorted by start time ──
        // We want the earliest slots first so we fill them up before using later ones.
        // This is the greedy "take the earliest available" strategy.
        List<TimeSlot> availableSlots =
                timeSlotRepository.findBySlotDateGreaterThanEqualAndAvailableTrue(LocalDate.now());

        // Sort slots by date first, then by start time within the same date
        // e.g. March 29 9am → March 29 11am → March 29 1pm → March 30 9am ...
        availableSlots.sort(
                Comparator.comparing(TimeSlot::getSlotDate)
                          .thenComparing(TimeSlot::getStartTime)
        );

        if (availableSlots.isEmpty()) {
            System.out.println("[SlotSchedulingService] No available slots found. Cannot schedule.");
            return new ArrayList<>();
        }

        // ── STEP 3: Greedy assignment — match each order to the first open slot ─
        List<DeliveryRequest> successfullyScheduled = new ArrayList<>();

        for (DeliveryRequest order : unscheduledOrders) {

            // Find the first slot that still has capacity
            TimeSlot assignedSlot = findFirstAvailableSlot(availableSlots);

            if (assignedSlot == null) {
                // No more slots — stop processing
                System.out.println("[SlotSchedulingService] No more available slots. " +
                        (unscheduledOrders.size() - successfullyScheduled.size()) +
                        " orders could not be scheduled.");
                break;
            }

            // Assign this slot to the order
            order.setTimeSlot(assignedSlot);
            order.setStatus(DeliveryStatusEnum.SCHEDULED);

            // Update the slot's booked count
            // If this fills it to capacity, isBookable() will return false
            // and findFirstAvailableSlot() will skip it for the next order
            assignedSlot.incrementBookedCount();

            // Save both the order and the slot
            timeSlotRepository.save(assignedSlot);
            deliveryRequestRepository.save(order);

            successfullyScheduled.add(order);

            System.out.println("[SlotSchedulingService] Order #" + order.getId() +
                    " (priority: " + order.getPriority() + ") → " +
                    assignedSlot.getLabel() + " on " + assignedSlot.getSlotDate());
        }

        System.out.println("[SlotSchedulingService] Scheduling complete. " +
                successfullyScheduled.size() + " orders assigned.");

        return successfullyScheduled;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 2: Schedule a single specific order
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Assigns the best available time slot to one specific order.
     *
     * Used when a single new order needs to be scheduled immediately
     * rather than waiting for the batch algorithm to run.
     *
     * For example: admin manually schedules one order that was stuck in PLACED.
     *
     * @param orderId the id of the order to schedule
     * @return the updated order with a slot assigned, or null if no slot is available
     */
    @Transactional
    public DeliveryRequest scheduleSingleOrder(Long orderId) {

        // Find the order
        DeliveryRequest order = deliveryRequestRepository.findById(orderId).orElse(null);

        if (order == null || order.getStatus() != DeliveryStatusEnum.PLACED) {
            return null;   // order doesn't exist or is already scheduled
        }

        // Get available slots from today, sorted earliest first
        List<TimeSlot> availableSlots =
                timeSlotRepository.findBySlotDateGreaterThanEqualAndAvailableTrue(LocalDate.now());

        availableSlots.sort(
                Comparator.comparing(TimeSlot::getSlotDate)
                          .thenComparing(TimeSlot::getStartTime)
        );

        TimeSlot slot = findFirstAvailableSlot(availableSlots);

        if (slot == null) {
            return null;   // no slots available
        }

        order.setTimeSlot(slot);
        order.setStatus(DeliveryStatusEnum.SCHEDULED);
        slot.incrementBookedCount();

        timeSlotRepository.save(slot);
        return deliveryRequestRepository.save(order);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 3: Get all orders waiting to be scheduled
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all orders that are still in PLACED status (not yet scheduled).
     * Used by the admin dashboard to show a list of orders awaiting assignment.
     *
     * @return list of unscheduled orders, highest priority first
     */
    public List<DeliveryRequest> getUnscheduledOrders() {
        List<DeliveryRequest> orders =
                deliveryRequestRepository.findByStatusOrderByPriorityDesc(DeliveryStatusEnum.PLACED);

        // Sort highest priority first
        orders.sort(Comparator.comparingInt(order -> -priorityRank(order.getPriority())));
        return orders;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Finds the first slot in the list that is still bookable.
     *
     * --- Why iterate the list instead of querying the database again? ---
     * We already loaded the slots at the start of runScheduling().
     * Iterating the in-memory list is much faster than hitting the database
     * again for each order. We update the slot's booked count in memory
     * as we go, so isBookable() stays accurate throughout the loop.
     *
     * @param slots list of candidate slots (sorted earliest first)
     * @return the first bookable slot, or null if none remain
     */
    private TimeSlot findFirstAvailableSlot(List<TimeSlot> slots) {
        for (TimeSlot slot : slots) {
            if (slot.isBookable()) {
                return slot;
            }
        }
        return null;   // all slots are full
    }

    /**
     * Maps a Priority enum to a numeric rank for sorting.
     * Higher number = higher priority = processed first.
     *
     * URGENT = 4  (processed first)
     * HIGH   = 3
     * MEDIUM = 2
     * LOW    = 1  (processed last)
     *
     * --- Why not just use the enum's ordinal()? ---
     * Enum ordinal() returns the position in the declaration (LOW=0, MEDIUM=1...).
     * This happens to match what we want, but relying on declaration order is fragile —
     * if someone adds a new enum value in the middle, ordinal() shifts.
     * An explicit switch is clearer and safer.
     *
     * @param priority the Priority enum value
     * @return integer rank (higher = more urgent)
     */
    private int priorityRank(Priority priority) {
        switch (priority) {
            case URGENT: return 4;
            case HIGH:   return 3;
            case MEDIUM: return 2;
            case LOW:    return 1;
            default:     return 0;
        }
    }
}
