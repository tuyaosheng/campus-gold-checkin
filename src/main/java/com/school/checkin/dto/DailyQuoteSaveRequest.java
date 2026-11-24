package com.school.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class DailyQuoteSaveRequest {

    @NotNull(message = "morningStartIndex 不能为空")
    private Integer morningStartIndex;

    @NotNull(message = "eveningStartIndex 不能为空")
    private Integer eveningStartIndex;

    private String morningQuotesText;

    private String eveningQuotesText;
}
