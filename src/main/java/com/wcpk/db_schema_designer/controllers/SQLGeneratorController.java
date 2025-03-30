package com.wcpk.db_schema_designer.controllers;

import com.wcpk.db_schema_designer.dto.SchemaRequest;
import com.wcpk.db_schema_designer.service.SQLGeneratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SQLGeneratorController {

    private final SQLGeneratorService sqlGeneratorService;

    public SQLGeneratorController(SQLGeneratorService sqlGeneratorService) {
        this.sqlGeneratorService = sqlGeneratorService;
    }

    @PostMapping("/generate-sql")
    public ResponseEntity<String> generateSQL(@RequestBody SchemaRequest schemaRequest) {
        String sqlScript = sqlGeneratorService.generateAndExecuteSQL(schemaRequest);
        return ResponseEntity.ok(sqlScript);
    }
}

