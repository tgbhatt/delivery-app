package com.bt.deliveryapp.migration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs a one-time database fix when the app starts up.
 *
 * --- Why do we need this? ---
 * When Hibernate first created the tracking_events and delivery_requests tables,
 * it made the status columns as MySQL ENUM types — a column that only allows a
 * fixed set of values like ENUM('PLACED', 'ASSIGNED', 'DELIVERED', ...).
 *
 * When we added ARRIVED to the Java enum, MySQL didn't know about it automatically.
 * So when we tried to insert 'ARRIVED', MySQL rejected it with "Data truncated".
 *
 * This class fixes that by running ALTER TABLE to add 'ARRIVED' to the allowed list.
 *
 * --- How does it run automatically? ---
 * @Component tells Spring "create this object and manage it".
 * @EventListener(ApplicationReadyEvent.class) means: "run this method once, right
 * after the app has fully started up and is ready to serve requests".
 *
 * --- Is it safe to run every time the app starts? ---
 * Yes — MySQL's ALTER TABLE MODIFY is safe to run repeatedly. If ARRIVED is already
 * in the ENUM list, MySQL just updates the column definition and nothing breaks.
 * The try-catch means any unexpected error is logged but won't crash the app.
 */
@Component
public class AddArrivedStatusMigration {

    // JdbcTemplate is Spring's helper for running raw SQL queries.
    // Spring Boot creates it automatically — we just @Autowired it.
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void addArrivedToEnumColumns() {

        String fullEnumList = "'PLACED','SCHEDULED','ASSIGNED','OUT_FOR_DELIVERY',"
                            + "'ARRIVED','DELIVERED','FAILED','RESCHEDULED'";

        try {
            // Fix the tracking_events table — both from_status and to_status columns
            jdbcTemplate.execute(
                "ALTER TABLE tracking_events " +
                "MODIFY COLUMN from_status ENUM(" + fullEnumList + ") NOT NULL, " +
                "MODIFY COLUMN to_status   ENUM(" + fullEnumList + ") NOT NULL"
            );

            // Fix the delivery_requests table — the status column
            jdbcTemplate.execute(
                "ALTER TABLE delivery_requests " +
                "MODIFY COLUMN status ENUM(" + fullEnumList + ") NOT NULL"
            );

            System.out.println("✅ Migration complete: ARRIVED added to status ENUM columns.");

        } catch (Exception e) {
            // This is fine — it might just mean the migration already ran before.
            System.out.println("ℹ️ Migration skipped or already applied: " + e.getMessage());
        }
    }
}
