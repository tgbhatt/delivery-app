package com.bt.deliveryapp.service;

import com.bt.deliveryapp.enums.Priority;
import com.bt.deliveryapp.enums.RecurrenceFrequency;
import com.bt.deliveryapp.model.RecurringDelivery;
import com.bt.deliveryapp.model.TimeSlot;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.RecurringDeliveryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class RecurringDeliveryService {

    @Autowired
    private RecurringDeliveryRepository recurringDeliveryRepository;

    @Autowired
    @Lazy
    private DeliveryBookingService deliveryBookingService;

    // ── Create a new recurring schedule ─────────────────────────────────────

    @Transactional
    public RecurringDelivery createRecurring(User customer,
                                             String pickupAddress,
                                             String pickupZone,
                                             String deliveryAddress,
                                             String packageDescription,
                                             String specialInstructions,
                                             Priority priority,
                                             RecurrenceFrequency frequency,
                                             Integer intervalDays,
                                             String daysOfWeek,
                                             Integer dayOfMonth,
                                             LocalDate firstBookingDate,
                                             LocalDate endDate) {
        RecurringDelivery r = new RecurringDelivery();
        r.setCustomer(customer);
        r.setPickupAddress(pickupAddress);
        r.setPickupZone(pickupZone);
        r.setDeliveryAddress(deliveryAddress);
        r.setPackageDescription(packageDescription);
        r.setSpecialInstructions(specialInstructions);
        r.setPriority(priority);
        r.setFrequency(frequency);
        r.setStartDate(firstBookingDate);
        r.setEndDate(endDate);
        r.setIntervalDays(intervalDays);
        r.setDaysOfWeek(daysOfWeek);
        r.setDayOfMonth(dayOfMonth);
        r.setActive(true);
        r.setCreatedAt(LocalDateTime.now());
        // Next run is the first occurrence after the initial booking date
        r.setNextRunDate(advanceDate(firstBookingDate, r));
        return recurringDeliveryRepository.save(r);
    }

    // ── Scheduled job: runs at 6 AM every day ───────────────────────────────

    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void processRecurringDeliveries() {
        LocalDate today = LocalDate.now();
        List<RecurringDelivery> due = recurringDeliveryRepository
                .findByActiveTrueAndNextRunDateLessThanEqual(today);

        for (RecurringDelivery r : due) {
            try {
                // Find the first available slot on or after today
                List<TimeSlot> slots = deliveryBookingService.getAvailableSlotsFrom(today);
                if (slots.isEmpty()) continue;

                TimeSlot slot = slots.get(0);
                deliveryBookingService.placeScheduledOrder(
                        r.getCustomer(),
                        r.getPickupAddress(),
                        r.getPickupZone(),
                        r.getDeliveryAddress(),
                        r.getPackageDescription(),
                        slot.getId(),
                        r.getPriority(),
                        r.getSpecialInstructions()
                );

                // Advance to the next run date
                LocalDate next = advanceDate(r.getNextRunDate(), r);
                if (r.getEndDate() != null && next.isAfter(r.getEndDate())) {
                    r.setActive(false);
                } else {
                    r.setNextRunDate(next);
                }
                recurringDeliveryRepository.save(r);

            } catch (Exception e) {
                // Log and continue — don't let one failure block others
                System.err.println("[RecurringDelivery] Failed to process schedule #"
                        + r.getId() + ": " + e.getMessage());
            }
        }
    }

    // ── Pause / Resume / Cancel ──────────────────────────────────────────────

    @Transactional
    public boolean pauseRecurring(Long id, User customer) {
        Optional<RecurringDelivery> opt = recurringDeliveryRepository.findById(id);
        if (opt.isEmpty() || !opt.get().getCustomer().getId().equals(customer.getId())) return false;
        RecurringDelivery r = opt.get();
        r.setActive(false);
        recurringDeliveryRepository.save(r);
        return true;
    }

    @Transactional
    public boolean resumeRecurring(Long id, User customer) {
        Optional<RecurringDelivery> opt = recurringDeliveryRepository.findById(id);
        if (opt.isEmpty() || !opt.get().getCustomer().getId().equals(customer.getId())) return false;
        RecurringDelivery r = opt.get();
        if (r.getEndDate() != null && LocalDate.now().isAfter(r.getEndDate())) return false;
        r.setActive(true);
        recurringDeliveryRepository.save(r);
        return true;
    }

    @Transactional
    public boolean cancelRecurring(Long id, User customer) {
        Optional<RecurringDelivery> opt = recurringDeliveryRepository.findById(id);
        if (opt.isEmpty() || !opt.get().getCustomer().getId().equals(customer.getId())) return false;
        recurringDeliveryRepository.deleteById(id);
        return true;
    }

    public List<RecurringDelivery> getRecurringForCustomer(User customer) {
        return recurringDeliveryRepository.findByCustomerOrderByCreatedAtDesc(customer);
    }

    // ── Date advancement logic ───────────────────────────────────────────────

    private LocalDate advanceDate(LocalDate from, RecurringDelivery r) {
        switch (r.getFrequency()) {
            case DAILY:
                return from.plusDays(1);

            case EVERY_X_DAYS:
                int interval = (r.getIntervalDays() != null && r.getIntervalDays() > 0)
                        ? r.getIntervalDays() : 1;
                return from.plusDays(interval);

            case WEEKLY:
                return from.plusWeeks(1);

            case EVERY_2_WEEKS:
                return from.plusWeeks(2);

            case EVERY_3_WEEKS:
                return from.plusWeeks(3);

            case MONTHLY:
                return from.plusMonths(1);

            case SPECIFIC_DAYS_OF_WEEK: {
                if (r.getDaysOfWeek() == null || r.getDaysOfWeek().isBlank()) {
                    return from.plusWeeks(1);
                }
                List<DayOfWeek> days = Arrays.stream(r.getDaysOfWeek().split(","))
                        .map(String::trim)
                        .map(DayOfWeek::valueOf)
                        .sorted()
                        .toList();
                // Walk forward up to 7 days to find the next matching day
                for (int i = 1; i <= 7; i++) {
                    LocalDate candidate = from.plusDays(i);
                    if (days.contains(candidate.getDayOfWeek())) return candidate;
                }
                return from.plusWeeks(1);
            }

            case SPECIFIC_DATE_OF_MONTH: {
                int targetDay = (r.getDayOfMonth() != null) ? r.getDayOfMonth() : from.getDayOfMonth();
                LocalDate nextMonth = from.plusMonths(1).withDayOfMonth(1);
                int maxDay = nextMonth.lengthOfMonth();
                return nextMonth.withDayOfMonth(Math.min(targetDay, maxDay));
            }

            default:
                return from.plusWeeks(1);
        }
    }
}
