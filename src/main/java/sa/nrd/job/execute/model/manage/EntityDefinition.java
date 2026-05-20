package sa.nrd.job.execute.model.manage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * One entity definition loaded from XML.
 *EntityDefinition is a plain Java model class used for XML-driven metadata and JDBC-based dynamic insert logic.
 * Meaning:
 * - entityName: logical name (used as registry key)
 * - tableName/schema: where to insert in DB
 * - fieldMapping: javaFieldName -> dbColumnName
 * - rows: list of rows to insert (each map key is javaFieldName)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityDefinition {

    private String entityName;
    private String tableName;
    private String schema;

    /** Mapping: XML field name (javaFieldName) -> DB column name. */
    private Map<String, String> fieldMapping;

    /** Rows to insert. Each row map uses XML field names as keys. */
    private List<Map<String, Object>> rows;

    /**
     * @return fully-qualified table name including schema (dbo.TABLE) if schema is present.
     */
    public String getFullTableName() {
        if (schema != null && !schema.trim().isEmpty()) {
            return schema + "." + tableName;
        }
        return tableName;
    }
}