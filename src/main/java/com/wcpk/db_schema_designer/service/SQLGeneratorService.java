package com.wcpk.db_schema_designer.service;

import com.wcpk.db_schema_designer.dto.SchemaRequest;
import org.springframework.stereotype.Service;

@Service
public class SQLGeneratorService {

    public String generateSQLScript(SchemaRequest schemaRequest) {
        StringBuilder sqlScript = new StringBuilder();

        for (SchemaRequest.Table table : schemaRequest.getTables()) {
            sqlScript.append("CREATE TABLE ")
                    .append(table.getName())
                    .append(" (\n");

            for (SchemaRequest.Field field : table.getFields()) {
                sqlScript.append(field.getName())
                        .append(" ")
                        .append(field.getType())
                        .append(field.isPrimaryKey() ? " PRIMARY KEY" : "")
                        .append(",\n");
            }
            sqlScript.append(");\n\n");
        }

        return sqlScript.toString();
    }
}

