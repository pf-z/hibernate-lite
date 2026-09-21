package me.pfzh.hibernatelite.arch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point of the dependency analysis tool.
 *
 * <p>Execution flow:</p>
 * <ol>
 *   <li>Analyze source code to build the dependency graph.</li>
 *   <li>Generate SVG diagrams into the Javadoc output directory.</li>
 *   <li>Inject the generated SVGs into the already-generated Javadoc HTML pages.</li>
 * </ol>
 *
 * @author Pengfei Zhang
 * @since 2026/9/21
 */
public class ArchMain {

    public static void main(String[] args) throws Exception {

        // Base package to analyze; may be overridden via the first CLI argument.
        String basePackage = args.length > 0 ? args[0] : "me.pfzh.hibernatelite";

        Path projectRoot = Paths.get("").toAbsolutePath();
        Path sourceDir   = projectRoot.resolve("src/main/java");
        Path outputDir   = projectRoot.resolve("target/site/apidocs");

        System.out.println("[analysis] Project root: " + projectRoot);
        System.out.println("[analysis] Source dir:   " + sourceDir);
        System.out.println("[analysis] Output dir:   " + outputDir);
        System.out.println("[analysis] Base package: " + basePackage);

        if (!Files.exists(sourceDir)) {
            System.err.println("[analysis] Source directory does not exist, aborting.");
            System.exit(1);
        }
        if (!Files.exists(outputDir)) {
            System.err.println("[analysis] Javadoc output directory does not exist. "
                    + "Please run `mvn package` first.");
            System.exit(1);
        }

        // 1. Analyze source files and generate SVG diagrams.
        DependencyAnalyzer analyzer = new DependencyAnalyzer(sourceDir, outputDir, basePackage);
        analyzer.analyzeAndGenerate();

        // 2. Inject the generated SVGs into the Javadoc HTML pages.
        JavadocInjector injector = new JavadocInjector(outputDir);
        injector.injectAll();

        System.out.println("[analysis] Done.");
    }
}