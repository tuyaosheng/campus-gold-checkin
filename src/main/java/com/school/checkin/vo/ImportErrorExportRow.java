package com.school.checkin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
public class ImportErrorExportRow {
    @ColumnWidth(14)
    @ExcelProperty("导入类型")
    private String importType;

    @ColumnWidth(12)
    @ExcelProperty("Excel行号")
    private Integer rowNumber;

    @ColumnWidth(48)
    @ExcelProperty("失败原因")
    private String reason;
}
