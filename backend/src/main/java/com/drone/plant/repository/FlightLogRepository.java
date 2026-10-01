package com.drone.plant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drone.plant.entity.FlightLog;

/** 飞行日志仓储： {@code FlightLogRow}（表格行）/ {@code FlightLogStat}（统计条）同一批方法取不同形状。 */
public interface FlightLogRepository extends JpaRepository<FlightLog, Long> {

    <T> List<T> findByJobOrderIdOrderBySeqNoAsc(Long jobOrderId, Class<T> type);

    <T> List<T> findAllByOrderByTakeoffAtDesc(Class<T> type);

    <T> List<T> findByAbnormalTrueOrderByTakeoffAtDesc(Class<T> type);

    <T> List<T> findByJobOrderIdAndAbnormalTrueOrderBySeqNoAsc(Long jobOrderId, Class<T> type);

    long countByJobOrderId(Long jobOrderId);

    long countByJobOrderIdAndAbnormalTrue(Long jobOrderId);

    long countByAbnormalTrue();
}
