package com.school.checkin.vo;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
public class ExportCheckInSummaryRow {

    @ColumnWidth(12)
    @ExcelProperty("年级")
    private String gradeName;

    @ColumnWidth(12)
    @ExcelProperty("班级")
    private String className;

    @ColumnWidth(16)
    @ExcelProperty("学生姓名")
    private String studentName;

    @ColumnWidth(18)
    @ExcelProperty("学号")
    private String studentNo;

    @ColumnWidth(14)
    @ExcelProperty("早上次数")
    private Integer morningCount;

    @ColumnWidth(14)
    @ExcelProperty("晚上次数")
    private Integer eveningCount;

    @ColumnWidth(14)
    @ExcelProperty("累计次数")
    private Integer totalCount;

    @ColumnWidth(18)
    @ExcelProperty("段位")
    private String rankTitle;

    @ExcelIgnore
    private String rankCssCode;
}
