package me.pfzh.hibernatelite.arch;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Static dependency analyzer that produces PlantUML diagrams for Javadoc pages.
 *
 * <p>Dependencies are collected from the following sources:</p>
 * <ol>
 *   <li>{@code extends} / {@code implements}</li>
 *   <li>Method parameter and return types (including generic type arguments)</li>
 *   <li>Object instantiations inside method bodies ({@code new X()})</li>
 *   <li>Method invocations on a resolved receiver ({@code x.method()})</li>
 *   <li>Local variable declarations inside method bodies</li>
 * </ol>
 *
 * <p>The following are explicitly ignored:</p>
 * <ul>
 *   <li>Field declarations by themselves (unless the field is actually used)</li>
 *   <li>JDK and third-party types</li>
 *   <li>{@code import} statements</li>
 * </ul>
 *
 * <p>Three kinds of diagrams are generated:</p>
 * <ul>
 *   <li>Class page:    {@code <ClassName>-dependencies.svg} — current class with
 *                     fields/methods, dependencies shown as name-only nodes.</li>
 *   <li>Package page:  {@code <shortName>-classes.svg} — package-local classes
 *                     and intra-package dependencies.</li>
 *   <li>Overview page: {@code overview-dependencies.svg} — package nodes and
 *                     inter-package dependencies.</li>
 * </ul>
 *
 * @author Pengfei Zhang
 * @since 2026/9/21
 */
class DependencyAnalyzer {

    private final Path sourceDir;
    private final Path outputDir;
    private final String basePackage;

    /** FQN -> class information */
    private final Map<String, ClassInfo> classInfos = new LinkedHashMap<>();
    /** Package name -> set of FQNs in this package */
    private final Map<String, Set<String>> packageClasses = new LinkedHashMap<>();
    /** FQN -> set of dependency FQNs */
    private final Map<String, Set<String>> classDependencies = new LinkedHashMap<>();
    /** All FQNs belonging to this project */
    private final Set<String> projectClasses = new HashSet<>();

    /**
     * Packages excluded from the overview diagram (typically parent packages
     * that would otherwise appear as enclosing boxes of their subpackages).
     */
    private static final Set<String> OVERVIEW_EXCLUDED_PACKAGES = Set.of(
            "me.pfzh.hibernatelite"
    );

    DependencyAnalyzer(Path sourceDir, Path outputDir, String basePackage) {
        this.sourceDir = sourceDir;
        this.outputDir = outputDir;
        this.basePackage = basePackage;
    }

    // ==================== Main flow ====================

    void analyzeAndGenerate() throws IOException {
        configureParser();

        List<Path> javaFiles = collectJavaFiles();
        if (javaFiles.isEmpty()) {
            System.out.println("[analysis] No Java source files found.");
            return;
        }
        System.out.println("[analysis] Found " + javaFiles.size() + " Java source files.");

        collectProjectClasses(javaFiles);
        System.out.println("[analysis] Project-defined types: " + projectClasses.size());

        for (Path f : javaFiles) {
            parseFile(f);
        }

        generatePerClassDiagrams();
        generatePerPackageClassDiagrams();
        generateOverviewDiagram();
    }

    // ==================== Parser configuration ====================

    private void configureParser() {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(),
                new JavaParserTypeSolver(sourceDir)
        );
        StaticJavaParser.getConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));
    }

    private List<Path> collectJavaFiles() throws IOException {
        try (Stream<Path> s = Files.walk(sourceDir)) {
            return s.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !isAnalysisFile(p))
                    .collect(Collectors.toList());
        }
    }

    /** Excludes source files belonging to this analysis tool itself. */
    private boolean isAnalysisFile(Path p) {
        String path = p.toString().replace('\\', '/');
        String analysisPath = basePackage.replace('.', '/') + "/arch/";
        return path.contains(analysisPath);
    }

    private void collectProjectClasses(List<Path> javaFiles) {
        for (Path f : javaFiles) {
            try {
                CompilationUnit cu = StaticJavaParser.parse(f);
                cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz ->
                        clazz.getFullyQualifiedName().ifPresent(projectClasses::add));
            } catch (Exception ignored) {
                // Skip files that cannot be parsed during the first pass.
            }
        }
    }

    // ==================== Parse a single file ====================

    private void parseFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                String fqn = clazz.getFullyQualifiedName().orElse(clazz.getNameAsString());
                String pkg = clazz.findCompilationUnit()
                        .flatMap(CompilationUnit::getPackageDeclaration)
                        .map(pd -> pd.getNameAsString())
                        .orElse("(default)");

                // Skip this analysis tool itself.
                if (pkg.equals(basePackage + ".arch")) return;

                ClassInfo info = classInfos.computeIfAbsent(fqn, k -> new ClassInfo());
                info.packageName = pkg;
                info.isInterface = clazz.isInterface();

                Set<String> deps = classDependencies.computeIfAbsent(fqn,
                        k -> new LinkedHashSet<>());
                packageClasses.computeIfAbsent(pkg, k -> new LinkedHashSet<>()).add(fqn);

                // (1) extends / implements
                clazz.getExtendedTypes().forEach(t -> resolveType(t).ifPresent(p -> {
                    if (!p.equals(fqn)) deps.add(p);
                }));
                clazz.getImplementedTypes().forEach(t -> resolveType(t).ifPresent(p -> {
                    if (!p.equals(fqn)) deps.add(p);
                }));

                // Fields are rendered but not treated as dependencies.
                clazz.getFields().forEach(field -> {
                    String vis = getVisibility(field);
                    String typeName = field.getElementType().asString();
                    String fieldName = field.getVariable(0).getNameAsString();
                    info.fields.add(vis + " " + typeName + " " + fieldName);
                });

                // Methods
                clazz.getMethods().forEach(method -> {
                    String vis = getVisibility(method);
                    String ret = method.getType().asString();
                    String params = method.getParameters().stream()
                            .map(p -> p.getType() + " " + p.getName())
                            .collect(Collectors.joining(", "));
                    info.methods.add(vis + " " + method.getName()
                            + "(" + params + ") : " + ret);

                    // (2) Return and parameter types
                    collectTypeAndGenerics(method.getType(), fqn, deps);
                    method.getParameters().forEach(p ->
                            collectTypeAndGenerics(p.getType(), fqn, deps));

                    // (3)(4)(5) Method body
                    method.getBody().ifPresent(body -> {
                        // (3) new X()
                        body.findAll(ObjectCreationExpr.class).forEach(e ->
                                resolveType(e.getType()).ifPresent(dep -> {
                                    if (!dep.equals(fqn)) deps.add(dep);
                                }));

                        // (4) Method call receiver type
                        body.findAll(MethodCallExpr.class).forEach(call ->
                                call.getScope().ifPresent(scope -> {
                                    try {
                                        String qn = scope.calculateResolvedType().describe();
                                        matchProjectClass(qn).ifPresent(dep -> {
                                            if (!dep.equals(fqn)) deps.add(dep);
                                        });
                                    } catch (Exception ignored) {
                                        // Unable to resolve the receiver type; skip.
                                    }
                                }));

                        // (5) Local variable declarations
                        body.findAll(VariableDeclarator.class).forEach(v ->
                                resolveType(v.getType()).ifPresent(dep -> {
                                    if (!dep.equals(fqn)) deps.add(dep);
                                }));
                    });
                });
            });
        } catch (Exception e) {
            System.err.println("[analysis] Failed to parse: " + file + " -> " + e.getMessage());
        }
    }

    // ==================== Type resolution helpers ====================

    /** Collects a type and any generic type arguments (e.g. {@code Page<User>} → Page, User). */
    private void collectTypeAndGenerics(Type type, String selfFqn, Set<String> deps) {
        resolveType(type).ifPresent(dep -> {
            if (!dep.equals(selfFqn)) deps.add(dep);
        });
        if (type instanceof ClassOrInterfaceType ct) {
            ct.getTypeArguments().ifPresent(args -> args.forEach(a ->
                    collectTypeAndGenerics(a, selfFqn, deps)));
        } else if (type instanceof ArrayType at) {
            collectTypeAndGenerics(at.getComponentType(), selfFqn, deps);
        }
    }

    /** Resolves a type to a project FQN, or empty if it is not a project type. */
    private Optional<String> resolveType(Type type) {
        if (type == null || type.isPrimitiveType() || type.isVoidType()) {
            return Optional.empty();
        }
        try {
            return matchProjectClass(type.resolve().describe());
        } catch (Exception e) {
            return matchProjectClass(type.asString());
        }
    }

    /** Matches an arbitrary type string against the set of project classes. */
    private Optional<String> matchProjectClass(String raw) {
        if (raw == null) return Optional.empty();
        String qn = raw;
        if (qn.contains("<")) qn = qn.substring(0, qn.indexOf('<'));
        qn = qn.trim();

        if (projectClasses.contains(qn)) return Optional.of(qn);

        String simple = qn.contains(".") ? qn.substring(qn.lastIndexOf('.') + 1) : qn;
        final String target = simple;
        return projectClasses.stream()
                .filter(c -> c.endsWith("." + target) || c.equals(target))
                .findFirst();
    }

    private String getVisibility(BodyDeclaration<?> decl) {
        if (!(decl instanceof NodeWithModifiers<?> nwm)) {
            return "~";
        }
        if (nwm.hasModifier(Modifier.Keyword.PRIVATE))   return "-";
        if (nwm.hasModifier(Modifier.Keyword.PUBLIC))    return "+";
        if (nwm.hasModifier(Modifier.Keyword.PROTECTED)) return "#";
        return "~";
    }

    // ==================== Diagram 1: class dependency ====================

    /**
     * Generates a per-class diagram where the current class shows its fields
     * and methods, and each dependency is rendered as a name-only node.
     *
     * <p>Output: {@code <package-path>/doc-files/<ClassName>-dependencies.svg}</p>
     */
    private void generatePerClassDiagrams() throws IOException {
        int count = 0;

        for (Map.Entry<String, ClassInfo> entry : classInfos.entrySet()) {

            String fqn = entry.getKey();
            ClassInfo info = entry.getValue();
            String simple = simpleName(fqn);

            StringBuilder sb = new StringBuilder("@startuml\n");

            // ---------- Layout ----------
            sb.append("top to bottom direction\n");

            // ---------- Global style ----------
            sb.append("skinparam shadowing false\n");
            sb.append("skinparam linetype ortho\n");
            sb.append("skinparam nodesep 60\n");
            sb.append("skinparam ranksep 80\n");

            sb.append("\n");

            // ---------- Current class ----------
            String currentKeyword = info.isInterface ? "interface" : "class";
            sb.append(currentKeyword)
                    .append(" \"")
                    .append(escape(simple))
                    .append("\" {\n");

            for (String field : info.fields) {
                sb.append("    ").append(field).append("\n");
            }
            for (String method : info.methods) {
                sb.append("    ").append(method).append("\n");
            }

            sb.append("}\n\n");

            // ---------- Dependency nodes (name only) ----------
            Set<String> deps = classDependencies.getOrDefault(
                    fqn, Collections.emptySet());

            Set<String> rendered = new LinkedHashSet<>();
            for (String dep : deps) {

                if (dep.equals(fqn)) continue;

                String depSimple = simpleName(dep);

                // Avoid duplicate node declarations for classes with the same simple name.
                if (!rendered.add(depSimple)) continue;

                ClassInfo depInfo = classInfos.get(dep);
                String kw = (depInfo != null && depInfo.isInterface)
                        ? "interface"
                        : "class";

                sb.append(kw)
                        .append(" \"")
                        .append(escape(depSimple))
                        .append("\"\n");

                // Hide members only for the dependency node.
                sb.append("hide ").append(depSimple).append(" members\n");
            }

            sb.append("\n");

            // ---------- Dependency edges ----------
            for (String dep : deps) {
                if (dep.equals(fqn)) continue;
                sb.append(simple)
                        .append(" --> ")
                        .append(simpleName(dep))
                        .append("\n");
            }

            sb.append("@enduml\n");

            // ---------- Output ----------
            Path docFiles = outputDir
                    .resolve(info.packageName.replace('.', '/'))
                    .resolve("doc-files");

            Files.createDirectories(docFiles);

            renderSvg(sb.toString(),
                    docFiles.resolve(simple + "-dependencies.svg"));

            count++;
        }

        System.out.println("[analysis] Class dependency diagrams generated: " + count);
    }

    // ==================== Diagram 2: package-local class diagram ====================

    /**
     * Generates a per-package diagram containing only the classes of that
     * package and the dependencies among them.
     *
     * <p>Output: {@code <package-path>/doc-files/<shortName>-classes.svg}</p>
     */
    private void generatePerPackageClassDiagrams() throws IOException {
        int count = 0;
        for (Map.Entry<String, Set<String>> entry : packageClasses.entrySet()) {

            String pkg = entry.getKey();
            Set<String> classes = entry.getValue();
            if (classes.isEmpty()) continue;

            StringBuilder sb = new StringBuilder("@startuml\n");

            sb.append("skinparam classAttributeIconSize 0\n");
            sb.append("skinparam shadowing false\n");
            sb.append("skinparam nodesep 50\n");
            sb.append("skinparam ranksep 70\n");
            sb.append("skinparam linetype ortho\n");

            // Show only class/interface names — no fields or methods.
            sb.append("hide members\n");
            sb.append("hide attributes\n");
            sb.append("hide methods\n");

            sb.append("\n");

            // Class declarations
            for (String fqn : classes) {
                ClassInfo info = classInfos.get(fqn);
                String kw = (info != null && info.isInterface) ? "interface" : "class";
                sb.append(kw).append(" ").append(simpleName(fqn)).append("\n");
            }
            sb.append("\n");

            // Intra-package edges only
            Set<String> edges = new LinkedHashSet<>();
            for (String fqn : classes) {
                String from = simpleName(fqn);
                Set<String> deps = classDependencies.getOrDefault(fqn, Collections.emptySet());
                for (String dep : deps) {
                    if (dep.equals(fqn)) continue;
                    if (!classes.contains(dep)) continue;
                    String to = simpleName(dep);
                    String edge = from + " --> " + to;
                    if (edges.add(edge)) {
                        sb.append(edge).append("\n");
                    }
                }
            }
            sb.append("@enduml\n");

            Path docFiles = outputDir.resolve(pkg.replace('.', '/')).resolve("doc-files");
            Files.createDirectories(docFiles);
            renderSvg(sb.toString(),
                    docFiles.resolve(simpleName(pkg) + "-classes.svg"));

            count++;
        }
        System.out.println("[analysis] Package class diagrams generated: " + count);
    }

    // ==================== Diagram 3: overview ====================

    /**
     * Generates the global overview diagram: package nodes plus inter-package
     * dependencies. Packages listed in {@link #OVERVIEW_EXCLUDED_PACKAGES}
     * are omitted.
     *
     * <p>Output: {@code doc-files/overview-dependencies.svg}</p>
     */
    private void generateOverviewDiagram() throws IOException {

        // ---------- Aggregate inter-package dependencies ----------
        Map<String, Set<String>> pkgDeps = new LinkedHashMap<>();

        for (Map.Entry<String, Set<String>> entry : classDependencies.entrySet()) {

            ClassInfo fromInfo = classInfos.get(entry.getKey());
            if (fromInfo == null) continue;

            for (String toClass : entry.getValue()) {

                ClassInfo toInfo = classInfos.get(toClass);
                if (toInfo == null) continue;

                if (!fromInfo.packageName.equals(toInfo.packageName)) {
                    pkgDeps
                            .computeIfAbsent(fromInfo.packageName,
                                    k -> new LinkedHashSet<>())
                            .add(toInfo.packageName);
                }
            }
        }

        if (pkgDeps.isEmpty()) {
            System.out.println("[analysis] No inter-package dependencies, skipping overview.");
            return;
        }

        // ---------- Collect packages that actually participate ----------
        Set<String> activePackages = new LinkedHashSet<>();

        for (Map.Entry<String, Set<String>> entry : pkgDeps.entrySet()) {

            if (OVERVIEW_EXCLUDED_PACKAGES.contains(entry.getKey())) continue;

            activePackages.add(entry.getKey());

            for (String toPackage : entry.getValue()) {
                if (OVERVIEW_EXCLUDED_PACKAGES.contains(toPackage)) continue;
                activePackages.add(toPackage);
            }
        }

        if (activePackages.isEmpty()) {
            System.out.println("[analysis] No packages left after exclusion, skipping overview.");
            return;
        }

        // ---------- PlantUML ----------
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");

        // Global style
        sb.append("skinparam shadowing false\n");
        sb.append("skinparam linetype ortho\n");
        sb.append("skinparam nodesep 30\n");
        sb.append("skinparam ranksep 55\n");

        // Node style
        sb.append("skinparam rectangle {\n");
        sb.append("    RoundCorner 12\n");
        sb.append("    BorderThickness 1\n");
        sb.append("    Padding 12\n");
        sb.append("}\n");

        // Arrow style
        sb.append("skinparam ArrowThickness 1\n");
        sb.append("skinparam ArrowColor #555555\n");

        sb.append("\n");

        // Package nodes
        for (String pkg : activePackages) {
            sb.append("rectangle \"")
                    .append(escape(pkg))
                    .append("\" as ")
                    .append(aliasFor(pkg))
                    .append("\n");
        }

        sb.append("\n");

        // Inter-package edges
        for (Map.Entry<String, Set<String>> entry : pkgDeps.entrySet()) {

            if (OVERVIEW_EXCLUDED_PACKAGES.contains(entry.getKey())) continue;

            String from = aliasFor(entry.getKey());

            for (String toPackage : entry.getValue()) {

                if (OVERVIEW_EXCLUDED_PACKAGES.contains(toPackage)) continue;

                sb.append(from)
                        .append(" --> ")
                        .append(aliasFor(toPackage))
                        .append("\n");
            }
        }

        sb.append("\n@enduml\n");

        // ---------- Output ----------
        Path docFiles = outputDir.resolve("doc-files");
        Files.createDirectories(docFiles);
        renderSvg(sb.toString(), docFiles.resolve("overview-dependencies.svg"));

        System.out.println("[analysis] Overview diagram generated.");
    }

    // ==================== Common helpers ====================

    /** Escapes a value for use inside a PlantUML quoted string. */
    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private void renderSvg(String puml, Path svgPath) throws IOException {
        try (OutputStream os = Files.newOutputStream(svgPath)) {
            SourceStringReader reader = new SourceStringReader(puml);
            reader.outputImage(os, new FileFormatOption(FileFormat.SVG));
        }
    }

    private String simpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    private String aliasFor(String pkg) {
        return "pkg_" + pkg.replace('.', '_');
    }

    // ==================== Data model ====================

    static class ClassInfo {
        String packageName;
        boolean isInterface;
        List<String> fields = new ArrayList<>();
        List<String> methods = new ArrayList<>();
    }
}