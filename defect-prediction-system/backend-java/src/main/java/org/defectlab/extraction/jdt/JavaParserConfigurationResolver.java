package org.metrics.jdt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Resolves historical Java settings from Eclipse, Maven, and Ant metadata.
 *
 * <p>Resolution priority is nearest Eclipse JDT preferences, effective Maven
 * settings, Ant settings, an explicit benchmark fallback, and finally
 * syntax-error detection.</p>
 */
public final class JavaParserConfigurationResolver {

    private static final int MAX_DETECTION_FILES = 64;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");
    private static final List<String> SOURCE_CANDIDATES = Collections.unmodifiableList(
            Arrays.asList("1.3", "1.4", "1.5", "1.6", "1.7", "1.8",
                    "9", "11", "17", "21"));

    private JavaParserConfigurationResolver() {
    }

    public static ResolvedJavaProject resolve(
            Path projectRoot,
            List<Path> javaFiles,
            JavaLanguageConfiguration explicitFallback) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>();
        for (Path file : javaFiles) {
            Path normalized = file.toAbsolutePath().normalize();
            if (normalized.startsWith(root) && Files.isRegularFile(normalized)) {
                files.add(normalized);
            }
        }
        Collections.sort(files);

        List<String> diagnostics = new ArrayList<>();
        JavaLanguageConfiguration detected = explicitFallback == null
                ? detectConfiguration(files)
                : explicitFallback;
        Map<Path, Metadata> metadataCache = new LinkedHashMap<>();
        Map<JavaLanguageConfiguration, List<Path>> grouped = new LinkedHashMap<>();
        Map<Path, JavaLanguageConfiguration> byFile = new LinkedHashMap<>();

        for (Path file : files) {
            Path module = metadataDirectory(file.getParent(), root);
            Metadata metadata = metadataCache.get(module);
            if (metadata == null) {
                metadata = resolveMetadata(module, root, diagnostics);
                metadataCache.put(module, metadata);
            }
            JavaLanguageConfiguration configuration =
                    metadata.toConfiguration(detected);
            if (StandardCharsets.UTF_8.equals(configuration.getCharset())
                    && !isValidUtf8(file)) {
                configuration = configuration.withCharset(StandardCharsets.ISO_8859_1);
                diagnostics.add("Using ISO-8859-1 for non-UTF-8 source " + file + ".");
            }
            byFile.put(file, configuration);
            grouped.computeIfAbsent(configuration, ignored -> new ArrayList<>())
                    .add(file);
        }
        return new ResolvedJavaProject(grouped, byFile, diagnostics);
    }

    private static Path metadataDirectory(Path start, Path root) {
        Path current = start;
        while (current != null && current.startsWith(root)) {
            if (Files.isRegularFile(current.resolve(
                    ".settings/org.eclipse.jdt.core.prefs"))
                    || Files.isRegularFile(current.resolve("pom.xml"))
                    || Files.isRegularFile(current.resolve("build.xml"))
                    || Files.isRegularFile(current.resolve("common-build.xml"))) {
                return current;
            }
            if (current.equals(root)) {
                break;
            }
            current = current.getParent();
        }
        return root;
    }

    private static Metadata resolveMetadata(
            Path module,
            Path root,
            List<String> diagnostics) {
        Metadata eclipse = eclipseMetadata(module, root, diagnostics);
        if (eclipse.hasLanguageLevel()) {
            return eclipse;
        }

        Metadata maven = mavenMetadata(module, root, diagnostics);
        if (maven.hasLanguageLevel()) {
            return maven;
        }

        Metadata ant = antMetadata(module, root, diagnostics);
        if (ant.hasLanguageLevel()) {
            return ant;
        }
        return Metadata.empty();
    }

    private static Metadata eclipseMetadata(
            Path module,
            Path root,
            List<String> diagnostics) {
        for (Path directory : ancestors(module, root)) {
            Path prefs = directory.resolve(".settings/org.eclipse.jdt.core.prefs");
            if (!Files.isRegularFile(prefs)) {
                continue;
            }
            Properties values = loadProperties(prefs, diagnostics);
            String source = values.getProperty(
                    "org.eclipse.jdt.core.compiler.source");
            String compliance = values.getProperty(
                    "org.eclipse.jdt.core.compiler.compliance");
            String target = values.getProperty(
                    "org.eclipse.jdt.core.compiler.codegen.targetPlatform");
            if (source != null || compliance != null || target != null) {
                return new Metadata(source, compliance, target, null,
                        "Eclipse prefs " + relative(root, prefs));
            }
        }
        return Metadata.empty();
    }

    private static Metadata mavenMetadata(
            Path module,
            Path root,
            List<String> diagnostics) {
        List<Path> poms = new ArrayList<>();
        for (Path directory : ancestors(module, root)) {
            Path pom = directory.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                poms.add(pom);
            }
        }
        Collections.reverse(poms);
        if (poms.isEmpty()) {
            return Metadata.empty();
        }

        Map<String, String> properties = new LinkedHashMap<>();
        String source = null;
        String target = null;
        String release = null;
        String encoding = null;
        Path origin = null;
        for (Path pom : poms) {
            Document document = parseXml(pom, diagnostics);
            if (document == null) {
                continue;
            }
            Element project = document.getDocumentElement();
            Element propertiesElement = directChild(project, "properties");
            if (propertiesElement != null) {
                for (Element child : directChildren(propertiesElement)) {
                    properties.put(localName(child), text(child));
                }
            }
            resolveAll(properties);

            String propertyRelease = firstNonBlank(
                    properties.get("maven.compiler.release"),
                    properties.get("maven.compiler.testRelease"));
            String propertySource = firstNonBlank(
                    properties.get("maven.compiler.source"),
                    properties.get("maven.compiler.testSource"));
            String propertyTarget = firstNonBlank(
                    properties.get("maven.compiler.target"),
                    properties.get("maven.compiler.testTarget"));
            String propertyEncoding = firstNonBlank(
                    properties.get("project.build.sourceEncoding"),
                    properties.get("maven.compiler.encoding"));
            if (propertyRelease != null) {
                release = resolve(propertyRelease, properties);
                origin = pom;
            }
            if (propertySource != null) {
                source = resolve(propertySource, properties);
                origin = pom;
            }
            if (propertyTarget != null) {
                target = resolve(propertyTarget, properties);
                origin = pom;
            }
            if (propertyEncoding != null) {
                encoding = resolve(propertyEncoding, properties);
            }

            NodeList plugins = document.getElementsByTagNameNS("*", "plugin");
            for (int index = 0; index < plugins.getLength(); index++) {
                Element plugin = (Element) plugins.item(index);
                if (!"maven-compiler-plugin".equals(
                        directChildText(plugin, "artifactId"))) {
                    continue;
                }
                Element configuration = directChild(plugin, "configuration");
                if (configuration == null) {
                    continue;
                }
                String configuredRelease = directChildText(
                        configuration, "release");
                String configuredSource = directChildText(
                        configuration, "source");
                String configuredTarget = directChildText(
                        configuration, "target");
                String configuredEncoding = directChildText(
                        configuration, "encoding");
                if (configuredRelease != null) {
                    release = resolve(configuredRelease, properties);
                    origin = pom;
                }
                if (configuredSource != null) {
                    source = resolve(configuredSource, properties);
                    origin = pom;
                }
                if (configuredTarget != null) {
                    target = resolve(configuredTarget, properties);
                    origin = pom;
                }
                if (configuredEncoding != null) {
                    encoding = resolve(configuredEncoding, properties);
                }
            }
        }
        if (release != null) {
            source = release;
            target = release;
        }
        return new Metadata(source, source, target, encoding,
                origin == null ? "Maven metadata"
                        : "Maven " + relative(root, origin));
    }

    private static Metadata antMetadata(
            Path module,
            Path root,
            List<String> diagnostics) {
        Set<Path> buildFiles = new LinkedHashSet<>();
        Set<Path> propertyFiles = new LinkedHashSet<>();
        for (Path directory : ancestors(module, root)) {
            addIfFile(buildFiles, directory.resolve("build.xml"));
            addIfFile(buildFiles, directory.resolve("common-build.xml"));
            addIfFile(propertyFiles, directory.resolve("build.properties"));
            addIfFile(propertyFiles, directory.resolve("project.properties"));
        }
        addIfFile(buildFiles, root.resolve("build/build.xml"));
        addIfFile(propertyFiles, root.resolve("build/build.properties"));
        if (buildFiles.isEmpty() && propertyFiles.isEmpty()) {
            return Metadata.empty();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (Path propertyFile : propertyFiles) {
            Properties loaded = loadProperties(propertyFile, diagnostics);
            for (String name : loaded.stringPropertyNames()) {
                values.putIfAbsent(name, loaded.getProperty(name));
            }
        }

        String directSource = null;
        String directTarget = null;
        String directEncoding = null;
        Path origin = null;
        for (Path buildFile : buildFiles) {
            Document document = parseXml(buildFile, diagnostics);
            if (document == null) {
                continue;
            }
            NodeList propertyNodes = document.getElementsByTagNameNS("*", "property");
            for (int index = 0; index < propertyNodes.getLength(); index++) {
                Element property = (Element) propertyNodes.item(index);
                String name = attribute(property, "name");
                String value = firstNonBlank(
                        attribute(property, "value"),
                        attribute(property, "location"));
                if (name != null && value != null) {
                    values.putIfAbsent(name, value);
                }
            }
            resolveAll(values);
            NodeList javacNodes = document.getElementsByTagNameNS("*", "javac");
            for (int index = 0; index < javacNodes.getLength(); index++) {
                Element javac = (Element) javacNodes.item(index);
                directSource = firstNonBlank(
                        directSource, attribute(javac, "source"));
                directTarget = firstNonBlank(
                        directTarget, attribute(javac, "target"));
                directEncoding = firstNonBlank(
                        directEncoding, attribute(javac, "encoding"));
            }
            origin = buildFile;
        }
        resolveAll(values);
        String source = firstNonBlank(
                directSource,
                value(values, "javac.source", "compiler.source",
                        "java.source", "source.version", "target.java.version"));
        String target = firstNonBlank(
                directTarget,
                value(values, "javac.target", "compiler.target",
                        "java.target", "target.version", "target.java.version"));
        String encoding = firstNonBlank(
                directEncoding,
                value(values, "javac.encoding", "compiler.encoding",
                        "source.encoding", "encoding"));
        return new Metadata(
                resolve(source, values),
                resolve(source, values),
                resolve(target, values),
                resolve(encoding, values),
                origin == null ? "Ant metadata"
                        : "Ant " + relative(root, origin));
    }

    private static JavaLanguageConfiguration detectConfiguration(
            List<Path> files) throws IOException {
        if (files.isEmpty()) {
            return JavaLanguageConfiguration.uniform("17", "empty-project fallback");
        }
        List<String> sources = detectionSources(files);
        String selected = "17";
        int bestErrors = Integer.MAX_VALUE;
        for (String candidate : SOURCE_CANDIDATES) {
            int errors = countSyntaxErrors(sources, candidate, bestErrors);
            if (errors < bestErrors) {
                bestErrors = errors;
                selected = candidate;
            }
        }
        return JavaLanguageConfiguration.uniform(
                selected,
                "syntax fallback (" + bestErrors + " parser errors)");
    }

    private static List<String> detectionSources(List<Path> files) throws IOException {
        int count = Math.min(MAX_DETECTION_FILES, files.size());
        List<String> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int fileIndex = count == 1 ? 0
                    : (int) Math.round(index * (files.size() - 1d) / (count - 1d));
            Path file = files.get(fileIndex);
            byte[] bytes = Files.readAllBytes(file);
            Charset charset = isValidUtf8(bytes)
                    ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1;
            result.add(new String(bytes, charset));
        }
        return result;
    }

    private static int countSyntaxErrors(
            List<String> sources,
            String candidate,
            int currentBest) {
        int errors = 0;
        JavaLanguageConfiguration configuration =
                JavaLanguageConfiguration.uniform(candidate, "detection");
        for (String source : sources) {
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setResolveBindings(false);
            parser.setBindingsRecovery(false);
            parser.setStatementsRecovery(true);
            parser.setCompilerOptions(configuration.compilerOptions());
            parser.setSource(source.toCharArray());
            CompilationUnit unit = (CompilationUnit) parser.createAST(null);
            for (IProblem problem : unit.getProblems()) {
                if (problem.isError()) {
                    errors++;
                    if (errors > currentBest) {
                        return errors;
                    }
                }
            }
        }
        return errors;
    }

    private static List<Path> ancestors(Path module, Path root) {
        List<Path> result = new ArrayList<>();
        Path current = module.toAbsolutePath().normalize();
        while (current != null && current.startsWith(root)) {
            result.add(current);
            if (current.equals(root)) {
                break;
            }
            current = current.getParent();
        }
        return result;
    }

    private static Properties loadProperties(
            Path path,
            List<String> diagnostics) {
        Properties result = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            result.load(input);
        } catch (IOException exception) {
            diagnostics.add("Could not read " + path + ": " + exception.getMessage());
        }
        return result;
    }

    private static Document parseXml(
            Path path,
            List<String> diagnostics) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(path.toFile());
        } catch (Exception exception) {
            diagnostics.add("Could not read build metadata " + path
                    + ": " + exception.getMessage());
            return null;
        }
    }

    private static Element directChild(Element parent, String name) {
        for (Element child : directChildren(parent)) {
            if (name.equals(localName(child))) {
                return child;
            }
        }
        return null;
    }

    private static String directChildText(Element parent, String name) {
        Element child = directChild(parent, name);
        return child == null ? null : text(child);
    }

    private static List<Element> directChildren(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element) {
                result.add((Element) child);
            }
        }
        return result;
    }

    private static String localName(Element element) {
        return element.getLocalName() == null
                ? element.getTagName() : element.getLocalName();
    }

    private static String text(Element element) {
        return trimToNull(element.getTextContent());
    }

    private static String attribute(Element element, String name) {
        return trimToNull(element.getAttribute(name));
    }

    private static String value(Map<String, String> values, String... names) {
        for (String name : names) {
            String value = values.get(name);
            if (trimToNull(value) != null) {
                return value;
            }
        }
        return null;
    }

    private static void resolveAll(Map<String, String> values) {
        for (int iteration = 0; iteration < 10; iteration++) {
            boolean changed = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String resolved = resolve(entry.getValue(), values);
                if (resolved != null && !resolved.equals(entry.getValue())) {
                    entry.setValue(resolved);
                    changed = true;
                }
            }
            if (!changed) {
                return;
            }
        }
    }

    private static String resolve(String value, Map<String, String> properties) {
        String result = trimToNull(value);
        if (result == null) {
            return null;
        }
        for (int iteration = 0; iteration < 10; iteration++) {
            Matcher matcher = PLACEHOLDER.matcher(result);
            StringBuffer buffer = new StringBuffer();
            boolean changed = false;
            while (matcher.find()) {
                String replacement = properties.get(matcher.group(1));
                if (replacement == null) {
                    continue;
                }
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
                changed = true;
            }
            matcher.appendTail(buffer);
            if (!changed) {
                break;
            }
            result = buffer.toString();
        }
        return result.contains("${") ? null : trimToNull(result);
    }

    private static String normalizeVersion(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT)
                .replace("jdk", "")
                .replace("_", ".")
                .trim();
        Matcher matcher = Pattern.compile("(1\\.[1-9]|[5-9]|1[0-9]|2[0-9])")
                .matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        String version = matcher.group(1);
        if (version.length() == 1 && Integer.parseInt(version) <= 8) {
            return "1." + version;
        }
        return version;
    }

    private static Charset charset(String value, Charset fallback) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return fallback;
        }
        try {
            return Charset.forName(normalized);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean isValidUtf8(Path file) throws IOException {
        return isValidUtf8(Files.readAllBytes(file));
    }

    private static boolean isValidUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private static void addIfFile(Set<Path> target, Path path) {
        if (Files.isRegularFile(path)) {
            target.add(path);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String relative(Path root, Path file) {
        try {
            return root.relativize(file.toAbsolutePath().normalize())
                    .toString().replace('\\', '/');
        } catch (IllegalArgumentException exception) {
            return file.toString();
        }
    }

    private static final class Metadata {
        private final String source;
        private final String compliance;
        private final String target;
        private final String encoding;
        private final String origin;

        private Metadata(
                String source,
                String compliance,
                String target,
                String encoding,
                String origin) {
            this.source = normalizeVersion(source);
            this.compliance = normalizeVersion(compliance);
            this.target = normalizeVersion(target);
            this.encoding = trimToNull(encoding);
            this.origin = origin;
        }

        private static Metadata empty() {
            return new Metadata(null, null, null, null, "fallback");
        }

        private boolean hasLanguageLevel() {
            return source != null || compliance != null || target != null;
        }

        private JavaLanguageConfiguration toConfiguration(
                JavaLanguageConfiguration fallback) {
            String resolvedSource = firstNonBlank(source, compliance,
                    fallback.getSource());
            String resolvedCompliance = firstNonBlank(compliance, source,
                    fallback.getCompliance(), resolvedSource);
            String resolvedTarget = firstNonBlank(target,
                    fallback.getTarget(), resolvedSource);

            // JDT 3.37 parses historical 1.1/1.2 syntax at its oldest
            // supported source level while still accepting their target value.
            if ("1.1".equals(resolvedSource) || "1.2".equals(resolvedSource)) {
                resolvedSource = "1.3";
            }
            if ("1.1".equals(resolvedCompliance)
                    || "1.2".equals(resolvedCompliance)) {
                resolvedCompliance = "1.3";
            }
            return new JavaLanguageConfiguration(
                    resolvedSource,
                    resolvedCompliance,
                    resolvedTarget,
                    charset(encoding, fallback.getCharset()),
                    hasLanguageLevel() ? origin : fallback.getOrigin());
        }
    }
}
