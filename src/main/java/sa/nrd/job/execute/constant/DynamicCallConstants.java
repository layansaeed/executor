package sa.nrd.job.execute.constant;

public final class DynamicCallConstants {

    private DynamicCallConstants() {
    }

    public static final String CONFIG_URL = "url";
    public static final String CONFIG_HTTP_METHOD = "httpMethod";
    public static final String CONFIG_MEDIA_TYPE = "mediaType";
    public static final String CONFIG_HEADERS_PREFIX = "headers.";

    public static final String CONFIG_AUTHORIZATION = "authorization";

    public static final String CONFIG_AUTH_URL = "auth.url";
    public static final String CONFIG_AUTH_HTTP_METHOD = "auth.httpMethod";
    public static final String CONFIG_AUTH_MEDIA_TYPE = "auth.mediaType";

    public static final String PREFIX_HEADER = "header.";
    public static final String PREFIX_BODY = "body.";
    public static final String PREFIX_AUTH_HEADER = "auth.header.";
    public static final String PREFIX_AUTH_BODY = "auth.body.";

    public static final String AUTH_TYPE_BEARER = "Bearer";

    public static final String TOKEN_ACCESS = "access_token";
    public static final String TOKEN_SIMPLE = "token";
    public static final String TOKEN_JWT = "jwt";

    public static final String DEFAULT_HTTP_METHOD = "POST";


}