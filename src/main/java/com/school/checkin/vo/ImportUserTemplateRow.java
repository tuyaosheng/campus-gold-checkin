package com.school.checkin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
public class ImportUserTemplateRow {

    @ColumnWidth(16)
    @ExcelProperty("姓名")
    private String realName;

    @ColumnWidth(26)
    @ExcelProperty("登录账号")
    private String username;

    @ColumnWidth(16)
    @ExcelProperty("角色")
    private String roleCode;

    @ColumnWidth(16)
    @ExcelProperty("年级")
    private String gradeName;

    @ColumnWidth(14)
    @ExcelProperty("班号")
    private String classNo;

    @ColumnWidth(18)
    @ExcelProperty("初始密码")
    private String password;
}
