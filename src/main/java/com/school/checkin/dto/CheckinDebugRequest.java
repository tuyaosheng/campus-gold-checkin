package com.school.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class CheckinDebugRequest {

    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}
