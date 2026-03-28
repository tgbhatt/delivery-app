package com.bt.deliveryapp.repository;

import com.bt.deliveryapp.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for querying the time_slots table.
 *
 * --- Why do we need this? ---
 * Feature 1 (Booking) needs to let a customer pick a time slot when
 * they schedule an order. This repository lets us:
 *   1. Show all available slots on a given date (so the customer can choose)
 *   2. Find all slots that are still bookable (not yet full)
 *
 * Feature 2 (Slot Scheduling Algorithm) will also use this repository
 * to find the best non-conflicting slot using the greedy algorithm.
 *
 * --- How Spring Data JPA works ---
 * We write method names in a special format and Spring automatically
 * generates the correct SQL query. We never write SQL manually.
 *
 * Example: findBySlotDate(LocalDate date)
 *   → SELECT * FROM time_slots WHERE slot_date = ?
 *
 * Example: findBySlotDateAndAvailableTrue(LocalDate date)
 *   → SELECT * FROM time_slots WHERE slot_date = ? AND available = true
 */
@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    /**
     * Get all time slots for a specific date.
     * Used to display the full list of slots to the customer when they
     * are choosing a delivery window.
     *
     * Example usage:
     *   List<TimeSlot> slots = timeSlotRepository.findBySlotDate(LocalDate.of(2026, 3, 30));
     */
    List<TimeSlot> findBySlotDate(LocalDate slotDate);

    /**
     * Get only the available (not fully booked) slots for a specific date.
     * Used in the booking form — we only show slots the customer can actually pick.
     *
     * "AndAvailableTrue" is Spring's way of saying WHERE available = true.
     */
    List<TimeSlot> findBySlotDateAndAvailableTrue(LocalDate slotDate);

    /**
     * Get all available slots across all future dates.
     * Used by Feature 2 (greedy scheduling) to find the best free slot
     * from the entire pool of upcoming availability.
     *
     * "And" chains two conditions. "AvailableTrue" means available = true.
     * "SlotDateGreaterThanEqual" means slot_date >= the given date.
     */
    List<TimeSlot> findBySlotDateGreaterThanEqualAndAvailableTrue(LocalDate fromDate);

}
