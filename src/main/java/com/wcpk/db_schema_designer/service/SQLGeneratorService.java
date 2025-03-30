package com.wcpk.db_schema_designer.service;

import com.wcpk.db_schema_designer.dto.SchemaRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.Console;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SQLGeneratorService {
    private final JdbcTemplate jdbcTemplate;

    public String generateAndExecuteSQL(SchemaRequest schemaRequest) {
        Set<String> manyToManyTables = new HashSet<>(); // Track M2M tables
        String sqlScript = generateSQLScript(schemaRequest, manyToManyTables);

        try {
            executeSQLScript(sqlScript);
            dropTables(schemaRequest, manyToManyTables); // Pass M2M tables to drop
            return "Skrypt SQL wykonany pomyślnie:\n" + sqlScript;
        } catch (Exception e) {
            dropTables(schemaRequest, manyToManyTables); // Ensure cleanup on error
            return "Błąd podczas wykonywania skryptu:\n" + e.getMessage();
        }
    }

    public String generateSQLScript(SchemaRequest schemaRequest, Set<String> manyToManyTables) {
        StringBuilder sqlScript = new StringBuilder();

        // First pass: Create main tables
        for (SchemaRequest.Table table : schemaRequest.getTables()) {
            boolean hasNonManyToManyRelationships = table.getRelationships().stream()
                    .anyMatch(rel -> !rel.isManyToMany());

            sqlScript.append(generateCreateTableSQL(table.getName()));
            sqlScript.append(generateFieldSQL(table.getFields(), hasNonManyToManyRelationships));
            sqlScript.append(generateRelationshipSQL(table.getRelationships()));
            sqlScript.append(generateEndTableSQL());
        }

        // Second pass: Create M2M tables and track them
        for (SchemaRequest.Table table : schemaRequest.getTables()) {
            for (SchemaRequest.Relationship rel : table.getRelationships()) {
                if (rel.isManyToMany()) {
                    String intermediateTableName = generateManyToManyTableName(
                            table.getName(),
                            rel.getReferencedTable()
                    );

                    if (!manyToManyTables.contains(intermediateTableName)) {
                        sqlScript.append(generateManyToManyTableSQL(
                                intermediateTableName,
                                table.getName(),
                                rel.getReferencedTable()
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

        for (int i = 0; i < fields.size(); i++) {
            SchemaRequest.Field field = fields.get(i);
            fieldSQL.append("    ")
                    .append(field.getName())
                    .append(" ")
                    .append(field.getType());

            if (field.isPrimaryKey()) fieldSQL.append(" PRIMARY KEY");
            if (field.isUnique()) fieldSQL.append(" UNIQUE");
            if (!field.isNullable()) fieldSQL.append(" NOT NULL");

            if (i < fields.size() - 1 || hasRelationships) {
                fieldSQL.append(",");
            }
            fieldSQL.append("\n");
        }

        return fieldSQL.toString();
    }

    private String generateRelationshipSQL(List<SchemaRequest.Relationship> relationships) {
        StringBuilder relationshipSQL = new StringBuilder();

        for (int j = 0; j < relationships.size(); j++) {
            SchemaRequest.Relationship relationship = relationships.get(j);

            if (relationship.isManyToMany()) {
                continue;
            }

            relationshipSQL.append("    FOREIGN KEY (")
                    .append(relationship.getFieldName())
                    .append(") REFERENCES ")
                    .append(relationship.getReferencedTable())
                    .append("(")
                    .append(relationship.getReferencedField())
                    .append(")");

            if (j < relationships.size() - 1) {
                relationshipSQL.append(",");
            }
            relationshipSQL.append("\n");
        }

        return relationshipSQL.toString();
    }

    private String generateManyToManyTableSQL(String tableName, String tableA, String tableB) {
        String[] tables = {tableA, tableB};
        Arrays.sort(tables, String.CASE_INSENSITIVE_ORDER);
        String sortedTableA = tables[0];
        String sortedTableB = tables[1];

        return "CREATE TABLE " + tableName + " (\n" +
                "    " + sortedTableA + "_id BIGINT NOT NULL,\n" +
                "    " + sortedTableB + "_id BIGINT NOT NULL,\n" +
                "    PRIMARY KEY (" + sortedTableA + "_id, " + sortedTableB + "_id),\n" +
                "    FOREIGN KEY (" + sortedTableA + "_id) REFERENCES " + sortedTableA + "(id),\n" +
                "    FOREIGN KEY (" + sortedTableB + "_id) REFERENCES " + sortedTableB + "(id)\n" +
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

