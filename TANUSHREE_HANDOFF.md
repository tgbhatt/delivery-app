# Handoff Notes for Tanushree

Hi Tanushree! Pull from `dev` branch — that has all the latest code. Here's exactly where things stand and what needs doing.

---

## Credentials

| Role     | Email                    | Password   |
|----------|--------------------------|------------|
| Admin    | admin@deliveriq.com      | admin123   |
| Customer | register a new account   | any        |
| Agent    | none yet — see Fix 1 below |          |

---

## Two fixes needed before starting UI work

### Fix 1 — Admin can't create agent accounts (build this first)
There is no way to create agent accounts through the UI. The `/register` page (see `RegisterService.java`) only creates CUSTOMERs. The admin dashboard (`/admin/dashboard`) has no "Add Agent" form.

**What to build:**
- Add a POST endpoint `/admin/create-agent` to `DashboardController.java`
- It should accept: `name`, `email`, `password`, `phone`
- Save a `User` with `UserRole.AGENT` via `UserRepository`
- Save a linked `Agent` profile via `AgentRepository` (use the `Agent(User user)` constructor — it sets `available=true` and `currentDeliveryCount=0` automatically)
- Redirect back to `/admin/dashboard` with a flash success/error message
- Add a small "Add Agent" form to `admin-dashboard.html` — a modal or inline form, your call

### Fix 2 — Admin login may fail
If the admin account was created in an earlier dev session with a different password, login will fail even though the credentials look right. Run this once in terminal to reset it:
```bash
mysql -u root -proot1234 smartdelivery -e "UPDATE users SET password = 'admin123' WHERE role = 'ADMIN';"
```
Then restart the app.

---

## What's already working

- **Customer booking** (`/customer/book`) — 4-step wizard, both immediate and scheduled, priority and special instructions all wired through correctly
- **Customer orders** (`/customer/orders`) — shows all orders, Track button per card
- **Live tracking** (`/track/{orderId}`) — timeline, agent status update buttons, state machine
- **Agent dashboard** (`/agent/dashboard`) — shows orders assigned to the logged-in agent
- **Admin dashboard** (`/admin/dashboard`) — shows live + scheduled orders, assign agent dropdown (this was broken due to a model attribute name mismatch — `allAgents` vs `agents` — already fixed in the latest commit)

## The full delivery flow (once Fix 1 is done)

1. Admin creates an agent account via the new form
2. Customer registers + books a delivery
3. Admin goes to `/admin/dashboard` → assigns the agent to the order
4. Agent logs in → sees the order under "Right Now" on `/agent/dashboard`
5. Agent clicks "Update Status" → goes to tracking page → advances status through ASSIGNED → OUT_FOR_DELIVERY → DELIVERED
6. Customer can watch the timeline update at `/track/{orderId}`

---

## Your UI pages

You own the UI polish for:
- `/login`
- `/register`  
- `/customer/book`
- `/customer/orders`

Bhavya owns: `/track/{orderId}`, `/agent/dashboard`, `/admin/dashboard`

The design system (`app.css`, `fragments/layout.html`) is already in place — Apple-inspired, `#2563EB` blue, `#1E3A5F` navy, Inter font. Match that style.

---

## Branch
Work on `dev`. Push back to `dev` when done.
