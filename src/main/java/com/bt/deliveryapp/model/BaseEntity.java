package com.bt.deliveryapp.model;

import jakarta.persistence.*;

/**
 * BaseEntity — the abstract parent class for all model classes.
 *
 * ─── What is an abstract class? ─────────────────────────────────────────────
 * An abstract class is a class that CANNOT be instantiated directly.
 * You cannot write:  new BaseEntity()  — that is not allowed.
 * It exists only to be extended (inherited) by other classes.
 *
 * Think of it like a blueprint template. "Vehicle" is abstract —
 * you cannot drive a "Vehicle", you drive a Car or a Bike.
 * Similarly, BaseEntity is never used directly — User, DeliveryRequest,
 * and TimeSlot extend it and become concrete usable classes.
 *
 * ─── What is @MappedSuperclass? ─────────────────────────────────────────────
 * In JPA (our database layer), @MappedSuperclass tells Hibernate:
 * "Do NOT create a separate table for this class. Instead, include its
 * fields in the tables of the classes that extend it."
 *
 * So the 'id' field defined here will appear as a column in the
 * users table, delivery_requests table, and time_slots table —
 * but there will be NO base_entity table in MySQL.
 *
 * ─── What is the abstract method getDisplayName()? ──────────────────────────
 * An abstract method has no body — it is just a declaration:
 *   public abstract String getDisplayName();
 *
 * This forces every subclass to provide its OWN implementation.
 * User, DeliveryRequest, and TimeSlot each override it differently.
 * This is POLYMORPHISM: the same method name produces different
 * behaviour depending on which class it is called on.
 *
 * Example:
 *   BaseEntity e1 = new User(...);
 *   BaseEntity e2 = new DeliveryRequest(...);
 *   e1.getDisplayName() → "User: Tanushree (CUSTOMER)"
 *   e2.getDisplayName() → "Order #5: Pizza Palace → 12 MG Road"
 *
 * Same method name, completely different output — that is polymorphism.
 *
 * ─── OOP Concepts demonstrated ──────────────────────────────────────────────
 * - Abstract class         : BaseEntity cannot be instantiated directly
 * - Inheritance            : User, DeliveryRequest, TimeSlot all extend this
 * - Method overriding      : each subclass provides its own getDisplayName()
 * - Polymorphism           : a BaseEntity reference can hold any subclass object
 * - Encapsulation          : id field is private, accessed via getId() / setId()
 */
@MappedSuperclass
public abstract class BaseEntity {

    // ---- Primary Key — shared by ALL entities --------------------------------
    // Every table in our app has an auto-incrementing Long id column.
    // By putting it here in BaseEntity, we avoid repeating this in every model.
    // This is the DRY principle: Don't Repeat Yourself.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- Getter and Setter for id -------------------------------------------
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    // ---- Abstract method — must be overridden in every subclass -------------
    // This is the method that enables polymorphism.
    // Every entity knows how to describe itself in a human-readable way,
    // but each one does it differently.
    public abstract String getDisplayName();
}
