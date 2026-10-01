package com.drone.plant.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 异常处置留痕：登记 / 继续观察 / 停飞 / 解除各写一条。 */
@Data
@Entity
@Table(name = "abnormal_handling_action")
public class AbnormalHandlingAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handling_id", nullable = false)
    private Long handlingId;

    /** 动作：REGISTER 登记 / OBSERVE 继续观察 / GROUND 停飞 / RESOLVE 解除 */
    @Column(name = "action", nullable = false, length = 16)
    private String action;

    @Column(name = "supervisor_id")
    private Long supervisorId;

    @Column(name = "supervisor_name", length = 32)
    private String supervisorName;

    /** 说明（处置说明 / 处置结论） */
    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
