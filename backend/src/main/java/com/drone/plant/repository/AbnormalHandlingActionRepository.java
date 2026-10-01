package com.drone.plant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drone.plant.entity.AbnormalHandlingAction;

/** 异常处置留痕仓储。 */
public interface AbnormalHandlingActionRepository extends JpaRepository<AbnormalHandlingAction, Long> {

    List<AbnormalHandlingAction> findByHandlingIdOrderByIdAsc(Long handlingId);
}
