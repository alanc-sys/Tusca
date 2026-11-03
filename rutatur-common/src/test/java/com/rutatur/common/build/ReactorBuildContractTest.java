package com.rutatur.common.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Guards the reactor contract that every later slice depends on: JDK 21 pins, the pinned
 * Spring Boot / Spring Cloud platform, deterministic module order and executable packaging.
 *
 * <p>Uses only the JDK XML parser so it runs before any shared dependency is on the classpath.
 */
class ReactorBuildContractTest {

    private static final Path ROOT = projectRoot();
    private static final List<String> EXECUTABLE_MODULES =
            List.of("poi-service", "itinerary-service", "route-match-service", "api-gateway");

    @Test
    void pinsJava21AsTheCompilerRelease() throws Exception {
        Document root = parse(ROOT.resolve("pom.xml"));

        assertThat(property(root, "java.version")).isEqualTo("21");
        assertThat(property(root, "maven.compiler.release")).isEqualTo("21");
        assertThat(property(root, "project.build.sourceEncoding")).isEqualTo("UTF-8");
    }

    @Test
    void declaresModulesInDependencyOrder() throws Exception {
        Document root = parse(ROOT.resolve("pom.xml"));

        assertThat(moduleNames(root))
                .containsExactly(
                        "rutatur-common",
                        "poi-service",
                        "itinerary-service",
                        "route-match-service",
                        "api-gateway");
    }

    @Test
    void pinsSpringBootAndSpringCloudPlatformVersions() throws Exception {
        Document root = parse(ROOT.resolve("pom.xml"));

        Element parent = child(root.getDocumentElement(), "parent");
        assertThat(childText(parent, "groupId")).isEqualTo("org.springframework.boot");
        assertThat(childText(parent, "artifactId")).isEqualTo("spring-boot-starter-parent");
        assertThat(childText(parent, "version")).isEqualTo("3.3.5");

        assertThat(property(root, "spring-cloud.version")).isEqualTo("2023.0.5");

        Element cloudBom = dependencyManagementEntry(root, "spring-cloud-dependencies");
        assertThat(cloudBom).as("spring-cloud-dependencies BOM import").isNotNull();
        assertThat(childText(cloudBom, "groupId")).isEqualTo("org.springframework.cloud");
        assertThat(childText(cloudBom, "version")).isEqualTo("${spring-cloud.version}");
        assertThat(childText(cloudBom, "type")).isEqualTo("pom");
        assertThat(childText(cloudBom, "scope")).isEqualTo("import");
    }

    @Test
    void marksServiceModulesAsExecutableBootJars() throws Exception {
        for (String module : EXECUTABLE_MODULES) {
            Document pom = parse(ROOT.resolve(module).resolve("pom.xml"));
            Element build = child(pom.getDocumentElement(), "build");

            assertThat(childText(build, "finalName")).as("%s finalName", module).isEqualTo("app");
            assertThat(pluginArtifactIds(build))
                    .as("%s repackage plugin", module)
                    .contains("spring-boot-maven-plugin");
            assertThat(dependencyArtifactIds(pom))
                    .as("%s depends on the shared contract jar", module)
                    .contains("rutatur-common");
            assertThat(dependencyVersion(pom, "rutatur-common"))
                    .as("%s must inherit the rutatur-common version", module)
                    .isNull();
        }
    }

    @Test
    void keepsSharedContractJarPlainAndWebNeutral() throws Exception {
        Document common = parse(ROOT.resolve("rutatur-common").resolve("pom.xml"));

        assertThat(childText(common.getDocumentElement(), "packaging")).isEqualTo("jar");

        Element build = child(common.getDocumentElement(), "build");
        assertThat(pluginArtifactIds(build))
                .as("rutatur-common must not be repackaged as a Boot jar")
                .doesNotContain("spring-boot-maven-plugin");

        assertThat(dependencyArtifactIds(common))
                .as("rutatur-common must stay servlet/reactive neutral")
                .doesNotContain(
                        "spring-boot-starter-web",
                        "spring-boot-starter-webflux",
                        "spring-boot-starter-tomcat",
                        "spring-cloud-starter-gateway");
    }

    private static Path projectRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("rutatur-common"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException(
                    "Cannot locate reactor root from " + System.getProperty("user.dir"));
        }
        return dir;
    }

    private static Document parse(Path pom) throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try (var stream = Files.newInputStream(pom)) {
            return factory.newDocumentBuilder().parse(stream);
        }
    }

    private static List<String> moduleNames(Document pom) {
        List<String> names = new ArrayList<>();
        for (Element module : children(child(pom.getDocumentElement(), "modules"), "module")) {
            names.add(module.getTextContent().trim());
        }
        return names;
    }

    private static String property(Document pom, String name) {
        Element properties = child(pom.getDocumentElement(), "properties");
        return properties == null ? null : childText(properties, name);
    }

    private static Element dependencyManagementEntry(Document pom, String artifactId) {
        Element management = child(pom.getDocumentElement(), "dependencyManagement");
        if (management == null) {
            return null;
        }
        for (Element dependency : children(child(management, "dependencies"), "dependency")) {
            if (artifactId.equals(childText(dependency, "artifactId"))) {
                return dependency;
            }
        }
        return null;
    }

    private static List<String> dependencyArtifactIds(Document pom) {
        List<String> ids = new ArrayList<>();
        for (Element dependencies : children(pom.getDocumentElement(), "dependencies")) {
            for (Element dependency : children(dependencies, "dependency")) {
                ids.add(childText(dependency, "artifactId"));
            }
        }
        return ids;
    }

    private static String dependencyVersion(Document pom, String artifactId) {
        for (Element dependencies : children(pom.getDocumentElement(), "dependencies")) {
            for (Element dependency : children(dependencies, "dependency")) {
                if (artifactId.equals(childText(dependency, "artifactId"))) {
                    return childText(dependency, "version");
                }
            }
        }
        return null;
    }

    private static List<String> pluginArtifactIds(Element build) {
        List<String> ids = new ArrayList<>();
        if (build == null) {
            return ids;
        }
        for (Element plugins : children(build, "plugins")) {
            for (Element plugin : children(plugins, "plugin")) {
                ids.add(childText(plugin, "artifactId"));
            }
        }
        return ids;
    }

    private static Element child(Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE && localName.equals(node.getLocalName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> matches = new ArrayList<>();
        if (parent == null) {
            return matches;
        }
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && localName.equals(node.getLocalName())) {
                matches.add((Element) node);
            }
        }
        return matches;
    }

    private static String childText(Element parent, String localName) {
        Element element = child(parent, localName);
        return element == null ? null : element.getTextContent().trim();
    }
}
