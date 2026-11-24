package com.school.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class EveningCheckinRuleRowRequest {

    @NotBlank(message = "gradeName 不能为空")
    private String gradeName;

    @NotBlank(message = "morningStart 不能为空")
    private String morningStart;

    @NotBlank(message = "morningEnd 不能为空")
    private String morningEnd;

    @NotBlank(message = "normalStart 不能为空")
    private String normalStart;

    @NotBlank(message = "normalEnd 不能为空")
    private String normalEnd;

    @NotBlank(message = "returnStart 不能为空")
    private String returnStart;

    @NotBlank(message = "returnEnd 不能为空")
    private String returnEnd;
}
