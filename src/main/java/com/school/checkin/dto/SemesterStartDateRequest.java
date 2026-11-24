package com.school.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class SemesterStartDateRequest {

    @NotBlank(message = "date 不能为空")
    private String date;
}
