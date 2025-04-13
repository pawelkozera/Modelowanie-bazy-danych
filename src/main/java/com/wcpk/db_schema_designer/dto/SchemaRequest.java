package com.wcpk.db_schema_designer.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SchemaRequest {
    private List<Table> tables;
    private List<Relationship> relationships;

    @Getter
    @Setter
    public static class Table {
        private String id;
        private String name;
        private List<Field> fields;
    }

    @Getter
    @Setter
    public static class Field {
        private String name;
        private String type;
        private boolean primaryKey;
        private boolean unique;
        private boolean nullable;
    }

    @Getter
    @Setter
    public static class Relationship {
        private String id;
        private String type;
        private String sourceEntityId;
        private String targetEntityId;
        private String sourceTableName;
        private String targetTableName;
        private String sourceCardinality;
        private String targetCardinality;
    }
}
