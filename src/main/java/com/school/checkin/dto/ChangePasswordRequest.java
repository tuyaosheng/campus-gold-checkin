package com.school.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "oldPassword 不能为空")
    private String oldPassword;

    @NotBlank(message = "newPassword 不能为空")
    private String newPassword;
}
