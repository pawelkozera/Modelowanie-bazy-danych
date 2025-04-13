package com.wcpk.db_schema_designer.service;

import com.wcpk.db_schema_designer.dto.SchemaRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.Console;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SQLGeneratorService {
    private final JdbcTemplate jdbcTemplate;

    private static final Set<String> RESERVED_KEYWORDS = Set.of(
            "select", "insert", "delete", "update", "from", "where", "user", "group", "table", "order", "by", "limit", "drop"
    );

    public String generateAndExecuteSQL(SchemaRequest schemaRequest) {
        try {
            validateSchemaRequest(schemaRequest);

            Set<String> manyToManyTables = new HashSet<>();
            String sqlScript = generateSQLScript(schemaRequest, manyToManyTables);
            executeSQLScript(sqlScript);
            dropTables(schemaRequest, manyToManyTables);
            return sqlScript;
        } catch (Exception e) {
            dropTables(schemaRequest, new HashSet<>());
            return "Błąd podczas wykonywania skryptu:\n" + e.getMessage();
        }
    }

    private void validateSchemaRequest(SchemaRequest schemaRequest) {
        for (SchemaRequest.Table table : schemaRequest.getTables()) {
            String tableName = table.getName().toLowerCase();
            if (RESERVED_KEYWORDS.contains(tableName)) {
                throw new IllegalArgumentException("Table name '" + tableName + "' is a reserved keyword.");
            }

            for (SchemaRequest.Field field : table.getFields()) {
                String fieldName = field.getName().toLowerCase();
                if (RESERVED_KEYWORDS.contains(fieldName)) {
                    throw new IllegalArgumentException("Field name '" + fieldName + "' is a reserved keyword.");
                }
            }
        }
    }

    private String generateSQLScript(SchemaRequest schemaRequest, Set<String> manyToManyTables) {
        StringBuilder sqlScript = new StringBuilder();

        for (SchemaRequest.Table table : schemaRequest.getTables()) {
            for (SchemaRequest.Relationship relationship : table.getRelationships()) {
                if (relationship.isOneToOne()) {
                    for (SchemaRequest.Field field : table.getFields()) {
                        if (field.getName().equals(relationship.getFieldName())) {
                            field.setUnique(true);
                        }
                    }
                }
            }
        }

        for (SchemaRequest.Table table : schemaRequest.getTables()) {
            boolean hasNonManyToManyRelationships = table.getRelationships().stream()
                    .anyMatch(rel -> !rel.isManyToMany());

            sqlScript.append(generateCreateTableSQL(table.getName()));
            sqlScript.append(generateFieldSQL(table.getFields(), hasNonManyToManyRelationships));
            sqlScript.append(generateRelationshipSQL(table.getRelationships()));
            sqlScript.append(generateEndTableSQL());
        }

        for (SchemaRequest.Table table : schemaRequest.getTables()) {
            for (SchemaRequest.Relationship rel : table.getRelationships()) {
                if (rel.isManyToMany()) {
                    String intermediateTableName = generateManyToManyTableName(
                            table.getName(),
                            rel.getReferencedTable()
                    );

                    if (!manyToManyTables.contains(intermediateTableName)) {
                        SchemaRequest.Relationship rel2 = schemaRequest.getTables().stream()
                                .filter(t -> t.getName().equals(rel.getReferencedTable()))
                                .flatMap(t -> t.getRelationships().stream())
                                .filter(r -> r.isManyToMany() && r.getReferencedTable().equals(table.getName()))
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Brak drugiej relacji many-to-many"));

                        sqlScript.append(generateManyToManyTableSQL(
                                intermediateTableName,
                                table.getName(),
                                rel.getReferencedTable(),
                                rel,
                                rel2
                        ));
                        manyToManyTables.add(intermediateTableName);
                    }
                }
            }
        }

        return sqlScript.toString();
    }

    private void executeSQLScript(String sqlScript) {
        String[] sqlStatements = sqlScript.split(";");
        for (String sql : sqlStatements) {
            if (!sql.trim().isEmpty()) {
                jdbcTemplate.execute(sql.trim() + ";");
            }
        }
    }

    private void dropTables(SchemaRequest schemaRequest, Set<String> manyToManyTables) {
        for (String m2mTable : manyToManyTables) {
            String dropSQL = "DROP TABLE IF EXISTS " + m2mTable + " CASCADE;";
            jdbcTemplate.execute(dropSQL);
        }

        List<SchemaRequest.Table> tables = schemaRequest.getTables();
        for (int i = tables.size() - 1; i >= 0; i--) {
            String dropSQL = "DROP TABLE IF EXISTS " + tables.get(i).getName() + " CASCADE;";
            jdbcTemplate.execute(dropSQL);
        }
    }

    private String generateCreateTableSQL(String tableName) {
        return "CREATE TABLE " +
                tableName +
                " (\n";
    }

    private String generateFieldSQL(List<SchemaRequest.Field> fields, boolean hasRelationships) {
        StringBuilder fieldSQL = new StringBuilder();
        List<String> primaryKeys = new ArrayList<>();
        List<String> fieldLines = new ArrayList<>();

        for (SchemaRequest.Field field : fields) {
            StringBuilder line = new StringBuilder();
            line.append("    ")
                    .append(field.getName())
                    .append(" ")
                    .append(field.getType());

            if (field.isUnique()) line.append(" UNIQUE");
            if (!field.isNullable()) line.append(" NOT NULL");

            fieldLines.add(line.toString());

            if (field.isPrimaryKey()) {
                primaryKeys.add(field.getName());
            }
        }

        if (!primaryKeys.isEmpty()) {
            fieldLines.add("    PRIMARY KEY (" + String.join(", ", primaryKeys) + ")");
        }

        fieldSQL.append(String.join(",\n", fieldLines));
        if (hasRelationships) {
            fieldSQL.append(",\n");
        } else {
            fieldSQL.append("\n");
        }

        return fieldSQL.toString();
    }

    private String generateRelationshipSQL(List<SchemaRequest.Relationship> relationships) {
        List<String> lines = new ArrayList<>();

        for (SchemaRequest.Relationship relationship : relationships) {
            if (relationship.isManyToMany()) continue;

            lines.add("    FOREIGN KEY (" + relationship.getFieldName() + ") REFERENCES " +
                    relationship.getReferencedTable() + "(" + relationship.getReferencedField() + ")");
        }

        return String.join(",\n", lines) + (lines.isEmpty() ? "" : "\n");
    }

    private String generateManyToManyTableSQL(String tableName,
                                              String tableA,
                                              String tableB,
                                              SchemaRequest.Relationship relA,
                                              SchemaRequest.Relationship relB) {
        String[] tables = {tableA, tableB};
        Arrays.sort(tables, String.CASE_INSENSITIVE_ORDER);

        String sortedTableA = tables[0];
        String sortedTableB = tables[1];

        String fieldA = sortedTableA.equals(tableA) ? relA.getReferencedField() : relB.getReferencedField();
        String fieldB = sortedTableB.equals(tableB) ? relB.getReferencedField() : relA.getReferencedField();

        return "CREATE TABLE " + tableName + " (\n" +
                "    " + sortedTableA + "_" + fieldA + " BIGINT NOT NULL,\n" +
                "    " + sortedTableB + "_" + fieldB + " BIGINT NOT NULL,\n" +
                "    PRIMARY KEY (" + sortedTableA + "_" + fieldA + ", " + sortedTableB + "_" + fieldB + "),\n" +
                "    FOREIGN KEY (" + sortedTableA + "_" + fieldA + ") REFERENCES " + sortedTableA + "(" + fieldA + "),\n" +
                "    FOREIGN KEY (" + sortedTableB + "_" + fieldB + ") REFERENCES " + sortedTableB + "(" + fieldB + ")\n" +
                ");\n\n";
    }

    private String generateManyToManyTableName(String tableA, String tableB) {
        String[] tables = {tableA, tableB};
        Arrays.sort(tables, String.CASE_INSENSITIVE_ORDER);
        return tables[0] + "_" + tables[1];
    }

    private String generateEndTableSQL() {
        return ");\n\n";
    }
}