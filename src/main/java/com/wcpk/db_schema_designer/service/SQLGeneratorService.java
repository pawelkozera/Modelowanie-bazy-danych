package com.wcpk.db_schema_designer.service;

import com.wcpk.db_schema_designer.dto.SchemaRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SQLGeneratorService {

    public String generateSQLScript(SchemaRequest schemaRequest) {
        StringBuilder sqlScript = new StringBuilder();

        for (SchemaRequest.Table table : schemaRequest.getTables()) {
            sqlScript.append(generateCreateTableSQL(table.getName()));
            sqlScript.append(generateFieldSQL(table.getFields(), !table.getRelationships().isEmpty()));
            sqlScript.append(generateRelationshipSQL(table.getRelationships()));
            sqlScript.append(generateEndTableSQL());
        }

        return sqlScript.toString();
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
            if (field.isAutoIncrement()) fieldSQL.append(" AUTO_INCREMENT");
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

    private String generateEndTableSQL() {
        return ");\n\n";
    }
}

