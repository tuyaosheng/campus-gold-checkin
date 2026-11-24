package com.school.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ResetPasswordRequest {

    @NotNull(message = "userId 不能为空")
    private Long userId;
}
