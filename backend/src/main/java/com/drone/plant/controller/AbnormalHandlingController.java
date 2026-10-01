package com.drone.plant.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drone.plant.common.ApiResponse;
import com.drone.plant.dto.HandlingDtos;
import com.drone.plant.dto.Views;
import com.drone.plant.service.AbnormalHandlingService;

import jakarta.validation.Valid;

/** 模块五（二）：异常架次停飞处置。 */
@RestController
@RequestMapping("/api/abnormal-handlings")
public class AbnormalHandlingController {

    private final AbnormalHandlingService handlingService;

    public AbnormalHandlingController(AbnormalHandlingService handlingService) {
        this.handlingService = handlingService;
    }

    /** 按状态 / 机身查看处置记录 */
    @GetMapping
    public ApiResponse<List<Views.HandlingView>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "droneId", required = false) Long droneId) {
        return ApiResponse.success(handlingService.list(status, droneId));
    }

    @GetMapping("/by-drone/{droneId}")
    public ApiResponse<List<Views.HandlingView>> byDrone(@PathVariable("droneId") Long droneId) {
        return ApiResponse.success(handlingService.listByDrone(droneId));
    }

    /** 从飞行日志的异常架次进入处置 */
    @GetMapping("/by-flight-log/{flightLogId}")
    public ApiResponse<List<Views.HandlingView>> byFlightLog(@PathVariable("flightLogId") Long flightLogId) {
        return ApiResponse.success(handlingService.listByFlightLog(flightLogId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Views.HandlingView> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(handlingService.detail(id));
    }

    /** 值班主管下拉：在队三级 / 教员 */
    @GetMapping("/supervisor-options")
    public ApiResponse<List<Views.PilotOptionView>> supervisorOptions() {
        return ApiResponse.success(handlingService.supervisorOptions());
    }

    /** 继续观察 */
    @PostMapping("/{id}/observe")
    public ApiResponse<Views.HandlingView> observe(@PathVariable("id") Long id,
                                                   @Valid @RequestBody HandlingDtos.ActionReq req) {
        return ApiResponse.success("已登记继续观察", handlingService.observe(id, req));
    }

    /** 停飞机身：关联无人机立即转为维修中，待飞作业单需改派 */
    @PostMapping("/{id}/ground")
    public ApiResponse<Views.HandlingView> ground(@PathVariable("id") Long id,
                                                  @Valid @RequestBody HandlingDtos.ActionReq req) {
        return ApiResponse.success("已停飞机身，未起飞作业单不得再选用该机", handlingService.ground(id, req));
    }

    /** 解除：处置结论必填，无其他未结束处置后机身方可恢复在库 */
    @PostMapping("/{id}/resolve")
    public ApiResponse<Views.HandlingView> resolve(@PathVariable("id") Long id,
                                                   @Valid @RequestBody HandlingDtos.ResolveReq req) {
        return ApiResponse.success("处置已解除", handlingService.resolve(id, req));
    }
}
