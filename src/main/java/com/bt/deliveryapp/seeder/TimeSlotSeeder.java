package com.bt.deliveryapp.seeder;

import com.bt.deliveryapp.model.TimeSlot;
import com.bt.deliveryapp.repository.TimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * TimeSlotSeeder — pre-fills the time_slots table with delivery windows.
 *
 * ─── What is a Seeder? ───────────────────────────────────────────────────────
 * A seeder is a class that runs once when the app starts and inserts
 * test data into the database. Without it, the time_slots table would be
 * completely empty and there would be nothing for the scheduling algorithm
 * to assign orders into.
 *
 * Think of it like setting up a fresh restaurant — before you open for
 * business you need to stock the kitchen. The seeder stocks the database.
 *
 * ─── What is CommandLineRunner? ─────────────────────────────────────────────
 * CommandLineRunner is a Spring Boot interface with one method: run().
 * Any class that implements CommandLineRunner will have its run() method
 * called automatically by Spring Boot right after the app starts up.
 *
 * We use this to check whether slots already exist — if they do, we skip
 * seeding so we don't create duplicate rows every time the app restarts.
 *
 * ─── What slots does this create? ───────────────────────────────────────────
 * For each day over the next 7 days, it creates 4 delivery windows:
 *   - Morning:   09:00 – 11:00
 *   - Midday:    11:00 – 13:00
 *   - Afternoon: 13:00 – 16:00
 *   - Evening:   16:00 – 19:00
 *
 * Each slot has a capacity of 3 — meaning up to 3 orders can be booked
 * into the same window (handled by 3 different agents working in parallel).
 *
 * ─── Why @Component? ────────────────────────────────────────────────────────
 * @Component tells Spring "this is a managed bean — create it and run it."
 * It is similar to @Service but used for utility classes like seeders
 * that don't fit neatly into the service/repository pattern.
 */
@Component
public class TimeSlotSeeder implements CommandLineRunner {

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    // ── The 4 delivery windows we offer each day ──────────────────────────────
    // Each window is defined by a start time, end time, and a human-readable label.
    // We store these as parallel arrays — index 0 matches across all three arrays.
    private static final LocalTime[] START_TIMES = {
            LocalTime.of(9, 0),   // 09:00
            LocalTime.of(11, 0),  // 11:00
            LocalTime.of(13, 0),  // 13:00
            LocalTime.of(16, 0),  // 16:00
    };

    private static final LocalTime[] END_TIMES = {
            LocalTime.of(11, 0),  // 11:00
            LocalTime.of(13, 0),  // 13:00
            LocalTime.of(16, 0),  // 16:00
            LocalTime.of(19, 0),  // 19:00
    };

    private static final String[] LABELS = {
            "Morning (9:00 AM – 11:00 AM)",
            "Midday (11:00 AM – 1:00 PM)",
            "Afternoon (1:00 PM – 4:00 PM)",
            "Evening (4:00 PM – 7:00 PM)",
    };

    // How many orders can fit in a single time slot
    // (3 agents can cover the same window simultaneously)
    private static final int SLOT_CAPACITY = 3;

    // How many days ahead to generate slots for
    private static final int DAYS_AHEAD = 7;

    /**
     * This method runs automatically when the app starts.
     *
     * --- What does it do? ---
     * 1. Checks if any slots already exist in the database.
     * 2. If yes → skips (so we don't duplicate rows on every restart).
     * 3. If no  → creates 4 slots × 7 days = 28 slots total.
     *
     * --- Why check count first? ---
     * Every time you restart the Spring Boot app, CommandLineRunner fires again.
     * Without the count check, you'd get 28 new rows added every single restart,
     * quickly filling the table with thousands of duplicates.
     */
    @Override
    public void run(String... args) throws Exception {
        LocalDate today = LocalDate.now();
        int created = 0;

        // Check each day individually — ensures future days are always populated
        // even if the app hasn't restarted in a while and old slots have passed
        for (int dayOffset = 0; dayOffset < DAYS_AHEAD; dayOffset++) {
            LocalDate date = today.plusDays(dayOffset);

            if (timeSlotRepository.findBySlotDate(date).isEmpty()) {
                for (int i = 0; i < START_TIMES.length; i++) {
                    timeSlotRepository.save(new TimeSlot(date, START_TIMES[i], END_TIMES[i], LABELS[i], SLOT_CAPACITY));
                    created++;
                }
            }
        }

        if (created > 0) {
            System.out.println("[TimeSlotSeeder] Created " + created + " new time slots.");
        } else {
            System.out.println("[TimeSlotSeeder] All slots already exist. Nothing to seed.");
        }
    }
}
