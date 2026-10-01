package com.drone.plant.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drone.plant.common.BizException;
import com.drone.plant.dto.HandlingDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.entity.AbnormalHandling;
import com.drone.plant.entity.AbnormalHandlingAction;
import com.drone.plant.entity.Drone;
import com.drone.plant.entity.FlightLog;
import com.drone.plant.entity.JobOrder;
import com.drone.plant.entity.Pilot;
import com.drone.plant.projection.HandlingRow;
import com.drone.plant.projection.PilotOption;
import com.drone.plant.projection.PlotOption;
import com.drone.plant.repository.AbnormalHandlingActionRepository;
import com.drone.plant.repository.AbnormalHandlingRepository;
import com.drone.plant.repository.DroneRepository;
import com.drone.plant.repository.FlightLogRepository;
import com.drone.plant.repository.JobOrderRepository;
import com.drone.plant.repository.PilotRepository;
import com.drone.plant.repository.PlotRepository;
import com.drone.plant.support.PlantRules;

/**
 * 模块五（二）：异常架次停飞处置。
 *
 * <p>主线：异常架次登记即自动生成 PENDING 处置单；值班主管（在队三级 / 教员）可
 * 「继续观察」OBSERVING、「停飞机身」GROUNDED、「解除」RESOLVED。
 * <ul>
 *   <li>停飞：关联无人机立即 MAINT（维修中），未起飞作业单不得再选该机，已有待飞单提示改派；</li>
 *   <li>解除：必须填写处置结论；若机身上还有其他未结束处置，机身保持维修中，不恢复在库；</li>
 *   <li>登记 / 观察 / 停飞 / 解除全部写 {@link AbnormalHandlingAction} 留痕。</li>
 * </ul>
 */
@Service
public class AbnormalHandlingService {

    private final AbnormalHandlingRepository handlingRepository;
    private final AbnormalHandlingActionRepository actionRepository;
    private final FlightLogRepository flightLogRepository;
    private final JobOrderRepository jobOrderRepository;
    private final DroneRepository droneRepository;
    private final PilotRepository pilotRepository;
    private final PlotRepository plotRepository;

    public AbnormalHandlingService(AbnormalHandlingRepository handlingRepository,
                                   AbnormalHandlingActionRepository actionRepository,
                                   FlightLogRepository flightLogRepository,
                                   JobOrderRepository jobOrderRepository,
                                   DroneRepository droneRepository,
                                   PilotRepository pilotRepository,
                                   PlotRepository plotRepository) {
        this.handlingRepository = handlingRepository;
        this.actionRepository = actionRepository;
        this.flightLogRepository = flightLogRepository;
        this.jobOrderRepository = jobOrderRepository;
        this.droneRepository = droneRepository;
        this.pilotRepository = pilotRepository;
        this.plotRepository = plotRepository;
    }

    // ---------------- 查询 ----------------

    /** 按状态查处置单（状态为空查全部），可叠加机身筛选。 */
    @Transactional(readOnly = true)
    public List<Views.HandlingView> list(String status, Long droneId) {
        List<HandlingRow> rows;
        if (droneId != null) {
            rows = handlingRepository.findByDroneIdOrderByUpdatedAtDesc(droneId, HandlingRow.class);
            if (!isBlank(status)) {
                validStatus(status);
                rows = rows.stream().filter(row -> status.equals(row.getStatus())).toList();
            }
        } else if (isBlank(status)) {
            rows = handlingRepository.findAllByOrderByUpdatedAtDesc(HandlingRow.class);
        } else {
            rows = handlingRepository.findByStatusOrderByUpdatedAtDesc(validStatus(status), HandlingRow.class);
        }
        NameBook book = nameBook();
        List<Views.HandlingView> list = new ArrayList<>();
        for (HandlingRow row : rows) {
            list.add(toView(row, book, false));
        }
        return list;
    }

    /** 从机身详情进入：该机的全部处置单。 */
    @Transactional(readOnly = true)
    public List<Views.HandlingView> listByDrone(Long droneId) {
        NameBook book = nameBook();
        List<Views.HandlingView> list = new ArrayList<>();
        for (HandlingRow row : handlingRepository.findByDroneIdOrderByUpdatedAtDesc(droneId, HandlingRow.class)) {
            list.add(toView(row, book, false));
        }
        return list;
    }

    /** 从飞行日志进入：该架次的处置单（正常 0 或 1 条）。 */
    @Transactional(readOnly = true)
    public List<Views.HandlingView> listByFlightLog(Long flightLogId) {
        NameBook book = nameBook();
        List<Views.HandlingView> list = new ArrayList<>();
        for (HandlingRow row : handlingRepository.findByFlightLogIdOrderByIdDesc(flightLogId, HandlingRow.class)) {
            list.add(toView(row, book, false));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public Views.HandlingView detail(Long id) {
        HandlingRow row = handlingRepository.findAllByOrderByUpdatedAtDesc(HandlingRow.class).stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new BizException("处置单不存在：id=" + id));
        return toView(row, nameBook(), true);
    }

    /** 值班主管下拉：在队且三级 / 教员。 */
    @Transactional(readOnly = true)
    public List<Views.PilotOptionView> supervisorOptions() {
        List<Views.PilotOptionView> list = new ArrayList<>();
        for (PilotOption row : pilotRepository.findByStatusOrderByPilotNoAsc("ACTIVE", PilotOption.class)) {
            if (PlantRules.supervisorLicenseAllowed(row.getLicenseLevel())) {
                list.add(new Views.PilotOptionView(row.getId(), row.getPilotNo(), row.getName(),
                        PlantRules.licenseName(row.getLicenseLevel()), row.getStatus()));
            }
        }
        return list;
    }

    // ---------------- 状态联动（给其他模块调用） ----------------

    /** 机身上未结束（未解除）的处置数：>0 时机身不得切回在库。 */
    @Transactional(readOnly = true)
    public long openCount(Long droneId) {
        return handlingRepository.countByDroneIdAndStatusNot(droneId, "RESOLVED");
    }

    /** 机身是否处于停飞中（已停飞未解除）：未起飞作业单不得再选该机。 */
    @Transactional(readOnly = true)
    public boolean isGrounded(Long droneId) {
        return droneId != null && handlingRepository.countByDroneIdAndStatus(droneId, "GROUNDED") > 0;
    }

    /**
     * 异常架次登记后自动进入待处置：由飞行日志登记调用。
     * 同一架次最多一张处置单（唯一索引兜底），已存在则直接返回。
     */
    @Transactional
    public AbnormalHandling registerOnCreate(FlightLog log, JobOrder order, Pilot registrar) {
        return handlingRepository.findByFlightLogId(log.getId()).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            AbnormalHandling handling = new AbnormalHandling();
            handling.setHandlingNo(nextHandlingNo());
            handling.setFlightLogId(log.getId());
            handling.setJobOrderId(order.getId());
            handling.setDroneId(order.getDroneId());
            handling.setStatus("PENDING");
            if (registrar != null) {
                handling.setRegistrarId(registrar.getId());
                handling.setRegistrarName(registrar.getName());
            }
            handling.setAbnormalDesc(log.getAbnormalDesc());
            handling.setCreatedAt(log.getCreatedAt() == null ? now : log.getCreatedAt());
            handling.setUpdatedAt(now);
            AbnormalHandling saved = handlingRepository.save(handling);
            logAction(saved.getId(), "REGISTER",
                    registrar == null ? null : registrar.getId(),
                    registrar == null ? null : registrar.getName(),
                    "异常架次登记，自动进入待处置", now);
            return saved;
        });
    }

    // ---------------- 处置动作 ----------------

    /** 待处置 / 继续观察 → 继续观察：机身不停飞，处置保持未结束。 */
    @Transactional
    public Views.HandlingView observe(Long id, HandlingDtos.ActionReq req) {
        AbnormalHandling handling = requireHandling(id);
        Pilot supervisor = requireSupervisor(req.getSupervisorId());
        requireFlow(handling, "OBSERVING");
        String remark = trim(req.getRemark());
        if (remark.isEmpty()) {
            throw new BizException("继续观察必须填写处置说明（观察要点与复检安排）");
        }
        LocalDateTime now = LocalDateTime.now();
        handling.setStatus("OBSERVING");
        handling.setDecision("OBSERVE");
        handling.setHandleRemark(remark);
        handling.setObservedAt(now);
        applySupervisor(handling, supervisor, now);
        handlingRepository.save(handling);
        logAction(handling.getId(), "OBSERVE", supervisor.getId(), supervisor.getName(), remark, now);
        return detail(handling.getId());
    }

    /** 待处置 / 继续观察 → 停飞机身：关联无人机立即维修中。 */
    @Transactional
    public Views.HandlingView ground(Long id, HandlingDtos.ActionReq req) {
        AbnormalHandling handling = requireHandling(id);
        Pilot supervisor = requireSupervisor(req.getSupervisorId());
        requireFlow(handling, "GROUNDED");
        String remark = trim(req.getRemark());
        if (remark.isEmpty()) {
            throw new BizException("停飞机身必须填写处置说明（故障现象与停飞原因）");
        }
        LocalDateTime now = LocalDateTime.now();
        handling.setStatus("GROUNDED");
        handling.setDecision("GROUND");
        handling.setHandleRemark(remark);
        handling.setGroundedAt(now);
        applySupervisor(handling, supervisor, now);
        handlingRepository.save(handling);

        // 机身立即转为维修中（作业中 / 在库都直接停；停用是行政终态，不覆盖）
        Drone drone = droneRepository.findById(handling.getDroneId())
                .orElseThrow(() -> new BizException("关联无人机不存在：id=" + handling.getDroneId()));
        if (!"DISABLED".equals(drone.getStatus()) && !"MAINT".equals(drone.getStatus())) {
            drone.setStatus("MAINT");
            drone.setUpdatedAt(now);
            droneRepository.save(drone);
        }
        logAction(handling.getId(), "GROUND", supervisor.getId(), supervisor.getName(), remark, now);
        return detail(handling.getId());
    }

    /** 解除：结论必填；仍有其他未结束处置时机身维持维修中，不能切回空闲。 */
    @Transactional
    public Views.HandlingView resolve(Long id, HandlingDtos.ResolveReq req) {
        AbnormalHandling handling = requireHandling(id);
        Pilot supervisor = requireSupervisor(req.getSupervisorId());
        requireFlow(handling, "RESOLVED");
        String conclusion = trim(req.getConclusion());
        if (conclusion.isEmpty()) {
            throw new BizException("解除前必须填写处置结论");
        }
        LocalDateTime now = LocalDateTime.now();
        handling.setStatus("RESOLVED");
        handling.setDecision("RESOLVE");
        handling.setConclusion(conclusion);
        handling.setResolvedAt(now);
        applySupervisor(handling, supervisor, now);
        handlingRepository.save(handling);

        // 仅当机身上再无其他未结束处置、且当前为维修中时，才恢复在库；停用机身不自动恢复
        Drone drone = droneRepository.findById(handling.getDroneId()).orElse(null);
        if (drone != null && "MAINT".equals(drone.getStatus()) && openCount(drone.getId()) == 0) {
            drone.setStatus("IDLE");
            drone.setUpdatedAt(now);
            droneRepository.save(drone);
        }
        logAction(handling.getId(), "RESOLVE", supervisor.getId(), supervisor.getName(), conclusion, now);
        return detail(handling.getId());
    }

    // ---------------- 装配与校验 ----------------

    private Views.HandlingView toView(HandlingRow row, NameBook book, boolean withDetail) {
        JobOrder order = book.orders.get(row.getJobOrderId());
        Drone drone = book.drones.get(row.getDroneId());
        FlightLog log = book.logs.get(row.getFlightLogId());
        String plotNo = null;
        String farmerName = null;
        if (order != null) {
            PlotOption plot = book.plots.get(order.getPlotId());
            if (plot != null) {
                plotNo = plot.getPlotNo();
                farmerName = plot.getFarmerName();
            }
        }
        List<Views.GroundedOrderView> groundedOrders = new ArrayList<>();
        List<Views.HandlingActionView> actions = new ArrayList<>();
        if (withDetail) {
            // 只有机身停飞中才有待改派待飞单；继续观察不影响派航
            if (isGrounded(row.getDroneId())) {
                for (JobOrder pending : jobOrderRepository.findByStatusOrderByJobDateDesc("PENDING",
                        JobOrder.class)) {
                    if (!pending.getDroneId().equals(row.getDroneId())) {
                        continue;
                    }
                    PlotOption plot = book.plots.get(pending.getPlotId());
                    Pilot pilot = book.pilots.get(pending.getPilotId());
                    groundedOrders.add(new Views.GroundedOrderView(pending.getId(), pending.getOrderNo(),
                            plot == null ? null : plot.getPlotNo(),
                            plot == null ? null : plot.getFarmerName(), pending.getJobDate(),
                            pilot == null ? null : pilot.getName()));
                }
            }
            for (AbnormalHandlingAction item : actionRepository.findByHandlingIdOrderByIdAsc(row.getId())) {
                actions.add(new Views.HandlingActionView(item.getId(), item.getAction(),
                        PlantRules.handlingActionName(item.getAction()), item.getSupervisorId(),
                        item.getSupervisorName(), item.getRemark(), item.getCreatedAt()));
            }
        }
        return new Views.HandlingView(
                row.getId(), row.getHandlingNo(), row.getFlightLogId(),
                log == null ? null : log.getSeqNo(),
                row.getJobOrderId(), order == null ? null : order.getOrderNo(), plotNo, farmerName,
                row.getDroneId(), drone == null ? null : drone.getBodyNo(), drone == null ? null : drone.getModel(),
                drone == null ? null : drone.getStatus(),
                drone == null ? null : PlantRules.droneStatusName(drone.getStatus()),
                row.getStatus(), PlantRules.handlingStatusName(row.getStatus()),
                row.getRegistrarId(), row.getRegistrarName(), row.getSupervisorId(), row.getSupervisorName(),
                row.getAbnormalDesc(), row.getDecision(), row.getHandleRemark(), row.getConclusion(),
                log == null ? null : log.getTakeoffAt(),
                row.getObservedAt(), row.getGroundedAt(), row.getResolvedAt(), row.getCreatedAt(),
                row.getUpdatedAt(), groundedOrders, actions);
    }

    private NameBook nameBook() {
        Map<Long, JobOrder> orders = new HashMap<>();
        for (JobOrder order : jobOrderRepository.findAll()) {
            orders.put(order.getId(), order);
        }
        Map<Long, Drone> drones = new HashMap<>();
        for (Drone drone : droneRepository.findAll()) {
            drones.put(drone.getId(), drone);
        }
        Map<Long, Pilot> pilots = new HashMap<>();
        for (Pilot pilot : pilotRepository.findAll()) {
            pilots.put(pilot.getId(), pilot);
        }
        Map<Long, PlotOption> plots = new HashMap<>();
        for (PlotOption plot : plotRepository.findAllByOrderByPlotNoAsc(PlotOption.class)) {
            plots.put(plot.getId(), plot);
        }
        Map<Long, FlightLog> logs = new HashMap<>();
        for (FlightLog log : flightLogRepository.findAll()) {
            logs.put(log.getId(), log);
        }
        return new NameBook(orders, drones, pilots, plots, logs);
    }

    private void applySupervisor(AbnormalHandling handling, Pilot supervisor, LocalDateTime now) {
        handling.setSupervisorId(supervisor.getId());
        handling.setSupervisorName(supervisor.getName());
        handling.setUpdatedAt(now);
    }

    private void logAction(Long handlingId, String action, Long supervisorId, String supervisorName,
                           String remark, LocalDateTime now) {
        AbnormalHandlingAction item = new AbnormalHandlingAction();
        item.setHandlingId(handlingId);
        item.setAction(action);
        item.setSupervisorId(supervisorId);
        item.setSupervisorName(supervisorName);
        item.setRemark(remark);
        item.setCreatedAt(now);
        actionRepository.save(item);
    }

    private AbnormalHandling requireHandling(Long id) {
        return handlingRepository.findById(id)
                .orElseThrow(() -> new BizException("处置单不存在：id=" + id));
    }

    private void requireFlow(AbnormalHandling handling, String target) {
        if ("RESOLVED".equals(handling.getStatus())) {
            throw new BizException("处置单 " + handling.getHandlingNo() + " 已解除，处置流程已结束，不能重复操作");
        }
        if (!PlantRules.handlingFlowAllowed(handling.getStatus(), target)) {
            throw new BizException("处置单 " + handling.getHandlingNo() + " 当前为"
                    + PlantRules.handlingStatusName(handling.getStatus()) + "，不能流转到"
                    + PlantRules.handlingStatusName(target));
        }
    }

    /** 值班主管：在队飞手 + 三级（高级）/ 教员执照。 */
    private Pilot requireSupervisor(Long supervisorId) {
        if (supervisorId == null) {
            throw new BizException("请先选择值班主管");
        }
        Pilot supervisor = pilotRepository.findById(supervisorId)
                .orElseThrow(() -> new BizException("值班主管不存在：id=" + supervisorId));
        if (!"ACTIVE".equals(supervisor.getStatus())) {
            throw new BizException(supervisor.getName() + " 当前为"
                    + PlantRules.pilotStatusName(supervisor.getStatus()) + "，不能担任值班主管");
        }
        if (!PlantRules.supervisorLicenseAllowed(supervisor.getLicenseLevel())) {
            throw new BizException("值班主管 " + supervisor.getName() + " 的执照为"
                    + PlantRules.licenseName(supervisor.getLicenseLevel()) + "，值班主管必须是三级（高级）或教员");
        }
        return supervisor;
    }

    private String nextHandlingNo() {
        String prefix = "AH-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        long seq = handlingRepository.count() + 1;
        String candidate;
        do {
            candidate = prefix + String.format("%02d", seq);
            seq++;
        } while (handlingRepository.existsByHandlingNo(candidate));
        return candidate;
    }

    private String validStatus(String status) {
        if (!PlantRules.isValidHandlingStatus(status)) {
            throw new BizException("未知处置状态：" + status);
        }
        return status;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record NameBook(Map<Long, JobOrder> orders, Map<Long, Drone> drones, Map<Long, Pilot> pilots,
                            Map<Long, PlotOption> plots, Map<Long, FlightLog> logs) {
    }
}
