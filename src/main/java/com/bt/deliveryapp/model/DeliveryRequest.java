package com.bt.deliveryapp.model;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.enums.Priority;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a single food delivery order placed by a customer.
 *
 * This is the core model of the entire app. Every order — whether placed
 * immediately or scheduled for later — lives in this table.
 *
 * --- The two order types ---
 * The field "isImmediate" controls which path this order takes:
 *
 *   isImmediate = true  → Order Now path
 *     Customer wants delivery ASAP. Goes into a priority queue.
 *     Status flow: PLACED → ASSIGNED → OUT_FOR_DELIVERY → DELIVERED / FAILED
 *
 *   isImmediate = false → Schedule for Later path
 *     Customer picked a future time window. Goes through slot scheduling.
 *     Status flow: PLACED → SCHEDULED → ASSIGNED → OUT_FOR_DELIVERY → DELIVERED / FAILED / RESCHEDULED
 *
 * --- Relationships to other tables ---
 * @ManyToOne to User        — many orders can belong to one customer
 * @ManyToOne to TimeSlot    — many orders can share one time slot (for scheduled orders)
 */
@Entity
@Table(name = "delivery_requests")
public class DeliveryRequest extends BaseEntity {
    // Inherits id, getId(), setId(), and the getDisplayName() contract from BaseEntity.

    // ---- Who placed this order? ----
    // Links to the users table via a foreign key column "customer_id"
    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    // ---- Where is the food being picked up from? ----
    // The restaurant's address
    @Column(nullable = false, length = 255)
    private String restaurantAddress;

    // ---- Where is it being delivered to? ----
    // The customer's delivery address
    @Column(nullable = false, length = 255)
    private String customerAddress;

    // ---- What was ordered? ----
    // A short description of the food items e.g. "2x Butter Chicken, 1x Garlic Naan"
    @Column(length = 300)
    private String orderDescription;

    // ---- How urgent is this order? ----
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    // ---- Is this an immediate order or a scheduled one? ----
    // true  = Order Now  → skip slot scheduling, go straight into the queue
    // false = Schedule for Later → run through the slot scheduling algorithm
    @Column(nullable = false)
    private boolean immediate;

    // ---- Which time slot was assigned? ----
    // Only relevant when isImmediate = false (scheduled orders)
    // Null for immediate orders — they don't get a slot
    @ManyToOne
    @JoinColumn(name = "time_slot_id")
    private TimeSlot timeSlot;

    // ---- Current status of this order ----
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatusEnum status;

    // ---- Timestamps ----
    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ---- Which delivery zone does the pickup address fall in? ----
    // Used by Feature 3 Day 4 (zone-based optimisation) to match orders to
    // agents who are already in the same area.
    //
    // Possible values: "NORTH", "SOUTH", "EAST", "WEST", "CENTRAL"
    // Nullable — older orders placed before this field was added won't have a zone.
    // The algorithm falls back gracefully when this is null (picks any available agent).
    @Column(length = 20)
    private String pickupZone;

    // ---- Which agent is assigned to this order? ----
    // Null when the order is first placed — Feature 3 (route optimisation) will
    // assign the nearest available agent and populate this field.
    // nullable = true because no agent is assigned at booking time.
    @ManyToOne
    @JoinColumn(name = "agent_id")
    private User agent;

    // ---- Any special delivery instructions? ----
    // e.g. "Leave at door", "Call on arrival", "No contactless"
    @Column(length = 500)
    private String specialInstructions;

    // ---- Constructors ----
    public DeliveryRequest() {
    }

    // Constructor for an IMMEDIATE order (Order Now)
    public DeliveryRequest(User customer, String restaurantAddress, String customerAddress,
                           String orderDescription, Priority priority, String specialInstructions) {
        this.customer = customer;
        this.restaurantAddress = restaurantAddress;
        this.customerAddress = customerAddress;
        this.orderDescription = orderDescription;
        this.priority = priority;
        this.specialInstructions = specialInstructions;
        this.immediate = true;                // Order Now path
        this.status = DeliveryStatusEnum.PLACED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Constructor for a SCHEDULED order (Schedule for Later)
    // Takes a TimeSlot in addition to the standard fields
    public DeliveryRequest(User customer, String restaurantAddress, String customerAddress,
                           String orderDescription, Priority priority,
                           TimeSlot timeSlot, String specialInstructions) {
        this.customer = customer;
        this.restaurantAddress = restaurantAddress;
        this.customerAddress = customerAddress;
        this.orderDescription = orderDescription;
        this.priority = priority;
        this.specialInstructions = specialInstructions;
        this.timeSlot = timeSlot;
        this.immediate = false;               // Schedule for Later path
        this.status = DeliveryStatusEnum.PLACED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ---- Getters and Setters ----

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
    }

    public User getAgent() {
        return agent;
    }

    public void setAgent(User agent) {
        this.agent = agent;
    }

    public String getRestaurantAddress() {
        return restaurantAddress;
    }

    public void setRestaurantAddress(String restaurantAddress) {
        this.restaurantAddress = restaurantAddress;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(String customerAddress) {
        this.customerAddress = customerAddress;
    }

    public String getOrderDescription() {
        return orderDescription;
    }

    public void setOrderDescription(String orderDescription) {
        this.orderDescription = orderDescription;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public boolean isImmediate() {
        return immediate;
    }

    public void setImmediate(boolean immediate) {
        this.immediate = immediate;
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(TimeSlot timeSlot) {
        this.timeSlot = timeSlot;
    }

    public DeliveryStatusEnum getStatus() {
        return status;
    }

    public void setStatus(DeliveryStatusEnum status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getSpecialInstructions() {
        return specialInstructions;
    }

    public void setSpecialInstructions(String specialInstructions) {
        this.specialInstructions = specialInstructions;
    }

    public String getPickupZone() {
        return pickupZone;
    }

    public void setPickupZone(String pickupZone) {
        this.pickupZone = pickupZone;
    }

    @Override
    public String getDisplayName() {
        return "Order #" + getId() + ": " + restaurantAddress + " → " + customerAddress;
    }

    @Override
    public String toString() {
        return "DeliveryRequest{id=" + getId() +
               ", from='" + restaurantAddress +
               "', to='" + customerAddress +
               "', immediate=" + immediate +
               ", priority=" + priority +
               ", status=" + status + "}";
    }
}
