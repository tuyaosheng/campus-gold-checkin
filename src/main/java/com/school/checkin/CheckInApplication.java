package com.school.checkin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.school.checkin.mapper")
@SpringBootApplication
public class CheckInApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckInApplication.class, args);
    }
}
