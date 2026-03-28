package com.bt.deliveryapp.enums;

/**
 * Represents the three types of users in this application.
 * Each role sees a different view and can do different things:
 *
 * - CUSTOMER: places delivery requests and tracks their packages
 * - AGENT: receives assigned deliveries and updates status on the road
 * - ADMIN: manages everything — assigns agents, monitors all orders
 *
 * Using an enum here means we can never have a user with role "Manager" or
 * "Staff" — only these three exact values are valid.
 */
public enum UserRole {

    CUSTOMER,  // A person sending or receiving a package
    AGENT,     // A delivery person on the ground
    ADMIN      // Business operator managing the whole system
}
