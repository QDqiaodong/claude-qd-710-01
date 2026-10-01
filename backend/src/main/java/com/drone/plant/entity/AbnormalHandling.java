package com.drone.plant.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 模块五（二）：异常架次停飞处置单。
 *
 * <p>状态机：登记后自动 PENDING（待处置）→ 值班主管 OBSERVE 继续观察 / GROUND 停飞机身；
 * PENDING / OBSERVING / GROUNDED 均可 RESOLVE 解除（必须填写处置结论）。RESOLVED 是终态。
 * 选择 GROUND 时关联无人机立即转为 MAINT（维修中），解除时若再无其他未结束处置才恢复 IDLE。
 */
@Data
@Entity
@Table(name = "abnormal_handling")
public class AbnormalHandling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 处置单号，如 AH-24092301 */
    @Column(name = "handling_no", nullable = false, length = 24, unique = true)
    private String handlingNo;

    /** 异常架次（飞行日志 id），一架次最多一张处置单 */
    @Column(name = "flight_log_id", nullable = false)
    private Long flightLogId;

    /** 所属作业单（架次快照） */
    @Column(name = "job_order_id", nullable = false)
    private Long jobOrderId;

    /** 涉事无人机（架次快照） */
    @Column(name = "drone_id", nullable = false)
    private Long droneId;

    /** 状态：PENDING 待处置 / OBSERVING 继续观察 / GROUNDED 已停飞 / RESOLVED 已解除 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 登记人（架次飞手快照） */
    @Column(name = "registrar_id")
    private Long registrarId;

    @Column(name = "registrar_name", length = 32)
    private String registrarName;

    /** 值班主管（处置飞手） */
    @Column(name = "supervisor_id")
    private Long supervisorId;

    @Column(name = "supervisor_name", length = 32)
    private String supervisorName;

    /** 异常描述快照（架次描述，解除后仍可追溯） */
    @Column(name = "abnormal_desc", length = 255)
    private String abnormalDesc;

    /** 处置决定：OBSERVE / GROUND / RESOLVE */
    @Column(name = "decision", length = 16)
    private String decision;

    /** 处置说明（继续观察 / 停飞时填写） */
    @Column(name = "handle_remark", length = 255)
    private String handleRemark;

    /** 处置结论（解除必填） */
    @Column(name = "conclusion", length = 255)
    private String conclusion;

    @Column(name = "observed_at")
    private LocalDateTime observedAt;

    @Column(name = "grounded_at")
    private LocalDateTime groundedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
