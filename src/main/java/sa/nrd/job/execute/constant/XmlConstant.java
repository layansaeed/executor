package sa.nrd.job.execute.constant;

/**
 * Constants for entities-config.xml parsing.
 * Keeps all tag and attribute names in one place.
 */
public final class XmlConstant {

    private XmlConstant() { }


    // ===== XML tag names =====
    public static final String TAG_ENTITY = "entity";
    public static final String TAG_FIELDS = "fields";
    public static final String TAG_FIELD  = "field";
    public static final String TAG_DATA   = "data";
    public static final String TAG_VALUE  = "value";

    // ===== XML attribute names =====
    public static final String ATTR_ENTITY_NAME  = "name";
    public static final String ATTR_TABLE        = "table";
    public static final String ATTR_SCHEMA       = "schema";
    public static final String ATTR_FIELD_NAME   = "name";
    public static final String ATTR_FIELD_COLUMN = "column";
    public static final String ATTR_VALUE_FIELD  = "field";
}