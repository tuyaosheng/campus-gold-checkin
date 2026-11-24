package com.school.checkin.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportResultVO {
    private Integer totalRows;
    private Integer createCount;
    private Integer updateCount;
    private Integer successCount;
    private Integer skippedCount;
    private Integer errorCount;
    private Boolean preview;
    private List<ImportErrorVO> errors = new ArrayList<>();
}
