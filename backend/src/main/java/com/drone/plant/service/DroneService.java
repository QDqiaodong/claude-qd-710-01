package com.drone.plant.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drone.plant.common.BizException;
import com.drone.plant.dto.DroneDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.entity.Drone;
import com.drone.plant.projection.DroneCard;
import com.drone.plant.projection.DroneOption;
import com.drone.plant.projection.DronePayload;
import com.drone.plant.projection.FlightLogStat;
import com.drone.plant.projection.JobOrderBrief;
import com.drone.plant.projection.PlotOption;
import com.drone.plant.repository.DroneRepository;
import com.drone.plant.repository.FlightLogRepository;
import com.drone.plant.repository.JobOrderRepository;
import com.drone.plant.repository.PlotRepository;
import com.drone.plant.support.PlantRules;

/**
 * 模块二：植保无人机。
 *
 * <p>三个形状共用同一批查询方法：{@link DroneCard}（卡片墙）、{@link DroneOption}（下拉框）、
 * {@link DronePayload}（作业算药量时的载荷口径）。
 */
@Service
public class DroneService {

    private final DroneRepository droneRepository;
    private final JobOrderRepository jobOrderRepository;
    private final FlightLogRepository flightLogRepository;
    private final PlotRepository plotRepository;
    private final AbnormalHandlingService handlingService;

    public DroneService(DroneRepository droneRepository, JobOrderRepository jobOrderRepository,
                        FlightLogRepository flightLogRepository, PlotRepository plotRepository,
                        AbnormalHandlingService handlingService) {
        this.droneRepository = droneRepository;
        this.jobOrderRepository = jobOrderRepository;
        this.flightLogRepository = flightLogRepository;
        this.plotRepository = plotRepository;
        this.handlingService = handlingService;
    }

    @Transactional(readOnly = true)
    public List<Views.DroneView> list(String status, String droneType) {
        List<DroneCard> rows;
        if (isBlank(status)) {
            rows = isBlank(droneType)
                    ? droneRepository.findAllByOrderByBodyNoAsc(DroneCard.class)
                    : droneRepository.findByDroneTypeOrderByTotalAreaMuDesc(droneType, DroneCard.class);
        } else {
            rows = droneRepository.findByStatusOrderByBodyNoAsc(status, DroneCard.class);
        }
        Map<Long, DroneStat> stats = droneStats();
        List<Views.DroneView> list = new ArrayList<>();
        for (DroneCard row : rows) {
            DroneStat stat = stats.get(row.getId());
            long openHandling = handlingService.openCount(row.getId());
            list.add(new Views.DroneView(row.getId(), row.getBodyNo(), row.getModel(), row.getDroneType(),
                    PlantRules.droneTypeName(row.getDroneType()), row.getMaxPayload(), row.getSprayWidth(),
                    row.getTotalFlightHours(), row.getTotalAreaMu(), row.getBatteryPacks(), row.getStatus(),
                    PlantRules.droneStatusName(row.getStatus()), row.getPurchaseDate(),
                    stat == null ? 0 : stat.openOrders, stat == null ? null : stat.currentPlotNo,
                    stat == null ? 0 : stat.sortCount, null,
                    openHandling, handlingService.isGrounded(row.getId())));
        }
        return list;
    }

    /** 下拉框：同一方法取 {@link DroneOption} 形状；停飞中的机身在派工下拉里给出标记（前端禁用） */
    @Transactional(readOnly = true)
    public List<Views.DroneOptionView> options() {
        List<Views.DroneOptionView> list = new ArrayList<>();
        for (DroneOption row : droneRepository.findAllByOrderByBodyNoAsc(DroneOption.class)) {
            list.add(new Views.DroneOptionView(row.getId(), row.getBodyNo(), row.getModel(), row.getStatus(),
                    PlantRules.droneStatusName(row.getStatus()), row.getMaxPayload(),
                    handlingService.isGrounded(row.getId())));
        }
        return list;
    }

    /** 载荷口径：给正在作业的几台机算「还能带多少药」 */
    @Transactional(readOnly = true)
    public List<DronePayload> payloads() {
        return droneRepository.findAllByOrderByBodyNoAsc(DronePayload.class);
    }

    @Transactional
    public Views.DroneView create(DroneDtos.SaveReq req) {
        String bodyNo = trim(req.getBodyNo());
        if (bodyNo.isEmpty()) {
            throw new BizException("机身编号不能为空");
        }
        if (droneRepository.existsByBodyNo(bodyNo)) {
            throw new BizException("机身编号 " + bodyNo + " 已存在");
        }
        String droneType = upper(req.getDroneType());
        if (!PlantRules.isValidDroneType(droneType)) {
            throw new BizException("类型只能是 MULTI / SINGLE / SPRAY，收到的是：" + req.getDroneType());
        }
        BigDecimal maxPayload = nvl(req.getMaxPayload());
        if (maxPayload.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("最大载荷必须大于 0");
        }
        if (maxPayload.compareTo(PlantRules.droneMaxPayload(droneType)) > 0) {
            throw new BizException(PlantRules.droneTypeName(droneType) + "的最大载荷上限是 "
                    + PlantRules.droneMaxPayload(droneType) + " L，收到的是 " + maxPayload);
        }
        BigDecimal sprayWidth = nvl(req.getSprayWidth());
        if (sprayWidth.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("喷幅必须大于 0");
        }
        if (sprayWidth.compareTo(PlantRules.droneMaxWidth(droneType)) > 0) {
            throw new BizException(PlantRules.droneTypeName(droneType) + "的喷幅上限是 "
                    + PlantRules.droneMaxWidth(droneType) + " m，收到的是 " + sprayWidth);
        }
        int packs = req.getBatteryPacks() == null ? 0 : req.getBatteryPacks();
        if (packs < PlantRules.MIN_BATTERY_PACKS || packs > PlantRules.MAX_BATTERY_PACKS) {
            throw new BizException("电池组数应在 " + PlantRules.MIN_BATTERY_PACKS + "~" + PlantRules.MAX_BATTERY_PACKS
                    + " 之间，收到的是 " + packs);
        }
        Drone drone = new Drone();
        drone.setBodyNo(bodyNo);
        drone.setModel(trim(req.getModel()));
        drone.setDroneType(droneType);
        drone.setMaxPayload(maxPayload.setScale(2, RoundingMode.HALF_UP));
        drone.setSprayWidth(sprayWidth.setScale(2, RoundingMode.HALF_UP));
        drone.setTotalFlightHours(BigDecimal.ZERO);
        drone.setTotalAreaMu(BigDecimal.ZERO);
        drone.setBatteryPacks(packs);
        drone.setStatus("IDLE");
        drone.setPurchaseDate(req.getPurchaseDate());
        drone.setUpdatedAt(LocalDateTime.now());
        Drone saved = droneRepository.save(drone);
        return new Views.DroneView(saved.getId(), saved.getBodyNo(), saved.getModel(), saved.getDroneType(),
                PlantRules.droneTypeName(saved.getDroneType()), saved.getMaxPayload(), saved.getSprayWidth(),
                saved.getTotalFlightHours(), saved.getTotalAreaMu(), saved.getBatteryPacks(), saved.getStatus(),
                PlantRules.droneStatusName(saved.getStatus()), saved.getPurchaseDate(), 0, null, 0,
                saved.getUpdatedAt(), 0, false);
    }

    /** 状态切换：在库 ↔ 维修 / 停用；进出的「作业中」由作业单流转推进 */
    @Transactional
    public Views.DroneView updateStatus(Long id, String status) {
        Drone drone = droneRepository.findById(id)
                .orElseThrow(() -> new BizException("无人机不存在：id=" + id));
        String target = upper(status);
        if (!PlantRules.isValidDroneStatus(target)) {
            throw new BizException("无人机状态只能是 IDLE / WORKING / MAINT / DISABLED，收到的是：" + status);
        }
        if ("WORKING".equals(target)) {
            throw new BizException("作业中由作业单起飞自动推进，请在作业单页操作");
        }
        if ("MAINT".equals(target) || "DISABLED".equals(target)) {
            if (hasWorkingOrder(id)) {
                throw new BizException(drone.getBodyNo() + " 还有作业中的作业单，不能转入 "
                        + PlantRules.droneStatusName(target));
            }
        }
        // 仍有未结束（待处置 / 继续观察 / 已停飞）处置时，机身不能切回在库：先由值班主管解除处置
        if ("IDLE".equals(target) && handlingService.openCount(id) > 0) {
            throw new BizException(drone.getBodyNo() + " 还有 " + handlingService.openCount(id)
                    + " 条未结束的异常处置，不能切回在库；请先在异常处置台由值班主管解除");
        }
        drone.setStatus(target);
        drone.setUpdatedAt(LocalDateTime.now());
        Drone saved = droneRepository.save(drone);
        DroneStat stat = droneStats().get(id);
        return new Views.DroneView(saved.getId(), saved.getBodyNo(), saved.getModel(), saved.getDroneType(),
                PlantRules.droneTypeName(saved.getDroneType()), saved.getMaxPayload(), saved.getSprayWidth(),
                saved.getTotalFlightHours(), saved.getTotalAreaMu(), saved.getBatteryPacks(), saved.getStatus(),
                PlantRules.droneStatusName(saved.getStatus()), saved.getPurchaseDate(),
                stat == null ? 0 : stat.openOrders, stat == null ? null : stat.currentPlotNo,
                stat == null ? 0 : stat.sortCount, saved.getUpdatedAt(),
                handlingService.openCount(id), handlingService.isGrounded(id));
    }

    private boolean hasWorkingOrder(Long droneId) {
        for (JobOrderBrief order : jobOrderRepository.findByDroneIdOrderByJobDateDesc(droneId, JobOrderBrief.class)) {
            if ("WORKING".equals(order.getStatus())) {
                return true;
            }
        }
        return false;
    }

    /** 每台机的在手作业单、累计架次、当前作业地块 */
    private Map<Long, DroneStat> droneStats() {
        Map<Long, String> plotNoMap = plotNoMap();
        Map<Long, DroneStat> map = new HashMap<>();
        Map<Long, Long> orderToDrone = new HashMap<>();
        Map<Long, List<JobOrderBrief>> byDrone = new HashMap<>();
        for (JobOrderBrief order : jobOrderRepository.findAllByOrderByJobDateDesc(JobOrderBrief.class)) {
            if (order.getDroneId() == null) {
                continue;
            }
            orderToDrone.put(order.getId(), order.getDroneId());
            byDrone.computeIfAbsent(order.getDroneId(), key -> new ArrayList<>()).add(order);
        }
        for (FlightLogStat log : flightLogRepository.findAllByOrderByTakeoffAtDesc(FlightLogStat.class)) {
            Long droneId = orderToDrone.get(log.getJobOrderId());
            if (droneId == null) {
                continue;
            }
            DroneStat old = map.getOrDefault(droneId, new DroneStat(0, null, 0));
            map.put(droneId, new DroneStat(old.openOrders, old.currentPlotNo, old.sortCount + 1));
        }
        for (Map.Entry<Long, List<JobOrderBrief>> entry : byDrone.entrySet()) {
            long open = 0;
            String plotNo = null;
            for (JobOrderBrief order : entry.getValue()) {
                if ("PENDING".equals(order.getStatus()) || "WORKING".equals(order.getStatus())) {
                    open++;
                }
                if ("WORKING".equals(order.getStatus())) {
                    plotNo = plotNoMap.get(order.getPlotId());
                }
            }
            DroneStat old = map.getOrDefault(entry.getKey(), new DroneStat(0, null, 0));
            map.put(entry.getKey(), new DroneStat(open, plotNo, old.sortCount));
        }
        return map;
    }

    private Map<Long, String> plotNoMap() {
        Map<Long, String> map = new HashMap<>();
        for (PlotOption row : plotRepository.findAllByOrderByPlotNoAsc(PlotOption.class)) {
            map.put(row.getId(), row.getPlotNo());
        }
        return map;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record DroneStat(long openOrders, String currentPlotNo, long sortCount) {
    }
}
