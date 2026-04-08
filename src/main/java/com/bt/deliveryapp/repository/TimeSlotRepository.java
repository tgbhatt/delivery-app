package com.bt.deliveryapp.repository;

import com.bt.deliveryapp.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for TimeSlot — handles all database reads and writes for time slots.
 *
 * Used by: Feature 2 (slot scheduling — Tanushree's feature),
 *          DataLoader (temp testing).
 *
 * --- This is a shared repository used across multiple features ---
 */
@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    // Find all available slots on a specific date — used by the booking form
    List<TimeSlot> findBySlotDateAndAvailableTrue(LocalDate slotDate);

    // Find all slots on a specific date (available or not) — used by admin
    List<TimeSlot> findBySlotDate(LocalDate slotDate);
}
