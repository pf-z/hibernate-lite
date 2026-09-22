package me.pfzh.hibernatelite.arch;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Injects the generated SVG dependency diagrams into the Javadoc HTML pages.
 *
 * <p>Injection targets:</p>
 * <ul>
 *   <li>Class page ({@code Xxx.html})           → {@code doc-files/Xxx-dependencies.svg}</li>
 *   <li>Package page ({@code package-summary.html}) → {@code doc-files/<shortName>-classes.svg}</li>
 *   <li>Overview page ({@code index.html} / {@code overview-summary.html})
 *       → {@code doc-files/overview-dependencies.svg}</li>
 * </ul>
 *
 * <p>Every injected diagram carries a small toggle button that switches
 * between fit-to-width (default) and original size (horizontal scrolling).</p>
 *
 * @author Pengfei Zhang
 * @since 2026/9/21
 */
class JavadocInjector {

    /** HTML files that should never be processed. */
    private static final Set<String> SKIP_FILES = Set.of(
            "overview-tree.html",
            "deprecated-list.html",
            "allclasses-index.html",
            "allpackages-index.html",
            "constant-values.html",
            "serialized-form.html",
            "help-doc.html",
            "index-all.html",
            "search.html",
            "tree.html"
    );

    private final Path javadocDir;

    JavadocInjector(Path javadocDir) {
        this.javadocDir = javadocDir;
    }

    /**
     * Walks the Javadoc output directory and injects diagrams into every page
     * that has a matching SVG file.
     */
    void injectAll() throws IOException {
        int[] classCount = {0};
        int[] pkgCount = {0};
        int[] overviewCount = {0};

        try (Stream<Path> s = Files.walk(javadocDir)) {
            s.filter(p -> p.toString().endsWith(".html"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        try {
                            if ("package-summary.html".equals(fileName)) {
                                if (processPackagePage(p)) pkgCount[0]++;
                            } else if ("index.html".equals(fileName)
                                    || "overview-summary.html".equals(fileName)) {
                                if (processOverviewPage(p)) overviewCount[0]++;
                            } else if (!SKIP_FILES.contains(fileName)
                                    && !fileName.startsWith("package-")
                                    && !fileName.startsWith("module-")) {
                                if (processClassPage(p)) classCount[0]++;
                            }
                        } catch (Exception e) {
                            System.err.println("[analysis] Injection failed: " + p + " -> " + e.getMessage());
                        }
                    });
        }

        System.out.println("[analysis] Class pages injected:    " + classCount[0]);
        System.out.println("[analysis] Package pages injected:  " + pkgCount[0]);
        System.out.println("[analysis] Overview pages injected: " + overviewCount[0]);
    }

    // ==================== Class page ====================

    /**
     * Injects {@code <ClassName>-dependencies.svg} into the class description area.
     */
    private boolean processClassPage(Path htmlFile) throws IOException {
        String fileName = htmlFile.getFileName().toString();
        String className = fileName.substring(0, fileName.length() - 5);

        Path svgPath = htmlFile.getParent()
                .resolve("doc-files")
                .resolve(className + "-dependencies.svg");
        if (!Files.exists(svgPath)) return false;

        Document doc = parse(htmlFile);
        if (doc.selectFirst("img.uml-dependency-diagram") != null) return false;

        Element target = doc.selectFirst("section.class-description");
        if (target == null) target = doc.selectFirst("div.class-description");
        if (target == null) {
            Element signature = doc.selectFirst("div.type-signature");
            if (signature != null) target = signature.parent();
        }
        if (target == null) return false;

        appendImage(target,
                "doc-files/" + className + "-dependencies.svg",
                "uml-dependency-diagram",
                "Dependency diagram for " + className);

        Files.writeString(htmlFile, doc.outerHtml());
        return true;
    }

    // ==================== Package page ====================

    /**
     * Injects the package-local class diagram ({@code <shortName>-classes.svg})
     * into the package description area.
     */
    private boolean processPackagePage(Path htmlFile) throws IOException {
        Path docFiles = htmlFile.getParent().resolve("doc-files");
        if (!Files.exists(docFiles) || !Files.isDirectory(docFiles)) return false;

        Path svgFile;
        try (Stream<Path> s = Files.list(docFiles)) {
            Optional<Path> found = s
                    .filter(p -> p.getFileName().toString().endsWith("-classes.svg"))
                    .findFirst();
            if (found.isEmpty()) return false;
            svgFile = found.get();
        }

        Document doc = parse(htmlFile);
        if (doc.selectFirst("img.uml-package-diagram") != null) return false;

        Element target = doc.selectFirst("section.package-description");
        if (target == null) target = doc.selectFirst("div.package-description");
        if (target == null) {
            Element header = doc.selectFirst("section.package-header");
            if (header != null) target = header.parent();
        }
        if (target == null) target = doc.selectFirst("main");
        if (target == null) target = doc.body();
        if (target == null) return false;

        appendImage(target,
                "doc-files/" + svgFile.getFileName(),
                "uml-package-diagram",
                "Class diagram for package");

        Files.writeString(htmlFile, doc.outerHtml());
        return true;
    }

    // ==================== Overview page ====================

    /**
     * Injects the global package diagram ({@code overview-dependencies.svg})
     * directly beneath the page title, above the package list.
     */
    private boolean processOverviewPage(Path htmlFile) throws IOException {
        Path overviewSvg = javadocDir.resolve("doc-files/overview-dependencies.svg");
        if (!Files.exists(overviewSvg)) return false;

        Document doc = parse(htmlFile);
        if (doc.selectFirst("img.uml-overview-diagram") != null) return false;

        // Preferred: insert right after <div class="header"> (contains <h1 class="title">).
        Element title = doc.selectFirst("h1.title");
        if (title != null) {
            Element header = title.parent();
            if (header != null) {
                Element wrapper = doc.createElement("div");
                header.after(wrapper);
                appendImage(wrapper, "doc-files/overview-dependencies.svg",
                        "uml-overview-diagram", "Overview dependency diagram");
                Files.writeString(htmlFile, doc.outerHtml());
                return true;
            }
            Element wrapper = doc.createElement("div");
            title.after(wrapper);
            appendImage(wrapper, "doc-files/overview-dependencies.svg",
                    "uml-overview-diagram", "Overview dependency diagram");
            Files.writeString(htmlFile, doc.outerHtml());
            return true;
        }

        // Fallback: prepend to the content container, main, or body.
        Element content = doc.selectFirst("section.overview-summary div.content-container");
        if (content == null) content = doc.selectFirst("div.content-container");
        if (content == null) content = doc.selectFirst("main");
        if (content == null) content = doc.body();
        if (content == null) return false;

        appendImage(content, "doc-files/overview-dependencies.svg",
                "uml-overview-diagram", "Overview dependency diagram");

        Files.writeString(htmlFile, doc.outerHtml());
        return true;
    }

    // ==================== Helpers ====================

    /**
     * Parses an HTML file with pretty-printing disabled so that the original
     * Javadoc layout (indentation, whitespace, alignment) is preserved.
     */
    private Document parse(Path htmlFile) throws IOException {
        Document doc = Jsoup.parse(htmlFile.toFile(), "UTF-8");
        doc.outputSettings().prettyPrint(false);
        return doc;
    }

    /**
     * Appends a centered, borderless diagram image to the given element,
     * together with a small toggle button that switches between fit-to-width
     * (default) and original size (horizontal scrolling).
     */
    private void appendImage(Element target, String src, String cssClass, String alt) {
        String imgId  = "uml-img-" + Math.abs(src.hashCode());
        String wrapId = imgId + "-wrap";

        Element wrapper = target.appendElement("div");
        wrapper.addClass("uml-diagram");
        wrapper.attr("id", wrapId);
        wrapper.attr("style",
                "text-align:center; margin-top:0; overflow-x:hidden; position:relative;");

        // Shared styles for the toggle link, declared once per wrapper.
        Element style = wrapper.appendElement("style");
        style.appendText(
                ".uml-toggle{"
                        + "  font-family:inherit;"
                        + "  font-size:12px;"
                        + "  color:#3c5a99;"
                        + "  text-decoration:none;"
                        + "  cursor:pointer;"
                        + "  user-select:none;"
                        + "  padding:2px 4px;"
                        + "  border-radius:2px;"
                        + "  transition:background 0.15s ease;"
                        + "}"
                        + ".uml-toggle:hover{"
                        + "  text-decoration:underline;"
                        + "  background:#eef2f7;"
                        + "}"
        );

        Element img = wrapper.appendElement("img");
        img.addClass(cssClass);
        img.attr("id", imgId);
        img.attr("src", src);
        img.attr("alt", alt);
        img.attr("style",
                "display:block; "
                        + "max-width:100%; "
                        + "margin:0 auto; "
                        + "border:none; "
                        + "padding:0; "
                        + "background:transparent;");

        // The toggle is an <a> styled as a link, pinned to the top-left corner
        // of the image wrapper and draggable within the wrapper bounds.
        Element btn = wrapper.appendElement("a");
        btn.addClass("uml-toggle");
        btn.text("Original Size");
        btn.attr("style",
                "position:absolute; left:6px; top:6px; z-index:10;");

        btn.attr("onclick",
                "if(this.dataset.wasDrag==='1'){this.dataset.wasDrag='0';return false;}"
                        + "var i=document.getElementById('" + imgId + "');"
                        + "var w=document.getElementById('" + wrapId + "');"
                        + "if(i.style.maxWidth==='100%'){"
                        + "  i.style.maxWidth='none';"
                        + "  w.style.overflowX='auto';"
                        + "  this.textContent='Fit Width';"
                        + "} else {"
                        + "  i.style.maxWidth='100%';"
                        + "  w.style.overflowX='hidden';"
                        + "  this.textContent='Original Size';"
                        + "}"
                        + "return false;");

        btn.attr("onpointerdown",
                "this.setPointerCapture(event.pointerId);"
                        + "this.dataset.dragX=event.clientX-this.offsetLeft;"
                        + "this.dataset.dragY=event.clientY-this.offsetTop;"
                        + "this.dataset.dragging='1';"
                        + "this.dataset.downX=event.clientX;"
                        + "this.dataset.downY=event.clientY;"
                        + "event.preventDefault();"
                        + "event.stopPropagation();");

        btn.attr("onpointermove",
                "if(this.dataset.dragging!=='1')return;"
                        + "var w=document.getElementById('" + wrapId + "');"
                        + "var x=event.clientX-parseInt(this.dataset.dragX);"
                        + "var y=event.clientY-parseInt(this.dataset.dragY);"
                        + "x=Math.max(0,Math.min(x,w.clientWidth-this.offsetWidth));"
                        + "y=Math.max(0,Math.min(y,w.clientHeight-this.offsetHeight));"
                        + "this.style.left=x+'px';"
                        + "this.style.top=y+'px';"
                        + "this.style.right='auto';");

        btn.attr("onpointerup",
                "this.dataset.dragging='0';"
                        + "var dx=Math.abs(event.clientX-parseInt(this.dataset.downX||0));"
                        + "var dy=Math.abs(event.clientY-parseInt(this.dataset.downY||0));"
                        + "this.dataset.wasDrag=(dx>3||dy>3)?'1':'0';");
    }
}