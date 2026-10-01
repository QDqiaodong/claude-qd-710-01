package com.drone.plant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模块五（二）异常架次停飞处置入参。 */
public final class HandlingDtos {

    private HandlingDtos() {
    }

    /** 继续观察 / 停飞机身：必须带值班主管；停飞与观察都要写处置说明 */
    @Data
    public static class ActionReq {

        @NotNull(message = "请先选择值班主管")
        private Long supervisorId;

        /** 处置说明（继续观察原因 / 停飞原因） */
        private String remark;
    }

    /** 解除：值班主管 + 必填处置结论 */
    @Data
    public static class ResolveReq {

        @NotNull(message = "请先选择值班主管")
        private Long supervisorId;

        /** 处置结论：不填不能解除 */
        private String conclusion;
    }
}
