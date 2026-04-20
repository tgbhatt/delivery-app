# DeliverIQ

A full-stack last-mile delivery management platform built with Java Spring Boot. Customers book deliveries, admins manage the operation, and agents handle fulfilment — all from one app.

---

## What It Does

- Customers book on-demand or scheduled deliveries through a multi-step wizard
- Admins assign delivery agents and monitor all active orders in real time
- Agents update order status as they move through the delivery journey
- Customers track their order live with a full status timeline
- Deliveries are confirmed at the door via a one-time OTP

---

## Features

- **Delivery booking** — pick up address, drop-off address, package details, priority level, and special instructions
- **Immediate & scheduled orders** — book now or pick a time slot
- **Live order tracking** — real-time status timeline from PLACED → ASSIGNED → OUT FOR DELIVERY → ARRIVED → DELIVERED
- **OTP delivery confirmation** — agent enters a 4-digit code from the customer to confirm handoff
- **Recurring deliveries** — set a repeat schedule (daily, weekly, monthly) with an optional end date
- **Book Again** — re-book any past order in one click with pre-filled details
- **Admin dashboard** — live order feed, scheduled queue, agent assignment, summary stats
- **Agent dashboard** — personal workload view with active orders and upcoming schedule
- **Role-based access** — separate views and permissions for Customer, Agent, and Admin
- **Route optimisation** — agents are matched to orders based on pickup zone

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.3.5, Spring MVC |
| Database | MySQL (hosted on Railway) |
| ORM | Spring Data JPA / Hibernate |
| Templating | Thymeleaf |
| Frontend | HTML, CSS, Bootstrap Icons |
| Build tool | Maven |
| Version control | Git / GitHub |

---

## Team

Built by Bhavya Patel and Tanushree Bhatt as a collaborative full-stack project.
