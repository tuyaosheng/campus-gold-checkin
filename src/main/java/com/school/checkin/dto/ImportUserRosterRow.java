package com.school.checkin.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class ImportUserRosterRow {

    @ExcelProperty("姓名")
    private String realName;

    @ExcelProperty("登录账号")
    private String username;

    @ExcelProperty("角色")
    private String roleCode;

    @ExcelProperty("年级")
    private String gradeName;

    @ExcelProperty("班号")
    private String classNo;

    @ExcelProperty("初始密码")
    private String password;
}
