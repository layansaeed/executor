package sa.nrd.job.execute.service.bean;

import sa.nrd.job.execute.model.manage.EntityDefinition;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores {@link EntityDefinition} objects loaded from entities-config.xml in memory.
 * Key: entityName (XML attribute "name")
 * Value: parsed {@link EntityDefinition}
 */
@Component
public class EntityDefinitionRegistry {

    /** Keeps the same order as XML (for easier debugging). */
    private final Map<String, EntityDefinition> entities = new LinkedHashMap<>();

    /**
     * Add/replace one entity definition.
     *
     * @param def entity definition parsed from XML
     */
    public void put(EntityDefinition def) {
        String entityName = def.getEntityName();

        if (entities.containsKey(entityName)) {
            throw new IllegalStateException("Duplicate entity name found in XML: " + entityName);
        }
        entities.put(entityName, def);
    }

    /**
     * Get entity definition by name.
     *
     * @param entityName XML "name" attribute
     * @return stored entity definition
     * @throws RuntimeException if not found
     */
    public EntityDefinition get(String entityName) {
        EntityDefinition def = entities.get(entityName);
        if (def == null) {
            throw new RuntimeException("No entity loaded with name: " + entityName);
        }
        return def;
    }

    /**
     * Read-only view of all entity definitions.
     *
     * @return unmodifiable map of entityName -> EntityDefinition
     */
    public Map<String, EntityDefinition> getAll() {
        return (entities);
    }

}