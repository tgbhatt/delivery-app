package com.bt.deliveryapp.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Represents a delivery time window on a specific date.
 *
 * --- What is a TimeSlot? ---
 * When a customer places a booking, they choose a preferred time window —
 * for example "March 28, between 10:00 AM and 12:00 PM". That window is a TimeSlot.
 * The system stores all available slots in this table and marks them as
 * available or booked. Feature 2 (Slot Scheduling) will read this table
 * and find the best free slot for a new delivery.
 *
 * --- Why is this a separate class? ---
 * This is OOP design. Instead of storing "slotDate", "slotStartTime", "slotEndTime"
 * directly inside DeliveryRequest, we pull it out into its own class.
 * This means many DeliveryRequests can reference the same TimeSlot object —
 * the slot exists independently in its own table. This is NORMALISATION in databases:
 * avoid repeating the same data across multiple rows.
 *
 * --- How does it connect to DeliveryRequest? ---
 * DeliveryRequest has a field "preferredTimeSlot" which is a TimeSlot object.
 * In the database, this becomes a "time_slot_id" foreign key column in delivery_requests.
 */
@Entity
@Table(name = "time_slots")
public class TimeSlot {

    // ---- Primary Key ----
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- The date this slot is for ----
    // e.g. 2026-03-28
    @Column(nullable = false)
    private LocalDate slotDate;

    // ---- Start time of the delivery window ----
    // e.g. 10:00 AM
    @Column(nullable = false)
    private LocalTime startTime;

    // ---- End time of the delivery window ----
    // e.g. 12:00 PM (so the window is 2 hours)
    @Column(nullable = false)
    private LocalTime endTime;

    // ---- Is this slot still available? ----
    // When a delivery is assigned to this slot, we flip this to false
    // so no other delivery can be booked into the same window.
    // This is the heart of Feature 2 — conflict prevention.
    @Column(nullable = false)
    private boolean available = true;

    // ---- Label for display ----
    // A human-readable name like "Morning (10:00 - 12:00)"
    // Makes it easy to show nicely in HTML templates
    @Column(length = 100)
    private String label;

    // ---- How many deliveries can fit in this slot ----
    // A slot doesn't have to be exclusive — maybe 3 agents can cover it.
    // Default is 1, but admin can configure higher capacity.
    @Column(nullable = false)
    private int capacity = 1;

    // ---- How many deliveries are currently booked into this slot ----
    // When this equals capacity, the slot is full.
    @Column(nullable = false)
    private int bookedCount = 0;

    // ---- Constructors ----
    public TimeSlot() {
    }

    public TimeSlot(LocalDate slotDate, LocalTime startTime, LocalTime endTime, String label, int capacity) {
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.label = label;
        this.capacity = capacity;
        this.bookedCount = 0;
        this.available = true;
    }

    // ---- Business logic method ----
    // This is OOP: the TimeSlot object KNOWS whether it is still bookable.
    // We keep this logic inside the class (Encapsulation) rather than checking
    // it externally in every service that needs it.
    public boolean isBookable() {
        return available && bookedCount < capacity;
    }

    // ---- Called when a delivery is successfully assigned to this slot ----
    public void incrementBookedCount() {
        this.bookedCount++;
        if (this.bookedCount >= this.capacity) {
            this.available = false;  // Slot is now full — mark unavailable
        }
    }

    // ---- Getters and Setters ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public void setSlotDate(LocalDate slotDate) {
        this.slotDate = slotDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getBookedCount() {
        return bookedCount;
    }

    public void setBookedCount(int bookedCount) {
        this.bookedCount = bookedCount;
    }

    @Override
    public String toString() {
        return "TimeSlot{id=" + id + ", date=" + slotDate +
               ", " + startTime + "-" + endTime +
               ", available=" + available + "}";
    }
}
