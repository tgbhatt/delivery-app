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
public class DeliveryRequest {

    // ---- Primary Key ----
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    // We explicitly name the column "is_immediate" to prevent Hibernate
    // from creating a duplicate column with a different name.
    @Column(name = "is_immediate", nullable = false)
    private boolean isImmediate;

    // ---- Which time slot was assigned? ----
    // Only relevant when isImmediate = false (scheduled orders)
    // Null for immediate orders — they don't get a slot
    @ManyToOne
    @JoinColumn(name = "time_slot_id")
    private TimeSlot timeSlot;

    // ---- Which agent is handling this order? ----
    // Null until an agent is assigned (Feature 3)
    @ManyToOne
    @JoinColumn(name = "agent_id")
    private Agent agent;

    // ---- Current status of this order ----
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatusEnum status;

    // ---- Timestamps ----
    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ---- Which zone is the pickup location in? ----
    // Used by RouteOptimisationService to assign a nearby delivery agent
    // e.g. "NORTH", "SOUTH", "CENTRAL" — nullable because zone may not always be known
    @Column(length = 50)
    private String pickupZone;

    // ---- Any special delivery instructions? ----
    // e.g. "Leave at door", "Call on arrival", "No contactless"
    @Column(length = 500)
    private String specialInstructions;

    // ---- OTP fields for delivery confirmation ----
    // When an agent clicks "I've Arrived", a 4-digit OTP is generated and stored here.
    // The customer sees this OTP on their tracking page and reads it aloud to the agent.
    // Once the agent enters the correct OTP, the order is marked DELIVERED.
    // After delivery is confirmed (or failed), this is cleared back to null.
    @Column(length = 4)
    private String deliveryOtp;

    // Records exactly when the OTP was generated — useful for expiry checks in the future.
    private LocalDateTime otpGeneratedAt;

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
        this.isImmediate = true;              // Order Now path
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
        this.isImmediate = false;             // Schedule for Later path
        this.status = DeliveryStatusEnum.PLACED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ---- Getters and Setters ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
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
        return isImmediate;
    }

    public void setImmediate(boolean immediate) {
        isImmediate = immediate;
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(TimeSlot timeSlot) {
        this.timeSlot = timeSlot;
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
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

    public String getPickupZone() {
        return pickupZone;
    }

    public void setPickupZone(String pickupZone) {
        this.pickupZone = pickupZone;
    }

    public String getSpecialInstructions() {
        return specialInstructions;
    }

    public void setSpecialInstructions(String specialInstructions) {
        this.specialInstructions = specialInstructions;
    }

    // ---- Alias getters for Thymeleaf templates ----
    // The HTML templates were written using short, friendly field names like
    // "pickupAddress" and "currentStatus". The actual model fields have longer,
    // more descriptive names. These alias getters bridge that gap — Thymeleaf
    // calls ${order.pickupAddress} which invokes getPickupAddress() below.
    // This way the templates stay readable and we don't rename the real fields.

    // Templates use: ${order.pickupAddress}
    public String getPickupAddress() {
        return restaurantAddress;
    }

    // Templates use: ${order.deliveryAddress}
    public String getDeliveryAddress() {
        return customerAddress;
    }

    // Templates use: ${order.packageDescription}
    public String getPackageDescription() {
        return orderDescription;
    }

    // Templates use: ${order.currentStatus}
    public DeliveryStatusEnum getCurrentStatus() {
        return status;
    }

    public String getDeliveryOtp() {
        return deliveryOtp;
    }

    public void setDeliveryOtp(String deliveryOtp) {
        this.deliveryOtp = deliveryOtp;
    }

    public LocalDateTime getOtpGeneratedAt() {
        return otpGeneratedAt;
    }

    public void setOtpGeneratedAt(LocalDateTime otpGeneratedAt) {
        this.otpGeneratedAt = otpGeneratedAt;
    }

    @Override
    public String toString() {
        return "DeliveryRequest{id=" + id +
               ", from='" + restaurantAddress +
               "', to='" + customerAddress +
               "', immediate=" + isImmediate +
               ", priority=" + priority +
               ", status=" + status + "}";
    }
}
