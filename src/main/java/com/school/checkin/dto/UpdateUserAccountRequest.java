package com.school.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class UpdateUserAccountRequest {

    @NotNull(message = "userId 不能为空")
    private Long userId;

    @NotBlank(message = "realName 不能为空")
    private String realName;

    @NotBlank(message = "roleCode 不能为空")
    private String roleCode;

    private String gradeName;

    private Long classId;

    @NotNull(message = "enabled 不能为空")
    private Integer enabled;
}
