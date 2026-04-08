package com.bt.deliveryapp.enums;

/**
 * Represents how urgently a delivery needs to be handled.
 * When a customer places a booking, they pick one of these priority levels.
 * The scheduling algorithm uses this to decide which deliveries get the
 * earliest time slots — URGENT always goes before LOW.
 *
 * Think of it like flight boarding: URGENT is Business Class, LOW is Economy.
 */
public enum Priority {

    LOW,      // Can wait — no rush, standard delivery window
    MEDIUM,   // Preferred sooner but flexible
    HIGH,     // Needs to go out today, prioritised in scheduling
    URGENT    // Must be handled immediately, given the earliest available slot
}
