package com.bt.deliveryapp.repository;

import com.bt.deliveryapp.model.RecurringDelivery;
import com.bt.deliveryapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RecurringDeliveryRepository extends JpaRepository<RecurringDelivery, Long> {

    List<RecurringDelivery> findByCustomerOrderByCreatedAtDesc(User customer);

    List<RecurringDelivery> findByActiveTrueAndNextRunDateLessThanEqual(LocalDate date);
}
