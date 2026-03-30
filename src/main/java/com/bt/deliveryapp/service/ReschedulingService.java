package com.bt.deliveryapp.service;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.TimeSlot;
import com.bt.deliveryapp.repository.DeliveryRequestRepository;
import com.bt.deliveryapp.repository.TimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * ReschedulingService — handles FAILED deliveries that need a second attempt.
 *
 * ─── What problem does this solve? ───────────────────────────────────────────
 * When a delivery fails (customer not home, wrong address, etc.) the order
 * moves to FAILED status. The delivery attempt is over, but the order is
 * not fulfilled — the customer still needs their food.
 *
 * An admin can then reschedule the order: find a new available time slot
 * and try again. The order moves from FAILED → RESCHEDULED, gets a new
 * slot assigned, and re-enters the delivery pipeline.
 *
 * ─── How does this connect to the State Machine? ─────────────────────────────
 * This service handles the final transition in the state machine:
 *
 *   OUT_FOR_DELIVERY → FAILED  (agent marks it failed — DeliveryAgentService)
 *                        ↓
 *                    RESCHEDULED  (admin gives it a new slot — this service)
 *                        ↓
 *                    ASSIGNED  (Feature 3 assigns a new agent — same as normal flow)
 *
 * ─── OOP concepts here ───────────────────────────────────────────────────────
 * - Encapsulation   : findBestSlot() is private — internal detail hidden
 * - Abstraction     : admin calls rescheduleOrder() without knowing slot logic
 * - State machine   : only FAILED orders can be rescheduled (enforced here)
 * - Method overloading : two versions of rescheduleOrder() — one where admin
 *                        picks the slot, one where the system picks automatically
 */
@Service
public class ReschedulingService {

    @Autowired
    private DeliveryRequestRepository deliveryRequestRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 1: Admin manually picks a new slot for a failed order
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reschedules a FAILED order to a specific slot chosen by the admin.
     *
     * --- Method overloading ---
     * This method takes a slotId parameter — admin specifies exactly which slot.
     * The second rescheduleOrder() below takes no slotId — system picks automatically.
     * Same method name, different parameters = METHOD OVERLOADING in Java.
     * This demonstrates a required OOP concept from the assignment checklist.
     *
     * @param orderId the failed order to reschedule
     * @param slotId  the specific slot the admin has chosen
     * @return the updated order, or empty if reschedule was not allowed
     */
    @Transactional
    public Optional<DeliveryRequest> rescheduleOrder(Long orderId, Long slotId) {

        // Find the order
        Optional<DeliveryRequest> orderOpt = deliveryRequestRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return Optional.empty();
        }

        DeliveryRequest order = orderOpt.get();

        // State machine check: only FAILED orders can be rescheduled
        if (order.getStatus() != DeliveryStatusEnum.FAILED) {
            return Optional.empty();
        }

        // Find the chosen slot
        Optional<TimeSlot> slotOpt = timeSlotRepository.findById(slotId);
        if (slotOpt.isEmpty()) {
            return Optional.empty();
        }

        TimeSlot slot = slotOpt.get();

        // Check the slot is still bookable
        if (!slot.isBookable()) {
            return Optional.empty();
        }

        // Assign the new slot and update status to RESCHEDULED
        order.setTimeSlot(slot);
        order.setStatus(DeliveryStatusEnum.RESCHEDULED);
        slot.incrementBookedCount();

        timeSlotRepository.save(slot);
        return Optional.of(deliveryRequestRepository.save(order));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 2: System automatically finds the best slot (overloaded version)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reschedules a FAILED order by automatically finding the earliest available slot.
     *
     * --- This is METHOD OVERLOADING ---
     * Same name as the method above (rescheduleOrder) but different parameters.
     * This version takes only orderId — no slotId — because the system chooses
     * the slot automatically using the same greedy "earliest first" logic
     * from SlotSchedulingService.
     *
     * Java distinguishes the two methods by their parameter lists, not names.
     * This is called the method signature.
     *
     * @param orderId the failed order to reschedule
     * @return the updated order, or empty if no slot is available
     */
    @Transactional
    public Optional<DeliveryRequest> rescheduleOrder(Long orderId) {

        // Find the order
        Optional<DeliveryRequest> orderOpt = deliveryRequestRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            return Optional.empty();
        }

        DeliveryRequest order = orderOpt.get();

        // State machine check
        if (order.getStatus() != DeliveryStatusEnum.FAILED) {
            return Optional.empty();
        }

        // Find the best available slot automatically — same greedy logic as Feature 2
        TimeSlot bestSlot = findBestSlot();

        if (bestSlot == null) {
            return Optional.empty();   // no slots available right now
        }

        order.setTimeSlot(bestSlot);
        order.setStatus(DeliveryStatusEnum.RESCHEDULED);
        bestSlot.incrementBookedCount();

        timeSlotRepository.save(bestSlot);
        return Optional.of(deliveryRequestRepository.save(order));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 3: Get all failed orders waiting to be rescheduled
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all orders currently in FAILED status.
     * Used by the admin dashboard to show the list of failed deliveries
     * that need attention.
     *
     * @return list of FAILED orders
     */
    public List<DeliveryRequest> getFailedOrders() {
        return deliveryRequestRepository.findByStatus(DeliveryStatusEnum.FAILED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 4: Get all rescheduled orders (audit trail)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all orders that have been rescheduled.
     * Useful for admin reporting — how many deliveries needed a second attempt?
     *
     * @return list of RESCHEDULED orders
     */
    public List<DeliveryRequest> getRescheduledOrders() {
        return deliveryRequestRepository.findByStatus(DeliveryStatusEnum.RESCHEDULED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PRIVATE HELPER — find the earliest available slot
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Finds the earliest available time slot from today onwards.
     *
     * This is private (Encapsulation) — it is an internal detail of how
     * the automatic rescheduling picks a slot. The admin calling rescheduleOrder()
     * does not need to know how this works.
     *
     * Uses the same greedy "earliest first" strategy as SlotSchedulingService:
     * sort by date, then by start time, take the first bookable one.
     *
     * @return the earliest bookable TimeSlot, or null if none exists
     */
    private TimeSlot findBestSlot() {
        List<TimeSlot> available =
                timeSlotRepository.findBySlotDateGreaterThanEqualAndAvailableTrue(LocalDate.now());

        return available.stream()
                .sorted(Comparator.comparing(TimeSlot::getSlotDate)
                                  .thenComparing(TimeSlot::getStartTime))
                .filter(TimeSlot::isBookable)
                .findFirst()
                .orElse(null);
    }
}
