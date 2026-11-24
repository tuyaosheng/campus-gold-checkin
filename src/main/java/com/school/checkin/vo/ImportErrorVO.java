package com.school.checkin.vo;

import lombok.Data;

@Data
public class ImportErrorVO {
    private Integer rowNumber;
    private String reason;
}
