package com.bt.deliveryapp.enums;

/**
 * Represents every possible status a delivery can be in.
 * Think of this like a delivery's "life stages" — it always moves
 * forward through these steps in order (no jumping from PLACED to DELIVERED).
 *
 * This is an "enum" (short for enumeration) — a special Java type that holds
 * a fixed list of named constant values. We use it so nobody can accidentally
 * type "Delivered" or "delivered" — only DELIVERED is allowed.
 */
public enum DeliveryStatusEnum {

    PLACED,            // Customer just submitted the delivery request
    SCHEDULED,         // A time slot has been assigned to this delivery
    ASSIGNED,          // A delivery agent has been assigned to pick it up
    OUT_FOR_DELIVERY,  // Agent is currently on the way to deliver it
    ARRIVED,           // Agent has reached the customer's address — OTP generated, waiting for confirmation
    DELIVERED,         // OTP confirmed — successfully handed to the recipient
    FAILED,            // Delivery attempt was unsuccessful (nobody home, wrong address, etc.)
    RESCHEDULED        // A failed delivery has been given a new time slot
}
