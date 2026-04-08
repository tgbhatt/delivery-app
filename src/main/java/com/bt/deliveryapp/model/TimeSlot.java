package com.bt.deliveryapp.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Represents a bookable time window for a scheduled delivery.
 *
 * --- What is this for? ---
 * When a customer chooses "Schedule for Later", they pick from available
 * time windows like "10am–12pm on March 30". Each of those windows is a
 * TimeSlot row in the database.
 *
 * The Admin creates these slots in advance. Feature 2's scheduling algorithm
 * (Tanushree's) checks which slots still have capacity before assigning one.
 *
 * --- Real-world analogy ---
 * Think of a doctor's appointment system — the doctor (admin) sets available
 * slots, patients (customers) book into them, and once a slot is full,
 * no more bookings are allowed.
 *
 * --- OOP concept: Encapsulation ---
 * All fields are private. They can only be read/changed through the
 * getters and setters below — this protects the data from being
 * accidentally corrupted.
 */
@Entity
@Table(name = "time_slots")
public class TimeSlot {

    // ---- Primary Key ----
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- Which date is this slot for? ----
    // e.g. 2026-03-30
    @Column(nullable = false)
    private LocalDate slotDate;

    // ---- Start and end time of this window ----
    // e.g. startTime = 10:00, endTime = 12:00
    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    // ---- Human-readable label ----
    // e.g. "10am – 12pm" — shown on the booking form so customers understand it easily
    @Column(length = 50)
    private String label;

    // ---- Is this slot still open for new bookings? ----
    // Set to false when bookedCount reaches capacity
    @Column(nullable = false)
    private boolean available;

    // ---- Max deliveries allowed in this window ----
    // Admin decides this — e.g. capacity = 10 means max 10 orders per slot
    @Column(nullable = false)
    private int capacity;

    // ---- How many orders have already been booked into this slot? ----
    // When bookedCount == capacity, available is set to false
    @Column(nullable = false)
    private int bookedCount;

    // ---- Constructors ----
    public TimeSlot() {
    }

    public TimeSlot(LocalDate slotDate, LocalTime startTime, LocalTime endTime,
                    String label, int capacity) {
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.label = label;
        this.capacity = capacity;
        this.bookedCount = 0;
        this.available = true; // A new slot starts as available
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

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
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

    // ---- Template alias getter ----

    /** Alias for getSlotDate() — used in customer/orders.html */
    public LocalDate getDate() {
        return slotDate;
    }

    // ---- Business logic methods ----

    /**
     * Returns true if this slot can still accept new bookings.
     * A slot is bookable when it is marked available AND hasn't reached capacity.
     * Used by SlotSchedulingService and ReschedulingService before assigning a slot.
     */
    public boolean isBookable() {
        return available && bookedCount < capacity;
    }

    /**
     * Increments the booked count by one.
     * If the slot is now full (bookedCount == capacity), marks it unavailable.
     * Called whenever an order is assigned to this slot.
     */
    public void incrementBookedCount() {
        this.bookedCount++;
        if (this.bookedCount >= this.capacity) {
            this.available = false;
        }
    }

    @Override
    public String toString() {
        return "TimeSlot{id=" + id + ", date=" + slotDate + ", " + label +
               ", booked=" + bookedCount + "/" + capacity + "}";
    }
}
