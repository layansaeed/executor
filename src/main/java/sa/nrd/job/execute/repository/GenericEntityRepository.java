package sa.nrd.job.execute.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.nrd.job.execute.model.manage.EntityDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class GenericEntityRepository {
    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final JdbcTemplate jdbcTemplate;

    public GenericEntityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts all rows and returns count only.
     */
    @Transactional
    public int insertAllRows(EntityDefinition entity, List<Map<String, Object>> rows) {

        int rowsCount = (rows == null) ? 0 : rows.size();

        log.info("Inserting entity '{}' into table '{}' (rows passed = {})",
                entity.getEntityName(), entity.getFullTableName(), rowsCount);

        if (rowsCount == 0) {
            log.info("No rows provided for entity '{}'. Nothing to insert.", entity.getEntityName());
            return 0;
        }

        insertRowsInternal(entity, rows, false);

        log.info("Finished entity '{}': inserted {} row(s).", entity.getEntityName(), rowsCount);
        return rowsCount;
    }

    /**
     * Inserts all rows and returns generated row_id values.
     */
//    @Transactional
//    public List<Long> insertAllRowsReturnIds(EntityDefinition entity, List<Map<String, Object>> rows) {
//
//        int rowsCount = (rows == null) ? 0 : rows.size();
//
//        log.info("Inserting entity '{}' into table '{}' and returning row_id(s) (rows passed = {})",
//                entity.getEntityName(), entity.getFullTableName(), rowsCount);
//
//        if (rowsCount == 0) {
//            log.info("No rows provided for entity '{}'. Nothing to insert.", entity.getEntityName());
//            return Collections.emptyList();
//        }
//
//        List<Long> generatedIds = insertRowsInternal(entity, rows, true);
//
//        log.info("Finished entity '{}': inserted {} row(s). Returned {} row_id(s).",
//                entity.getEntityName(), rowsCount, generatedIds.size());
//
//        return generatedIds;
//    }

    /**
     * Shared insert logic for both public methods.
     * If returnIds = true, returns generated row_id values.
     * If returnIds = false, returns empty list.
     */
    private List<Long> insertRowsInternal(EntityDefinition entity,
                                          List<Map<String, Object>> rows,
                                          boolean returnIds) {

        int rowsCount = rows.size();

        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName(entity.getTableName());

        if (entity.getSchema() != null && !entity.getSchema().trim().isEmpty()) {
            insert = insert.withSchemaName(entity.getSchema());
        }

        String[] columns = entity.getFieldMapping().values().toArray(new String[0]);
        insert = insert.usingColumns(columns);
        insert = insert.usingGeneratedKeyColumns("row_id");

        List<Long> generatedIds = returnIds ? new ArrayList<>(rowsCount) : Collections.<Long>emptyList();

        for (int i = 0; i < rowsCount; i++) {
            Map<String, Object> rowData = rows.get(i);
            Map<String, Object> values = buildInsertValues(entity, rowData);

            if (returnIds) {
                Number id = insert.executeAndReturnKey(values);
                generatedIds.add(id.longValue());

                log.info("Inserted row {}/{} for entity '{}' -> generated row_id={}",
                        i + 1, rowsCount, entity.getEntityName(), id);
            } else {
                insert.execute(values);

                log.info("Inserted row {}/{} for entity '{}'",
                        i + 1, rowsCount, entity.getEntityName());
            }
        }

        return generatedIds;
    }

    /**
     * Converts one runtime row (keys = javaFieldName) into DB insert values (keys = dbColumnName).
     */
    private Map<String, Object> buildInsertValues(EntityDefinition entity, Map<String, Object> rowData) {
        Map<String, Object> values = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : entity.getFieldMapping().entrySet()) {
            Object value = rowData.get(entry.getKey());
            values.put(entry.getValue(), value);
        }

        return values;
    }
}