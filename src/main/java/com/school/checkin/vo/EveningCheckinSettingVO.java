package com.school.checkin.vo;

import lombok.Data;

import java.util.List;

@Data
public class EveningCheckinSettingVO {
    private String defaultReturnWeekday;
    private String overrideReturnDate;
    private String suggestedReturnDate;
    private String effectiveReturnDate;
    private List<EveningCheckinRuleRowVO> rules;
}
