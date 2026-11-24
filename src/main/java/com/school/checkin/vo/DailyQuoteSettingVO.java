package com.school.checkin.vo;

import lombok.Data;

@Data
public class DailyQuoteSettingVO {
    private String morningQuotesText;
    private Integer morningQuoteCount;
    private String morningTodayQuote;
    private Integer morningStartIndex;
    private Integer morningTodayIndex;
    private String morningStartDate;
    private String eveningQuotesText;
    private Integer eveningQuoteCount;
    private String eveningTodayQuote;
    private Integer eveningStartIndex;
    private Integer eveningTodayIndex;
    private String eveningStartDate;
}
