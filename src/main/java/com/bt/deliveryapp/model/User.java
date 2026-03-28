package com.bt.deliveryapp.model;

import com.bt.deliveryapp.enums.UserRole;
import jakarta.persistence.*;

/**
 * Represents a user of the SmartDelivery app.
 *
 * --- What is a Model class? ---
 * A model class is a Java class that maps directly to a table in your database.
 * Every field (variable) in this class becomes a column in the "users" table in MySQL.
 * When you save a User object, Hibernate (our ORM tool) automatically writes a row
 * into the database for you — you never write raw SQL yourself.
 *
 * --- What is OOP here? ---
 * This is Object-Oriented Programming in action: we represent a real-world concept
 * (a user) as a Java class with its own data (name, email, role) and we can create
 * as many User objects as we need, each representing one real person.
 *
 * --- What are the @Annotations? ---
 * Annotations (the lines starting with @) are special instructions for the framework:
 * @Entity    — tells JPA "this class maps to a database table"
 * @Table     — lets you set the exact table name in MySQL
 * @Id        — marks which field is the PRIMARY KEY (unique identifier)
 * @GeneratedValue — auto-generates the ID (1, 2, 3...) so you don't set it manually
 * @Column    — customises column behaviour (nullable = false means it can't be empty)
 * @Enumerated — tells JPA to save the enum value as text (e.g. "CUSTOMER") not a number
 */
@Entity
@Table(name = "users")  // Table will be called "users" in your smartdelivery MySQL database
public class User {

    // ---- Primary Key ----
    // Every table needs a unique ID for each row. This auto-increments: 1, 2, 3...
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- User's full name ----
    // nullable = false means this column CANNOT be empty in the database
    // length = 100 means MySQL allows up to 100 characters
    @Column(nullable = false, length = 100)
    private String name;

    // ---- Email address ----
    // unique = true means no two users can have the same email (like a login username)
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    // ---- Password ----
    // In a real app this would be encrypted, but for now we store it as plain text
    @Column(nullable = false)
    private String password;

    // ---- Phone number ----
    @Column(length = 15)
    private String phone;

    // ---- Role ----
    // This links to our UserRole enum. EnumType.STRING saves "CUSTOMER"/"AGENT"/"ADMIN"
    // as readable text in the database (instead of 0/1/2 which is confusing)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    // ---- Constructors ----
    // A constructor is a special method called when you create a new User object.
    // We always need a no-argument constructor for JPA (it uses it internally).
    public User() {
    }

    // This constructor lets you create a fully-filled User in one line of code
    public User(String name, String email, String password, String phone, UserRole role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.role = role;
    }

    // ---- Getters and Setters ----
    // In Java, fields are private (hidden), and we expose them via getters/setters.
    // This is the OOP principle of ENCAPSULATION — controlling how data is accessed.
    // A getter reads the value, a setter updates it.

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    // ---- toString ----
    // This is useful for printing/logging a User object — instead of seeing
    // "com.bt.deliveryapp.model.User@3a4621" you get readable info
    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', email='" + email + "', role=" + role + "}";
    }
}
