package com.school.checkin.dto;

import com.school.checkin.vo.ImportErrorVO;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

@Data
public class ImportErrorExportRequest {
    @NotBlank(message = "importType 不能为空")
    private String importType;
    private List<ImportErrorVO> errors = new ArrayList<>();
}
