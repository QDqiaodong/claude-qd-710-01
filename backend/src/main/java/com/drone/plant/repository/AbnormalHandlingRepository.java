package com.drone.plant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drone.plant.entity.AbnormalHandling;

/** 异常架次停飞处置单仓储。 */
public interface AbnormalHandlingRepository extends JpaRepository<AbnormalHandling, Long> {

    <T> List<T> findAllByOrderByUpdatedAtDesc(Class<T> type);

    <T> List<T> findByStatusOrderByUpdatedAtDesc(String status, Class<T> type);

    <T> List<T> findByDroneIdOrderByUpdatedAtDesc(Long droneId, Class<T> type);

    <T> List<T> findByFlightLogIdOrderByIdDesc(Long flightLogId, Class<T> type);

    Optional<AbnormalHandling> findByFlightLogId(Long flightLogId);

    boolean existsByHandlingNo(String handlingNo);

    /** 某机身是否存在未结束（未解除）处置：决定能否切回在库 / 派航 */
    long countByDroneIdAndStatusNot(Long droneId, String status);

    /** 某机身是否存在已停飞且未解除的处置：决定待飞单能否继续用该机 */
    long countByDroneIdAndStatus(Long droneId, String status);

    long countByStatus(String status);

    long countByStatusNot(String status);
}
