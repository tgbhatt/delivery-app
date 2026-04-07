package com.bt.deliveryapp.model;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Records a single status change event for a delivery.
 *
 * --- What is this for? ---
 * Every time a delivery's status changes (e.g. PLACED → ASSIGNED, or
 * ASSIGNED → OUT_FOR_DELIVERY), one TrackingEvent row is saved to the database.
 * Over time, a delivery builds up a list of these events — that list IS the
 * tracking timeline the customer sees.
 *
 * --- Real-world analogy ---
 * Think of it like a courier's parcel tracking page:
 *   "10:00 — Order placed"
 *   "10:15 — Agent assigned"
 *   "11:30 — Out for delivery"
 *   "12:05 — Delivered"
 * Each of those lines is one TrackingEvent row in the database.
 *
 * --- Why store fromStatus AND toStatus? ---
 * The state machine in TrackingService needs to validate that a transition
 * is legal. Storing both ends of each transition makes it easy to display
 * what changed ("moved from ASSIGNED to OUT_FOR_DELIVERY") and to audit
 * if something went wrong.
 *
 * --- OOP concepts used here ---
 * @ManyToOne relationships: many events belong to one delivery, many events
 * can be created by one user (agent). This models real-world relationships
 * in code using object references instead of raw foreign key IDs.
 *
 * --- This is Bhavya's model (Feature 4: Live Status Tracking) ---
 */
@Entity
@Table(name = "tracking_events")
public class TrackingEvent {

    // ---- Primary Key ----
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- Which delivery does this event belong to? ----
    // Many tracking events can belong to one delivery request.
    // The foreign key in the DB will be "delivery_request_id".
    @ManyToOne
    @JoinColumn(name = "delivery_request_id", nullable = false)
    private DeliveryRequest deliveryRequest;

    // ---- What status was it BEFORE this change? ----
    // e.g. ASSIGNED — needed so we can display "moved from X to Y"
    // and so the state machine can enforce valid transitions
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatusEnum fromStatus;

    // ---- What status did it change TO? ----
    // e.g. OUT_FOR_DELIVERY
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatusEnum toStatus;

    // ---- Who made this status change? ----
    // Usually the delivery agent. Could be null for system-generated events
    // (e.g. when the system auto-assigns a slot, it's a system event, not human).
    @ManyToOne
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    // ---- When did this change happen? ----
    @Column(nullable = false)
    private LocalDateTime timestamp;

    // ---- Optional note from the agent ----
    // e.g. "Nobody home", "Left at door", "Wrong address — called customer"
    // Used especially when status is FAILED so there's a reason on record.
    @Column(length = 300)
    private String note;

    // ---- Constructors ----
    public TrackingEvent() {
    }

    // Used when an agent manually updates status (most common case)
    public TrackingEvent(DeliveryRequest deliveryRequest,
                         DeliveryStatusEnum fromStatus,
                         DeliveryStatusEnum toStatus,
                         User updatedBy,
                         String note) {
        this.deliveryRequest = deliveryRequest;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.updatedBy = updatedBy;
        this.note = note;
        this.timestamp = LocalDateTime.now();
    }

    // Used for system-generated events (no human actor, e.g. auto slot assignment)
    public TrackingEvent(DeliveryRequest deliveryRequest,
                         DeliveryStatusEnum fromStatus,
                         DeliveryStatusEnum toStatus) {
        this.deliveryRequest = deliveryRequest;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.updatedBy = null; // System-generated
        this.note = "System update";
        this.timestamp = LocalDateTime.now();
    }

    // ---- Getters and Setters ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DeliveryRequest getDeliveryRequest() {
        return deliveryRequest;
    }

    public void setDeliveryRequest(DeliveryRequest deliveryRequest) {
        this.deliveryRequest = deliveryRequest;
    }

    public DeliveryStatusEnum getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(DeliveryStatusEnum fromStatus) {
        this.fromStatus = fromStatus;
    }

    public DeliveryStatusEnum getToStatus() {
        return toStatus;
    }

    public void setToStatus(DeliveryStatusEnum toStatus) {
        this.toStatus = toStatus;
    }

    public User getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(User updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public String toString() {
        return "TrackingEvent{id=" + id +
               ", delivery=" + (deliveryRequest != null ? deliveryRequest.getId() : "null") +
               ", " + fromStatus + " → " + toStatus +
               ", at=" + timestamp + "}";
    }
}
