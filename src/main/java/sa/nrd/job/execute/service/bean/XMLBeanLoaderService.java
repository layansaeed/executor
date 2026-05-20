package sa.nrd.job.execute.service.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.nrd.job.execute.model.manage.EntityDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.*;

import static sa.nrd.job.execute.constant.XmlConstant.*;

/**
 * reading XML file
 * parsing XML
 * converting it into EntityDefinition
 * putting definitions into registry
 */

@Service
public class XMLBeanLoaderService {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final ApplicationContext context;
    private final EntityDefinitionRegistry registry;

    /** Path to the XML file (loaded from application.properties). */
    private final String entitiesConfigPath;

    public XMLBeanLoaderService(
            ApplicationContext context,
            EntityDefinitionRegistry registry,
            @Value("${app.entities-config.path}") String entitiesConfigPath
    ) {
        this.context = context;
        this.registry = registry;
        this.entitiesConfigPath = entitiesConfigPath;
    }

    /**
     * Loads entity definitions from the XML file and stores them in {@link EntityDefinitionRegistry}.
     *
     * @throws Exception if XML not found or parsing fails
     */
    public void loadBeansFromXML() throws Exception {
        log.info("Loading entity definitions from XML: {}", entitiesConfigPath);

        Resource resource = context.getResource("file:"+entitiesConfigPath);
        if (!resource.exists()) {
            throw new RuntimeException("entities-config.xml not found at path: " + entitiesConfigPath);
        }

        Document doc = parseXMLDocument(resource);
        NodeList entityNodes = doc.getElementsByTagName(TAG_ENTITY);

        int loaded = 0;

        for (int i = 0; i < entityNodes.getLength(); i++) {
            Element entityElement = (Element) entityNodes.item(i);

            EntityDefinition def = parseEntityElement(entityElement);
            registry.put(def);

            loaded++;
            log.info("Loaded entity '{}' -> table '{}'",
                    def.getEntityName(), def.getFullTableName());
        }

        log.info("XML loading complete. Total entities loaded: {}", loaded);
    }

    /**
     * Parses XML file resource into a DOM {@link Document}.
     */
    private Document parseXMLDocument(Resource resource) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(resource.getInputStream());
        doc.getDocumentElement().normalize();
        return doc;
    }

    /**
     * Parses one <entity> node into an {@link EntityDefinition}.
     */
    private EntityDefinition parseEntityElement(Element entityElement) {

        Map<String, String> fieldMapping = parseFieldMapping(entityElement);
        List<Map<String, Object>> rows = parseRows(entityElement);

        return new EntityDefinition(entityElement.getAttribute(ATTR_ENTITY_NAME),entityElement.getAttribute(ATTR_TABLE),
                entityElement.getAttribute(ATTR_SCHEMA), fieldMapping,rows  );
    }

    /**
     * Parses <fields><field .../></fields> section into:
     * javaFieldName -> dbColumnName
     */
    private Map<String, String> parseFieldMapping(Element entityElement) {
        Map<String, String> mapping = new LinkedHashMap<>();

        NodeList fieldsNodes = entityElement.getElementsByTagName(TAG_FIELDS);
        if (fieldsNodes.getLength() == 0) return mapping;

        Element fieldsElement = (Element) fieldsNodes.item(0);
        NodeList fieldNodes = fieldsElement.getElementsByTagName(TAG_FIELD);

        for (int j = 0; j < fieldNodes.getLength(); j++) {
            Element fieldElement = (Element) fieldNodes.item(j);
            String javaField = fieldElement.getAttribute(ATTR_FIELD_NAME);
            String dbColumn = fieldElement.getAttribute(ATTR_FIELD_COLUMN);
            mapping.put(javaField, dbColumn);
        }

        return mapping;
    }

    /**
     * Parses all <data> blocks.
     * Each <data> block represents ONE row.
     */
    private List<Map<String, Object>> parseRows(Element entityElement) {
        List<Map<String, Object>> rows = new ArrayList<>();

        NodeList dataNodes = entityElement.getElementsByTagName(TAG_DATA);
        if (dataNodes.getLength() == 0) return rows;

        for (int i = 0; i < dataNodes.getLength(); i++) {
            Element dataElement = (Element) dataNodes.item(i);
            rows.add(parseOneRow(dataElement));
        }
        return rows;
    }

    /**
     * Parses one <data> block into a row map.
     * Keys are XML field names; values are stored as String.
     */
    private Map<String, Object> parseOneRow(Element dataElement) {
        Map<String, Object> row = new LinkedHashMap<>();
        NodeList valueNodes = dataElement.getElementsByTagName(TAG_VALUE);

        for (int j = 0; j < valueNodes.getLength(); j++) {
            Element valueElement = (Element) valueNodes.item(j);

            row.put(valueElement.getAttribute(ATTR_VALUE_FIELD)/*fieldName*/,
                    valueElement.getTextContent()/*valueStr*/);
        }
        return row;
    }
}