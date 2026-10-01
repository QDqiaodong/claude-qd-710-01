package com.drone.plant.projection;

import java.time.LocalDateTime;

/**
 * 异常处置单投影：处置列表用的行形状（处置单自身全字段）。
 * 跨表的机身编号 / 作业单号 / 地块等名称由 service 统一查 NameBook 装配。
 */
public interface HandlingRow {

    Long getId();

    String getHandlingNo();

    Long getFlightLogId();

    Long getJobOrderId();

    Long getDroneId();

    String getStatus();

    Long getRegistrarId();

    String getRegistrarName();

    Long getSupervisorId();

    String getSupervisorName();

    String getAbnormalDesc();

    String getDecision();

    String getHandleRemark();

    String getConclusion();

    LocalDateTime getObservedAt();

    LocalDateTime getGroundedAt();

    LocalDateTime getResolvedAt();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}
