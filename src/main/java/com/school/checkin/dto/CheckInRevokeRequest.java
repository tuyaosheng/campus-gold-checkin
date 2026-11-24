package com.school.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class CheckInRevokeRequest {

    @NotNull(message = "studentId 不能为空")
    private Long studentId;

    @NotBlank(message = "uniqueCode 不能为空")
    private String uniqueCode;
}
