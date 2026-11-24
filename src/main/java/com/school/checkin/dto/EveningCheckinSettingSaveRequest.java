package com.school.checkin.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class EveningCheckinSettingSaveRequest {

    private String overrideReturnDate;

    @Valid
    @NotEmpty(message = "rules 不能为空")
    private List<EveningCheckinRuleRowRequest> rules;
}
