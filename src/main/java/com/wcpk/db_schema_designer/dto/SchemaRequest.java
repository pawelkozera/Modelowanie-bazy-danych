package com.wcpk.db_schema_designer.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SchemaRequest {
    private List<Table> tables;

    @Getter
    @Setter
    public static class Table {
        private String name;
        private List<Field> fields;
    }

    @Getter
    @Setter
    public static class Field {
        private String name;
        private String type;
        private boolean isPrimaryKey;
        private boolean isForeignKey;
        private String references;
    }
}

