package com.wcpk.db_schema_designer.service;

import com.wcpk.db_schema_designer.dto.SchemaRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SQLGeneratorService {

    private final JdbcTemplate jdbcTemplate;

    public String generateAndExecuteSQL(SchemaRequest schemaRequest) {
        try {
            Map<String, SchemaRequest.Table> tableMap = new HashMap<>();
            Map<String, SchemaRequest.Table> tableNameMap = new HashMap<>();
            for (SchemaRequest.Table table : schemaRequest.getTables()) {
                tableMap.put(table.getId(), table);
                tableNameMap.put(table.getName(), table);
            }

            StringBuilder sqlScript = new StringBuilder();
            for (SchemaRequest.Table table : schemaRequest.getTables()) {
                sqlScript.append(generateCreateTableSQL(table)).append("\n");
            }
            for (SchemaRequest.Relationship relationship : schemaRequest.getRelationships()) {
                switch (relationship.getType()) {
                    case "one-to-one":
                        sqlScript.append(generateOneToOneSQL(relationship, tableNameMap)).append("\n");
                        break;
                    case "one-to-many":
                        sqlScript.append(generateOneToManySQL(relationship, tableNameMap)).append("\n");
                        break;
                    case "many-to-many":
                        sqlScript.append(generateManyToManySQL(relationship, tableNameMap)).append("\n");
                        break;
                }
            }

             clearDatabase();
             executeSQLScript(sqlScript.toString());

            return "" + sqlScript;
        } catch (Exception e) {
            return "Błąd podczas wykonywania skryptu:\n" + e.getMessage();
        }
    }

    private String generateCreateTableSQL(SchemaRequest.Table table) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(table.getName()).append(" (\n");

        List<String> pkFields = new ArrayList<>();

        for (int i = 0; i < table.getFields().size(); i++) {
            SchemaRequest.Field field = table.getFields().get(i);
            sb.append("    ").append(field.getName()).append(" ").append(field.getType());
            if (!field.isNullable()) sb.append(" NOT NULL");
            if (field.isUnique()) sb.append(" UNIQUE");

            if (i < table.getFields().size() - 1 || table.getFields().stream().anyMatch(SchemaRequest.Field::isPrimaryKey)) {
                sb.append(",");
            }
            sb.append("\n");

            if (field.isPrimaryKey()) {
                pkFields.add(field.getName());
            }
        }

        if (!pkFields.isEmpty()) {
            sb.append("    PRIMARY KEY (").append(String.join(", ", pkFields)).append(")\n");
        }

        sb.append(");\n");
        return sb.toString();
    }

    private String generateOneToOneSQL(SchemaRequest.Relationship rel, Map<String, SchemaRequest.Table> tableNameMap) {
        String pkSource = getPrimaryKeyColumn(rel.getSourceTableName(), tableNameMap);
        String pkType = getPrimaryKeyType(rel.getSourceTableName(), tableNameMap);

        return "ALTER TABLE " + rel.getTargetTableName() + " ADD COLUMN " +
                pkSource + " " + pkType + " UNIQUE,\n" +
                "ADD CONSTRAINT fk_" + rel.getTargetTableName() + "_" + rel.getSourceTableName() +
                " FOREIGN KEY (" + pkSource + ") REFERENCES " + rel.getSourceTableName() + "(" + pkSource + ");\n";
    }

    private String generateOneToManySQL(SchemaRequest.Relationship rel, Map<String, SchemaRequest.Table> tableNameMap) {
        String manyTable;
        String oneTable;

        if ("many".equalsIgnoreCase(rel.getSourceCardinality())) {
            manyTable = rel.getSourceTableName();
            oneTable = rel.getTargetTableName();
        } else {
            manyTable = rel.getTargetTableName();
            oneTable = rel.getSourceTableName();
        }

        String pkOne = getPrimaryKeyColumn(oneTable, tableNameMap);
        String pkType = getPrimaryKeyType(oneTable, tableNameMap);

        return "ALTER TABLE " + manyTable + " ADD COLUMN " +
                pkOne + " " + pkType + ",\n" +
                "ADD CONSTRAINT fk_" + manyTable + "_" + oneTable +
                " FOREIGN KEY (" + pkOne + ") REFERENCES " + oneTable + "(" + pkOne + ");\n";
    }

    private String generateManyToManySQL(SchemaRequest.Relationship rel, Map<String, SchemaRequest.Table> tableNameMap) {
        String intermediateTable = rel.getSourceTableName() + "_" + rel.getTargetTableName();
        String pkSource = getPrimaryKeyColumn(rel.getSourceTableName(), tableNameMap);
        String pkSourceType = getPrimaryKeyType(rel.getSourceTableName(), tableNameMap);
        String pkTarget = getPrimaryKeyColumn(rel.getTargetTableName(), tableNameMap);
        String pkTargetType = getPrimaryKeyType(rel.getTargetTableName(), tableNameMap);

        return "CREATE TABLE " + intermediateTable + " (\n" +
                "    " + pkSource + " " + pkSourceType + " NOT NULL,\n" +
                "    " + pkTarget + " " + pkTargetType + " NOT NULL,\n" +
                "    PRIMARY KEY (" + pkSource + ", " + pkTarget + "),\n" +
                "    FOREIGN KEY (" + pkSource + ") REFERENCES " + rel.getSourceTableName() + "(" + pkSource + "),\n" +
                "    FOREIGN KEY (" + pkTarget + ") REFERENCES " + rel.getTargetTableName() + "(" + pkTarget + ")\n" +
                ");\n";
    }

    private String getPrimaryKeyColumn(String tableName, Map<String, SchemaRequest.Table> tableNameMap) {
        SchemaRequest.Table table = tableNameMap.get(tableName);
        if (table != null) {
            for (SchemaRequest.Field field : table.getFields()) {
                if (field.isPrimaryKey()) {
                    return field.getName();
                }
            }
        }
        throw new RuntimeException("Nie znaleziono klucza głównego dla tabeli " + tableName);
    }

    private String getPrimaryKeyType(String tableName, Map<String, SchemaRequest.Table> tableNameMap) {
        SchemaRequest.Table table = tableNameMap.get(tableName);
        if (table != null) {
            for (SchemaRequest.Field field : table.getFields()) {
                if (field.isPrimaryKey()) {
                    return field.getType();
                }
            }
        }
        throw new RuntimeException("Nie znaleziono typu klucza głównego dla tabeli " + tableName);
    }

    private void executeSQLScript(String sqlScript) {
        for (String sql : sqlScript.split(";")) {
            if (!sql.trim().isEmpty()) {
                jdbcTemplate.execute(sql.trim() + ";");
            }
        }
    }
    private void clearDatabase() {
        jdbcTemplate.execute("DROP SCHEMA public CASCADE;");
        jdbcTemplate.execute("CREATE SCHEMA public;");
    }

}
