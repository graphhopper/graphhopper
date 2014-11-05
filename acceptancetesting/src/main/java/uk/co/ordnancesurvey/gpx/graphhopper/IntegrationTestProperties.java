package uk.co.ordnancesurvey.gpx.graphhopper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds all properties for a test run. By default uses the file test.properties
 * to load but by setting the system property testProperties this can be
 * overridden. Any property defined for the test run can also be overridden at
 * an individual system property level.
 */
public final class IntegrationTestProperties {
    private static final String YES = "yes";
    private static final String TRUE = "true";
    private static final String BOOL_FLAG = "1";
    private static final String DEFAULT_PROPERTIES_FILE = "./target/test-classes/test.properties";
    private static final String LOADED_TESTING_PROPERTIES_FILE = "loaded testing properties file: {}";
    private static final String PROPERTIES_FILE_UNAVAILABLE = "properties file: {} unavailable";
    private static final String TEST_PROPERTIES = "testProperties";
    private static final String OVERRIDING_FROM_SYSTEM_PROPERTY = "Overriding {} from system property {}";
    private static final String ERROR_DURING_LOAD = "ERROR DURING LOAD";

    public static final String AWS_SECRET_KEY = "aws.secretKey";
    public static final String AWS_ACCESS_KEY_ID = "aws.accessKeyId";

    private Properties testProperties = null;
    private static IntegrationTestProperties instance = null;
    private static final Logger LOG = LoggerFactory
            .getLogger(IntegrationTestProperties.class);

    public static String getTestProperty(final String property) {
        loadIfRequired();
        return instance.getProperty(property);
    }

    public static int getTestPropertyInt(final String property) {
        String propertyValue = getTestProperty(property);
        return Integer.parseInt(propertyValue);
    }

    public static boolean getTestPropertyBool(final String property) {
        String testProperty = getTestProperty(property);
        boolean valid = false;
        if (null != testProperty) {
            testProperty = testProperty.toLowerCase();
            valid = (TRUE.equals(testProperty) || YES.equals(testProperty) || BOOL_FLAG
                    .equals(testProperty));
        }
        return valid;
    }

    private static synchronized void loadIfRequired() {
        if (null == instance) {
            instance = new IntegrationTestProperties();
        }
    }

    public String getProperty(String property) {
        return testProperties.getProperty(property);
    }

    private IntegrationTestProperties() {
        testProperties = new Properties();

        File file = new File(".");
        LOG.debug(file.getAbsolutePath());
        File resource = selectPropertyFile();
        loadProperties(resource);
        processSystemOverrides();
    }

    private File selectPropertyFile() {
        File resource = new File(DEFAULT_PROPERTIES_FILE);
        final String alternateFileName = System.getProperty(TEST_PROPERTIES);
        LOG.info("Loading testing properties file:" + alternateFileName);
        if (null != alternateFileName) {
            final File altResource = new File(alternateFileName);
            if (altResource.canRead()) {
                resource = altResource;
            } else {
                LOG.info(PROPERTIES_FILE_UNAVAILABLE, alternateFileName);
            }
        }
        return resource;
    }

    private void loadProperties(File resource) {
        try (final InputStream resourceAsStream = new FileInputStream(resource)) {
            testProperties.load(resourceAsStream);
            LOG.info(LOADED_TESTING_PROPERTIES_FILE, resource.getAbsolutePath());
        } catch (IOException e) {
            LOG.error(ERROR_DURING_LOAD, e);
        }
    }

    private void processSystemOverrides() {
        copyProperties(System.getProperties(), testProperties);
    }

    private void copyProperties(Properties srcProp, Properties destProp) {
        for (Enumeration propertyNames = srcProp.propertyNames(); propertyNames
                .hasMoreElements();) {
            Object key = propertyNames.nextElement();
            Object value = srcProp.get(key);
            if (destProp.containsKey(key)) {
                LOG.info(OVERRIDING_FROM_SYSTEM_PROPERTY, key, value);
            }
            destProp.put(key, value);
        }
    }
}