package com.youzan;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;

import com.youzan.maven.plugin.annotation.classreading.MetadataReader;
import com.youzan.maven.plugin.annotation.classreading.SimpleMetadataReader;

import com.esotericsoftware.yamlbeans.YamlConfig;
import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlWriter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Find annotationNames and generate k8s file descriptors.
 *
 * @author yiji@apache.org
 */
@Mojo(name = "service", defaultPhase = LifecyclePhase.PACKAGE)
public class DubboServiceVisitor extends AbstractMojo {

    /**
     * Class path to be scanned.
     */
    @Parameter(defaultValue = "${project.build.directory}/classes", property = "searchDir")
    private File searchDirectory;

    /**
     * The path of k8s file descriptor .
     */
    @Parameter(defaultValue = "${project.build.directory}", property = "outputDir")
    private File outputDirectory;

    /**
     * K8s file descriptor name.
     */
    @Parameter(defaultValue = "dubbo-service.yaml", property = "fileName")
    private String outputFileName;

    /**
     * K8S is responsible for load balancing, default is false
     */
    @Parameter(defaultValue = "false", property = "autoLoadblance")
    private Boolean autoLoadblance;

    /**
     * Attribute profile prefix.
     */
    @Parameter(defaultValue = "application-", property = "prefixOfConfiguation")
    private String prefixOfConfiguation;

    /**
     * The current packaged environment, "daily", "qa", "perf", "pre", "prod"
     */
    @Parameter(defaultValue = "daily", property = "environment")
    private String environment;

    /**
     * Need to find service package names, multiple values can be separated by commas.
     */
    @Parameter(defaultValue = "com.youzan", property = "packageToScan")
    private String packageToScan;

    /**
     * application name key.
     */
    @Parameter(defaultValue = "application.name", property = "applicationNameKey")
    private String applicationNameKey;

    /**
     * application name key.
     */
    @Parameter(defaultValue = "", property = "applicationName")
    private String applicationName;

    /**
     * use properties file, eg: application.properties
     */
    @Parameter(defaultValue = "false", property = "usePropertyFile")
    private boolean usePropertyFile;

    @Parameter(defaultValue = "false", property = "skip")
    private boolean shouldSkip;

    /**
     * Specify module to force file descriptor generation, eg: *biz
     */
    @Parameter(defaultValue = "", property = "forceGenerate")
    private String forceGenerate;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    private String[] packageToScans;
    private Properties properties;
    private Properties globalProperties;
    private List<MetadataReader> foundAnnotation;

    private String[] annotationNames = {
            "com.youzan.api.rpc.annotation.Service",
            "com.alibaba.dubbo.config.annotation.Service",
            "com.youzan.api.rpc.annotation.ExtensionService"
    };

    private Map<String, Object> annotationDefaultValues;

    public void execute() throws MojoExecutionException {
        if (prepareConfiguation()) {
            findAnnotations();
            export();
        }
    }

    private boolean prepareConfiguation() throws MojoExecutionException {

        File f = searchDirectory;
        /**
         * Multi-level sub-modules are supported
         */
        if (f == null || !f.exists() || !f.isDirectory() || shouldSkip) {
            return false;
        }

        if (project.getModel() != null) {
            String name = project.getModel().getArtifactId();
            if (name != null && name.length() > 0 && forceGenerate != null && forceGenerate.length() > 1) {
                forceGenerate =
                        forceGenerate.indexOf("*") >= 0
                                ? forceGenerate.substring(forceGenerate.indexOf("*") + 1)
                                : forceGenerate;
            }

            /**
             * If the force module is specified,
             * it does not currently belong to this module and is ignored directly.
             */
            if (name != null && !name.endsWith(forceGenerate) && forceGenerate != null && forceGenerate.length() > 0) {
                /**
                 * Check if the current directory has files to delete
                 */
                removeIfNeed();
                return false;
            }
        }

        f = outputDirectory;
        if (f == null || !f.isDirectory()) {
            throw new MojoExecutionException("outputDirectory is required and should be directory, "
                    + "outputDirectory '" + (f == null ? "" : f.getAbsolutePath()) + "'");
        }

        Path path = Paths.get(f.getAbsolutePath(), outputFileName);

        this.properties = new Properties();
        this.globalProperties = new Properties();

        if (usePropertyFile) {
            if (Files.exists(path)) {
                getLog().warn("The file '" + outputFileName + "' already exists in path '" + path + "', the file will be overwritten.");
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed to delete file '" + path + "'", e);
                }
            }

            path = Paths.get(searchDirectory.getAbsolutePath(), prefixOfConfiguation + environment + ".properties");
            if (!Files.exists(path) && usePropertyFile) {
                throw new MojoExecutionException("Unable to find configuration  file '" + path + "'");
            }

            try {
                properties.load(Files.newBufferedReader(path));
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to load configuration  file '" + path + "'");
            }

            path = Paths.get(searchDirectory.getAbsolutePath(), "application.properties");
            try {
                if (Files.exists(path)) {
                    globalProperties.load(Files.newBufferedReader(path));
                }
            } catch (IOException ignored) {
            }
        }


        if (!StringUtils.hasText(packageToScan)) {
            throw new MojoExecutionException("packageToScan is required, packageToScan is '" + (packageToScan == null ? "" : packageToScan) + "'");
        }

        if (applicationName == null || applicationName.length() <= 0) {
            if (applicationNameKey != null &&
                    ((applicationName = getProperty(applicationNameKey) == null
                            ? getProperty(APP)
                            : getProperty(applicationNameKey)) == null)) {

                if (project != null) {
                    MavenProject parent = project;
                    while (parent != null) {
                        if (parent.getModel() != null
                                && parent.getModel().getPackaging() != null
                                && "pom".equals(parent.getModel().getPackaging())) {
                            applicationName = parent.getModel().getArtifactId();
                            getLog().warn("Detected project name '" + applicationName + "' from " + parent.getModel().getPomFile().toString());
                            break;
                        }
                        parent = parent.getParent();
                    }
                }

                if (applicationName == null || applicationName.length() <= 0) {
                    throw new MojoExecutionException("'" + applicationNameKey + "' or 'app' not found.");
                }
            }
        }

        this.packageToScans = packageToScan.split(",");
        this.foundAnnotation = new ArrayList<>();
        this.annotationDefaultValues = new HashMap<>();

        prepareAnnotationValues();

        return true;
    }

    private void findAnnotations() throws MojoExecutionException {
        findClassFiles(searchDirectory);
    }

    private void export() throws MojoExecutionException {
        writeToFile();
    }

    private void writeToFile() throws MojoExecutionException {
        YamlWriter writer = null;
        Path output = Paths.get(outputDirectory.getAbsolutePath(), outputFileName);
        Map pair = new LinkedHashMap();
        try {

            removeIfNeed();
            // do nothing
            if (foundAnnotation.isEmpty() && (forceGenerate == null || forceGenerate.isEmpty())) {
                return;
            }

            // create new file for annotation found.
            createIfNeed();

            getLog().info("Generating service information, file '" + output + "'.");
            writer = new YamlWriter(new FileWriter(Paths.get(outputDirectory.getAbsolutePath(), outputFileName).toFile()));
            writer.getConfig().writeConfig.setWriteRootTags(false);
            writer.getConfig().writeConfig.setWriteClassname(YamlConfig.WriteClassName.NEVER);
            List<String> unresolved = new ArrayList<>();

            pair.put("kind", "Service");
            pair.put("apiVersion", "v1");
            pair.put("metadata", new LinkedHashMap());
            pair.put("spec", new LinkedHashMap());

            Map<String, Boolean> cachePorts = new HashMap<>();

            List<Map<String, String>> annotationList = new ArrayList<Map<String, String>>();
            /** metadata configuration*/
            {
                Map metadata = (Map) pair.get("metadata");
                metadata.put("name", applicationName /*serviceName*/);
            }

            /** spec configuration*/
            Map spec = (Map) pair.get("spec");
            {
                spec.put("selector", new LinkedHashMap());
                spec.put("ports", new ArrayList());

                /** selector configuration*/
                {
                    Map selector = (Map) spec.get("selector");
                    selector.put("app", applicationName);
                }

                for (MetadataReader reader : foundAnnotation) {
                    for (String annotation : annotationNames) {
                        Map<String, Object> attributes = reader.getAnnotationMetadata().getAnnotationAttributes(annotation);
                        if (attributes != null) {

                            /** annotation configuration*/
                            {

                                Map<String, String> annotationMap = new LinkedHashMap<>();

                                annotationMap.put("interfaceClass", reader.getClassMetadata().getInterfaceNames()[0]);
                                appendAnnotations(annotationMap, attributes, annotation);

                                annotationList.add(annotationMap);
                            }

                            /** ports configuration*/
                            {
                                List ports = (List) spec.get("ports");

                                String[] protocols = (String[]) attributes.get("protocol");
                                for (String name : protocols) {

                                    if (!cachePorts.containsKey(name)) {
                                        Map port = new LinkedHashMap();
                                        port.put("protocol", "TCP");
                                        port.put("port", detectPort(name));
                                        ports.add(port);

                                        cachePorts.put(name, true);
                                    }

                                }
                            }

                            getLog().info("found service '" + serviceName(reader, attributes) + "'");
                        }
                    }
                }

                if (!autoLoadblance) {
                    spec.put("clusterIP", "None");
                }
            }

            {
                // 添加静态服务暴露
                appendUserDefindService(annotationList, spec, cachePorts);
            }

            {
                // convert annotations to json and compress
                Map metadata = (Map) pair.get("metadata");
                Map<String, String> annotations = new LinkedHashMap<>();
                // annotations.put("content-encoding", "snappy");
                String content = JSON.toJSONString(annotationList, SerializerFeature.QuoteFieldNames);

                if (content.length() > 255 * 1024) {
                    getLog().warn("Two many services found, max size: " + (255 * 1024) + " byte, current : " + content.length() + " byte.");
                }

                annotations.put("content", content);
                metadata.put("annotations", annotations);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to open file '" + output + "'", e);
        } finally {
            try {
                if (!foundAnnotation.isEmpty()
                        /**
                         * Force file descriptor generation based on user-specified module
                         */
                        || (forceGenerate != null && forceGenerate.length() > 0)) {
                    writer.write(pair);
                    writer.close();
                    getLog().info(foundAnnotation.size() + (foundAnnotation.size() > 1 ? " services" : " service")
                            + " generated successfully, file '" + output + "'");
                }
            } catch (YamlException e) {
                throw new MojoExecutionException("Failed to save file '" + output + "'", e);
            }
        }
    }

    private void appendUserDefindService(List<Map<String, String>> annotationList, Map spec, Map<String, Boolean> cachePorts) {
        Map<String, String> annotationMap = new LinkedHashMap<>();

        annotationMap.put("interfaceClass", "com.youzan.cloud.base.service.api.MessageService");
        annotationMap.put("extensionService", "true");
        annotationMap.put("protocol", "tether");
        annotationList.add(annotationMap);

        /** ports configuration*/
        {
            List ports = (List) spec.get("ports");

            if (!cachePorts.containsKey("tether")) {
                Map port = new LinkedHashMap();
                port.put("protocol", "TCP");
                port.put("port", detectPort("tether"));
                ports.add(port);

                cachePorts.put("tether", true);
            }
        }

        /**
         * Compatible with scenes without service exported
         */
        if (!autoLoadblance && spec.get("clusterIP") == null) {
            spec.put("clusterIP", "None");
        }
    }

    private String detectPort(String name) {
        String key = "application." + name + ".port";
        String port = null;

        if ((port = getProperty(key)) != null) {
            return port;
        }

        return name;
    }

    private void appendAnnotations(Map<String, String> annotations, Map<String, Object> attributes, String annotation) {

        if (annotation.equals("com.youzan.api.rpc.annotation.ExtensionService")) {
            annotations.put("extensionService", "true");
        }

        Iterator<Map.Entry<String, Object>> iterator = attributes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> attribute = iterator.next();
            if (shouldIgnore(attribute)) {
                continue;
            }
            String value = null;
            if (attribute.getValue() instanceof String[]) {
                value = toPlainString((String[]) attribute.getValue());
            } else {
                value = attribute.getValue().toString();
            }

            String key = attribute.getKey();

            key = key.equals("value") ? "group" : (key.equals("tag") ? "version" : key);

            if (key.equals("group")
                /*&& !annotation.equals("com.youzan.api.rpc.annotation.ExtensionService")*/) {

                if (!value.equals(annotationDefaultValues.get("group"))) {
                    annotations.put(key, value);
                }
                continue;
            }
            annotations.put(key, value);
        }
    }

    private boolean shouldIgnore(Map.Entry<String, Object> attribute) {
        Map<String, Object> defaults = annotationDefaultValues;
        if (attribute.getKey() != null) {
            String attrKey = attribute.getKey();
            Object attrValue = attribute.getValue();

            if (attrValue instanceof String) {
                String value = (String) attrValue;
                String defValue = (String) defaults.get(attrKey);

                if (value != null && defValue != null
                        // exclude value attribute
                        && (!attrKey.equals("value"))) {
                    if (value.equals(defValue)) {
                        return true;
                    }
                }

            } else if (attrValue instanceof Boolean) {
                boolean value = (boolean) attrValue;
                boolean defValue = (boolean) defaults.get(attrKey);
                return value == defValue;
            } else if (attrValue instanceof Integer) {
                int value = (int) attrValue;
                int defValue = (int) defaults.get(attrKey);
                return value == defValue;
            } else if (attrValue instanceof String[]) {
                String[] values = (String[]) attrValue;
                String[] defValues = (String[]) defaults.get(attrKey);
                if (values == null || values.length == 0) return true;
                if (values.length != defValues.length) return false;
                boolean same = true;
                for (int i = 0; i < values.length; i++) {
                    if (!defValues[i].equals(values[i])) {
                        same = false;
                        break;
                    }
                }
                return same;
            } else {
                if (attrKey.equals("interfaceClass")) {
                    return void.class.isAssignableFrom((Class<?>) attrValue)
                            || Void.class.isAssignableFrom((Class<?>) attrValue);
                }
            }
        }
        return false;
    }

    private String toPlainString(String[] array) {
        if (array == null || array.length == 0) return "";
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                buffer.append(",");
            }
            buffer.append(array[i]);
        }
        return buffer.toString();
    }

    private String serviceName(MetadataReader reader, Map<String, Object> attributes) {
        String[] interfaceNames = reader.getClassMetadata().getInterfaceNames();
        StringBuilder buffer = new StringBuilder();
        String group = (String) attributes.get("group");
        if (group != null && group.length() > 0) {
            buffer.append(group).append("/");
        }
        group = (String) attributes.get(VALUE_KEY);
        if (group != null && group.length() > 0) {
            buffer.append(group).append("/");
        }
        if (interfaceNames.length > 0) {
            buffer.append(interfaceNames[0]);
        }
        String version = (String) attributes.get("version");
        if (version != null && version.length() > 0) {
            buffer.append(":").append(version);
        }
        version = (String) attributes.get(TAG_KEY);
        if (version != null && version.length() > 0) {
            buffer.append(":").append(version);
        }
        return buffer.toString();
    }

    private void removeIfNeed() throws MojoExecutionException {
        Path path = Paths.get(outputDirectory.getAbsolutePath(), outputFileName);
        if (Files.exists(path)) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to delete file '" + path + "'", e);
            }
        }
    }

    private void createIfNeed() throws MojoExecutionException {
        Path path = Paths.get(outputDirectory.getAbsolutePath(), outputFileName);
        try {
            Files.createFile(path);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create new file '" + path + "'", e);
        }
    }


    private void findClassFiles(File search) throws MojoExecutionException {
        try {
            File[] files = search.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    findClassFiles(file);
                } else if (file.isFile()) {
                    if (file.toPath().toString().endsWith(".class")) {
                        SimpleMetadataReader reader = new SimpleMetadataReader(file, DubboServiceVisitor.class.getClassLoader());
                        if (shouldInclude(reader)) {
                            prepareAttributes(reader);
                            foundAnnotation.add(reader);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to parsing annotation.", e);
        }
    }

    private boolean shouldInclude(SimpleMetadataReader reader) {

        boolean packageMatched = false, annotationMatched = false;
        String className = reader.getClassMetadata().getClassName();

        for (String scanPackage : packageToScans) {
            if (className.startsWith(scanPackage)) {
                packageMatched = true;
                break;
            }
        }

        for (String annotation : annotationNames) {
            if (reader.getAnnotationMetadata().isAnnotated(annotation)) {
                annotationMatched = true;
                break;
            }
        }

        return (packageMatched && annotationMatched);
    }

    private void prepareAttributes(SimpleMetadataReader reader) {
        for (String annotation : annotationNames) {
            if (reader.getAnnotationMetadata().isAnnotated(annotation)) {
                Map<String, Object> attributes = reader.getAnnotationMetadata().getAnnotationAttributes(annotation);
                Iterator<Map.Entry<String, Object>> attribute = attributes.entrySet().iterator();
                while (attribute.hasNext()) {
                    Map.Entry<String, Object> attr = attribute.next();
                    if (attr.getValue() != null) {
                        Object value = attr.getValue();
                        if (value instanceof String) {
                            String unsolved = (String) value;
                            if (unsolved == null || unsolved.length() == 0) continue;
                            if (!unsolved.startsWith("${")) continue;
                            if (unsolved.startsWith("${") && unsolved.endsWith("}")) {
                                attr.setValue(getProperty(unsolved.substring(2, unsolved.length() - 1)));
                            }
                        } else if (value instanceof String[]) {
                            String[] unsolvedValus = (String[]) value;
                            if (unsolvedValus == null || unsolvedValus.length == 0) continue;
                            for (int i = 0; i < unsolvedValus.length; i++) {
                                if (!unsolvedValus[i].startsWith("${")) continue;
                                if (unsolvedValus[i].startsWith("${") && unsolvedValus[i].endsWith("}")) {
                                    unsolvedValus[i] = getProperty(unsolvedValus[i].substring(2, unsolvedValus[i].length() - 1));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    <T> T getProperty(String key) {
        Object value = System.getProperty(key);
        if (value != null) return (T) value;

        value = properties.get(key);
        if (value != null) {
            return (T) value;
        }
        value = globalProperties.getProperty(key);
        if (value != null) {
            return (T) value;
        }

        return null;
    }

    private void prepareAnnotationValues() {
        annotationDefaultValues.put("interfaceClass", void.class);
        annotationDefaultValues.put("interfaceName", "");
        annotationDefaultValues.put("version", "");
        annotationDefaultValues.put("tag", "");
        annotationDefaultValues.put("group", "");
        annotationDefaultValues.put("value", "");
        annotationDefaultValues.put("path", "");
        annotationDefaultValues.put("export", false);
        annotationDefaultValues.put("token", "");
        annotationDefaultValues.put("deprecated", false);
        annotationDefaultValues.put("dynamic", false);
        annotationDefaultValues.put("accesslog", "");
        annotationDefaultValues.put("executes", 0);
        annotationDefaultValues.put("register", false);
        annotationDefaultValues.put("weight", 0);
        annotationDefaultValues.put("document", "");
        annotationDefaultValues.put("delay", 0);
        annotationDefaultValues.put("local", "");
        annotationDefaultValues.put("stub", "");
        annotationDefaultValues.put("cluster", "");
        annotationDefaultValues.put("proxy", "");
        annotationDefaultValues.put("connections", 0);
        annotationDefaultValues.put("callbacks", 0);
        annotationDefaultValues.put("onconnect", "");
        annotationDefaultValues.put("ondisconnect", "");
        annotationDefaultValues.put("owner", "");
        annotationDefaultValues.put("layer", "");
        annotationDefaultValues.put("retries", 0);
        annotationDefaultValues.put("loadbalance", "");
        annotationDefaultValues.put("async", false);
        annotationDefaultValues.put("actives", 0);
        annotationDefaultValues.put("sent", false);
        annotationDefaultValues.put("mock", "");
        annotationDefaultValues.put("validation", "");
        annotationDefaultValues.put("timeout", 0);
        annotationDefaultValues.put("cache", "");
        annotationDefaultValues.put("filter", new String[0]);
        annotationDefaultValues.put("listener", new String[0]);
        annotationDefaultValues.put("parameters", new String[0]);
        annotationDefaultValues.put("application", "");
        annotationDefaultValues.put("module", "");
        annotationDefaultValues.put("provider", "");
        annotationDefaultValues.put("protocol", new String[0]);
        annotationDefaultValues.put("monitor", "");
        annotationDefaultValues.put("registry", new String[0]);
    }

    public static final String TAG_KEY = "tag";

    public static final String VALUE_KEY = "value";

    public static final String APP = "app";
}
