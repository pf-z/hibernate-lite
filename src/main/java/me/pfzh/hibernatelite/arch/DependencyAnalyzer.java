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
 * <p>Each dependency is tagged with one of three kinds, so diagrams can
 * distinguish them by color:</p>
 * <ul>
 *   <li>{@link DepKind#EXTENDS}   — {@code extends} / {@code implements}</li>
 *   <li>{@link DepKind#SIGNATURE} — method parameter and return types</li>
 *   <li>{@link DepKind#USAGE}     — {@code new}, local vars, method calls</li>
 * </ul>
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
 *   <li>Class page:    {@code <ClassName>-dependencies.svg}</li>
 *   <li>Package page:  {@code <shortName>-classes.svg}</li>
 *   <li>Overview page: {@code overview-dependencies.svg}</li>
 * </ul>
 *
 * @author Pengfei Zhang
 * @since 2026/9/21
 */
class DependencyAnalyzer {

    // ==================== Dependency kinds ====================

    /** Kind of dependency between two classes. */
    enum DepKind {
        /** {@code extends} / {@code implements}. */
        EXTENDS,
        /** Method parameter / return type. */
        SIGNATURE,
        /** {@code new}, local var, method call. */
        USAGE
    }

    // Colors used for the three dependency kinds.
    // Hue, lightness and saturation are all separated so the three are
    // distinguishable even in thumbnail view or grayscale print.
    private static final String COLOR_EXTENDS   = "#F9A825";   // bright red     (hue 0°,   medium)
    private static final String COLOR_SIGNATURE = "#0277BD";   // deep sky blue  (hue 200°, dark)
    private static final String COLOR_USAGE     = "#D32F2F";   // amber          (hue 45°,  bright)

    private final Path sourceDir;
    private final Path outputDir;
    private final String basePackage;

    /** FQN -> class information */
    private final Map<String, ClassInfo> classInfos = new LinkedHashMap<>();
    /** Package name -> set of FQNs in this package */
    private final Map<String, Set<String>> packageClasses = new LinkedHashMap<>();
    /** FQN -> set of dependency FQNs (flat union) */
    private final Map<String, Set<String>> classDependencies = new LinkedHashMap<>();
    /** FQN -> kind -> set of dependency FQNs (kind-aware) */
    private final Map<String, Map<DepKind, Set<String>>> classDependenciesByKind
            = new LinkedHashMap<>();
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

                packageClasses.computeIfAbsent(pkg, k -> new LinkedHashSet<>()).add(fqn);

                // (1) extends / implements → EXTENDS
                clazz.getExtendedTypes().forEach(t -> resolveType(t).ifPresent(p ->
                        addDependency(fqn, p, DepKind.EXTENDS)));
                clazz.getImplementedTypes().forEach(t -> resolveType(t).ifPresent(p ->
                        addDependency(fqn, p, DepKind.EXTENDS)));

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

                    // (2) Return / parameter types → SIGNATURE
                    collectTypeAndGenerics(method.getType(), fqn, DepKind.SIGNATURE);
                    method.getParameters().forEach(p ->
                            collectTypeAndGenerics(p.getType(), fqn, DepKind.SIGNATURE));

                    // (3)(4)(5) Method body → USAGE
                    method.getBody().ifPresent(body -> {
                        // (3) new X()
                        body.findAll(ObjectCreationExpr.class).forEach(e ->
                                resolveType(e.getType()).ifPresent(dep ->
                                        addDependency(fqn, dep, DepKind.USAGE)));

                        // (4) Method call receiver type
                        body.findAll(MethodCallExpr.class).forEach(call ->
                                call.getScope().ifPresent(scope -> {
                                    try {
                                        String qn = scope.calculateResolvedType().describe();
                                        matchProjectClass(qn).ifPresent(dep ->
                                                addDependency(fqn, dep, DepKind.USAGE));
                                    } catch (Exception ignored) {
                                        // Unable to resolve the receiver type; skip.
                                    }
                                }));

                        // (5) Local variable declarations
                        body.findAll(VariableDeclarator.class).forEach(v ->
                                resolveType(v.getType()).ifPresent(dep ->
                                        addDependency(fqn, dep, DepKind.USAGE)));
                    });
                });
            });
        } catch (Exception e) {
            System.err.println("[analysis] Failed to parse: " + file + " -> " + e.getMessage());
        }
    }

    // ==================== Dependency recording ====================

    /**
     * Records a dependency from {@code from} to {@code to} with the given kind.
     * Updates both the flat union map (for aggregation) and the kind-aware map
     * (for rendering). Self-dependencies are ignored.
     */
    private void addDependency(String from, String to, DepKind kind) {
        if (from == null || to == null || from.equals(to)) return;

        classDependencies
                .computeIfAbsent(from, k -> new LinkedHashSet<>())
                .add(to);

        classDependenciesByKind
                .computeIfAbsent(from, k -> new LinkedHashMap<>())
                .computeIfAbsent(kind, k -> new LinkedHashSet<>())
                .add(to);
    }

    // ==================== Type resolution helpers ====================

    /** Collects a type and any generic type arguments (e.g. {@code Page<User>} → Page, User). */
    private void collectTypeAndGenerics(Type type, String from, DepKind kind) {
        resolveType(type).ifPresent(dep -> addDependency(from, dep, kind));
        if (type instanceof ClassOrInterfaceType ct) {
            ct.getTypeArguments().ifPresent(args -> args.forEach(a ->
                    collectTypeAndGenerics(a, from, kind)));
        } else if (type instanceof ArrayType at) {
            collectTypeAndGenerics(at.getComponentType(), from, kind);
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
     * Arrows are colored by {@link DepKind}.
     *
     * <p>Layout is left to Graphviz (no forced direction), with
     * {@code linetype polyline} so edges take the shortest path and nodes stay
     * close together without manual tuning.</p>
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

            // ---------- Global style: auto-compact ----------
            sb.append("skinparam shadowing false\n");
            sb.append("skinparam linetype polyline\n");
            sb.append("skinparam nodesep 25\n");
            sb.append("skinparam ranksep 45\n");
            sb.append("skinparam padding 4\n");
            sb.append("skinparam ArrowFontSize 10\n");

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

            // ---------- Dependency edges, colored by kind ----------
            appendKindEdges(sb, simple, fqn);

            // ---------- Legend ----------
            appendLegend(sb);

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
     * Generates a per-package diagram that includes:
     * <ul>
     *   <li>All classes/interfaces declared in this package (simple name).</li>
     *   <li>Any external class referenced by these classes (fully-qualified name).</li>
     * </ul>
     * Arrows are colored by {@link DepKind}.
     *
     * <p>Layout is left to Graphviz (no forced direction), with
     * {@code linetype polyline} for compact routing.</p>
     *
     * <p>Output: {@code <package-path>/doc-files/<shortName>-classes.svg}</p>
     */
    private void generatePerPackageClassDiagrams() throws IOException {
        int count = 0;
        for (Map.Entry<String, Set<String>> entry : packageClasses.entrySet()) {

            String pkg = entry.getKey();
            Set<String> classes = entry.getValue();
            if (classes.isEmpty()) continue;

            // ---------- 1. Collect external classes ----------
            Set<String> externalNodes = new LinkedHashSet<>();
            for (String fqn : classes) {
                Set<String> deps = classDependencies.getOrDefault(fqn, Collections.emptySet());
                for (String dep : deps) {
                    if (dep.equals(fqn)) continue;
                    if (classes.contains(dep)) continue;
                    externalNodes.add(dep);
                }
            }

            StringBuilder sb = new StringBuilder("@startuml\n");

            // Prevent PlantUML from splitting quoted FQNs into nested namespaces.
            sb.append("set namespaceSeparator none\n");

            sb.append("skinparam classAttributeIconSize 0\n");
            sb.append("skinparam shadowing false\n");
            sb.append("skinparam linetype polyline\n");
            sb.append("skinparam nodesep 20\n");
            sb.append("skinparam ranksep 40\n");
            sb.append("skinparam padding 4\n");

            // Show only class/interface names — no fields or methods.
            sb.append("hide members\n");
            sb.append("hide attributes\n");
            sb.append("hide methods\n");

            sb.append("\n");

            // ---------- 2. Internal class declarations (simple name) ----------
            for (String fqn : classes) {
                ClassInfo info = classInfos.get(fqn);
                String kw = (info != null && info.isInterface) ? "interface" : "class";
                sb.append(kw).append(" ").append(simpleName(fqn)).append("\n");
            }
            sb.append("\n");

            // ---------- 3. External class declarations (quoted FQN) ----------
            for (String fqn : externalNodes) {
                ClassInfo info = classInfos.get(fqn);
                String kw = (info != null && info.isInterface) ? "interface" : "class";
                sb.append(kw)
                        .append(" \"")
                        .append(escape(fqn))
                        .append("\"\n");
            }
            sb.append("\n");

            // ---------- 4. Edges, colored by kind ----------
            appendKindEdgesForPackage(sb, classes);

            // ---------- 5. Legend ----------
            appendLegend(sb);

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

        // Global style: compact routing.
        sb.append("skinparam shadowing false\n");
        sb.append("skinparam linetype polyline\n");
        sb.append("skinparam nodesep 25\n");
        sb.append("skinparam ranksep 45\n");

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

    // ==================== Edge rendering helpers ====================

    /**
     * Appends dependency edges for a single source class, colored by
     * {@link DepKind}. Each edge carries a short letter label (E / S / U)
     * so that parallel edges between the same pair of classes remain
     * distinguishable.
     *
     * <p>All relation kinds are preserved: a pair of classes that are linked
     * by more than one kind will render one edge per kind.</p>
     */
    private void appendKindEdges(StringBuilder sb, String fromName, String fromFqn) {

        Map<DepKind, Set<String>> byKind = classDependenciesByKind
                .getOrDefault(fromFqn, Collections.emptyMap());

        Set<String> drawn = new LinkedHashSet<>();

        // EXTENDS
        for (String dep : byKind.getOrDefault(DepKind.EXTENDS, Collections.emptySet())) {
            String target = simpleName(dep);
            if (drawn.add("E:" + target)) {
                sb.append(fromName)
                        .append(" -[").append(COLOR_EXTENDS).append("]-|> ")
                        .append(target)
                        .append(" : <color:").append(COLOR_EXTENDS).append(">E</color>\n");
            }
        }

        // SIGNATURE
        for (String dep : byKind.getOrDefault(DepKind.SIGNATURE, Collections.emptySet())) {
            String target = simpleName(dep);
            if (drawn.add("S:" + target)) {
                sb.append(fromName)
                        .append(" -[").append(COLOR_SIGNATURE).append(",dashed]-|> ")
                        .append(target)
                        .append(" : <color:").append(COLOR_SIGNATURE).append(">S</color>\n");
            }
        }

        // USAGE
        for (String dep : byKind.getOrDefault(DepKind.USAGE, Collections.emptySet())) {
            String target = simpleName(dep);
            if (drawn.add("U:" + target)) {
                sb.append(fromName)
                        .append(" -[").append(COLOR_USAGE).append(",dotted]-|> ")
                        .append(target)
                        .append(" : <color:").append(COLOR_USAGE).append(">U</color>\n");
            }
        }
    }

    /**
     * Appends dependency edges for all classes in a package, colored by
     * {@link DepKind}. Targets inside the package use their simple name;
     * external targets use their FQN in quotes. Each edge carries a short
     * letter label (E / S / U) so parallel edges remain distinguishable.
     */
    private void appendKindEdgesForPackage(StringBuilder sb, Set<String> classes) {

        Set<String> drawn = new LinkedHashSet<>();

        for (String fqn : classes) {
            String fromName = simpleName(fqn);

            Map<DepKind, Set<String>> byKind = classDependenciesByKind
                    .getOrDefault(fqn, Collections.emptyMap());

            // EXTENDS
            for (String dep : byKind.getOrDefault(DepKind.EXTENDS, Collections.emptySet())) {
                String target = classes.contains(dep) ? simpleName(dep) : dep;
                if (drawn.add("E:" + fromName + "->" + target)) {
                    sb.append(solidEdge(fromName, target, COLOR_EXTENDS, "E"));
                }
            }

            // SIGNATURE
            for (String dep : byKind.getOrDefault(DepKind.SIGNATURE, Collections.emptySet())) {
                String target = classes.contains(dep) ? simpleName(dep) : dep;
                if (drawn.add("S:" + fromName + "->" + target)) {
                    sb.append(dashedEdge(fromName, target, COLOR_SIGNATURE, "S"));
                }
            }

            // USAGE
            for (String dep : byKind.getOrDefault(DepKind.USAGE, Collections.emptySet())) {
                String target = classes.contains(dep) ? simpleName(dep) : dep;
                if (drawn.add("U:" + fromName + "->" + target)) {
                    sb.append(dottedEdge(fromName, target, COLOR_USAGE, "U"));
                }
            }
        }
    }

    /** Solid edge — used for EXTENDS. Label letter is colored to match the line. */
    private String solidEdge(String fromName, String target, String color, String label) {
        String from = quoteIfNeeded(fromName);
        String to   = quoteIfNeeded(target);
        return from + " -[" + color + "]-|> " + to
                + " : <color:" + color + ">" + label + "</color>\n";
    }

    /** Dashed edge — used for SIGNATURE. Label letter is colored to match the line. */
    private String dashedEdge(String fromName, String target, String color, String label) {
        String from = quoteIfNeeded(fromName);
        String to   = quoteIfNeeded(target);
        return from + " -[" + color + ",dashed]-|> " + to
                + " : <color:" + color + ">" + label + "</color>\n";
    }

    /** Dotted edge — used for USAGE. Label letter is colored to match the line. */
    private String dottedEdge(String fromName, String target, String color, String label) {
        String from = quoteIfNeeded(fromName);
        String to   = quoteIfNeeded(target);
        return from + " -[" + color + ",dotted]-|> " + to
                + " : <color:" + color + ">" + label + "</color>\n";
    }

    /** Quotes a label if it contains a dot (i.e. looks like an FQN). */
    private String quoteIfNeeded(String label) {
        return label.contains(".")
                ? "\"" + escape(label) + "\""
                : label;
    }

    /**
     * Appends a small legend explaining the three dependency kinds and the
     * edge labels E / S / U. Colors are referenced from the constants so the
     * legend stays in sync with the actual edge colors.
     */
    private void appendLegend(StringBuilder sb) {
        sb.append("legend right\n");
        sb.append("  <b>Dependencies</b>\n");
        sb.append("  <color:").append(COLOR_EXTENDS)
                .append(">───▶</color> E : extends / implements\n");
        sb.append("  <color:").append(COLOR_SIGNATURE)
                .append(">╌╌╌╌╌▶</color> S : parameter / return\n");
        sb.append("  <color:").append(COLOR_USAGE)
                .append(">┈┈┈▶</color> U : usage\n");
        sb.append("endlegend\n\n");
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