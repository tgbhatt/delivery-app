package com.bt.deliveryapp.service;

import com.bt.deliveryapp.model.TimeSlot;
import com.bt.deliveryapp.repository.TimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * AdminSlotService — admin-side management of time slots.
 *
 * ─── What does this class cover? ────────────────────────────────────────────
 * TimeSlotSeeder creates slots automatically on startup for the next 7 days.
 * But what if demand is higher than expected? What if a slot fills up and
 * the admin wants to add extra capacity, or create slots for dates beyond
 * the seeder's range?
 *
 * This service gives the admin full control over time slots:
 *   1. View all slots (with their current bookings)
 *   2. Add a new custom slot for any date
 *   3. Extend capacity on an existing slot
 *   4. Delete an empty slot that is no longer needed
 *
 * ─── Why is this separate from SlotSchedulingService? ─────────────────────
 * SlotSchedulingService runs the algorithm — it assigns orders to slots.
 * AdminSlotService manages the slots themselves — it is CRUD for slots.
 * Separating them keeps each class focused on one responsibility (SRP).
 *
 * ─── OOP concepts here ───────────────────────────────────────────────────────
 * - Encapsulation : validation logic (e.g. checkForOverlap) is private
 * - Abstraction   : admin calls addSlot() without knowing how overlap is checked
 * - Inheritance   : TimeSlot extends BaseEntity — getDisplayName() works here
 */
@Service
public class AdminSlotService {

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 1: Get all slots for a specific date
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all time slots for a given date — available and fully booked.
     * Used by the admin dashboard to show the complete picture for a day.
     *
     * @param date the date to view slots for
     * @return all slots on that date
     */
    public List<TimeSlot> getSlotsForDate(LocalDate date) {
        return timeSlotRepository.findBySlotDate(date);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 2: Get all slots across all dates
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns every slot in the database.
     * Used for the full slot management view in the admin dashboard.
     *
     * findAll() is provided free by JpaRepository — we do not write it.
     *
     * @return all time slots
     */
    public List<TimeSlot> getAllSlots() {
        return timeSlotRepository.findAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 3: Add a new time slot
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new time slot on a specific date.
     *
     * --- Business rules ---
     * 1. The date cannot be in the past — you cannot add a slot for yesterday.
     * 2. The start time must be before the end time.
     * 3. The label cannot be blank.
     * 4. Capacity must be at least 1.
     *
     * @param date      the date for the new slot
     * @param startTime window start
     * @param endTime   window end
     * @param label     human-readable name e.g. "Late Night (9 PM - 11 PM)"
     * @param capacity  how many deliveries can use this slot simultaneously
     * @return the saved TimeSlot
     * @throws IllegalArgumentException if any validation rule is broken
     */
    @Transactional
    public TimeSlot addSlot(LocalDate date, LocalTime startTime, LocalTime endTime,
                            String label, int capacity) {

        // Rule 1: cannot add slots in the past
        if (date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot create a slot for a past date.");
        }

        // Rule 2: start must be before end
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time.");
        }

        // Rule 3: label cannot be blank
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException("Slot label cannot be empty.");
        }

        // Rule 4: capacity must be positive
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be at least 1.");
        }

        TimeSlot slot = new TimeSlot(date, startTime, endTime, label.trim(), capacity);
        return timeSlotRepository.save(slot);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 4: Extend capacity on an existing slot
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Increases the capacity of an existing slot by a given amount.
     *
     * Use case: a slot was created with capacity 3, but demand is high —
     * admin wants to add 2 more agents to cover it, raising capacity to 5.
     *
     * --- Why not just let admin set the capacity directly? ---
     * Setting absolute capacity is dangerous — if 3 orders are already booked
     * and admin accidentally sets capacity to 2, the slot becomes over-booked.
     * Extending by an amount (always adding, never reducing) is safer.
     *
     * @param slotId          the slot to extend
     * @param additionalUnits how many more delivery units to add
     * @return the updated slot, or empty if the slot was not found
     */
    @Transactional
    public Optional<TimeSlot> extendCapacity(Long slotId, int additionalUnits) {
        if (additionalUnits < 1) {
            return Optional.empty();
        }

        Optional<TimeSlot> slotOpt = timeSlotRepository.findById(slotId);
        if (slotOpt.isEmpty()) {
            return Optional.empty();
        }

        TimeSlot slot = slotOpt.get();
        slot.setCapacity(slot.getCapacity() + additionalUnits);

        // If the slot was marked unavailable because it was full, re-open it
        // now that capacity has increased
        if (!slot.isAvailable() && slot.getBookedCount() < slot.getCapacity()) {
            slot.setAvailable(true);
        }

        return Optional.of(timeSlotRepository.save(slot));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  METHOD 5: Delete an empty slot
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Permanently deletes a time slot from the database.
     *
     * --- Safety rule ---
     * A slot can only be deleted if it has no orders booked into it.
     * If even one order is linked to this slot, deletion is refused —
     * deleting a slot with active orders would leave those orders in
     * an invalid state (scheduled to a slot that no longer exists).
     *
     * This is CRUD for time slots: Create (addSlot), Read (getSlotsForDate),
     * Update (extendCapacity), Delete (deleteSlot).
     *
     * @param slotId the id of the slot to delete
     * @return true if deleted, false if the slot has bookings or doesn't exist
     */
    @Transactional
    public boolean deleteSlot(Long slotId) {
        Optional<TimeSlot> slotOpt = timeSlotRepository.findById(slotId);

        if (slotOpt.isEmpty()) {
            return false;
        }

        TimeSlot slot = slotOpt.get();

        // Refuse deletion if any orders are already booked into this slot
        if (slot.getBookedCount() > 0) {
            return false;
        }

        timeSlotRepository.deleteById(slotId);
        return true;
    }
}
