/**
 * Workbench shell for MinDis: module lifecycle, sidebar navigation.
 * Bespoke implementation with a WorkbenchFX-inspired API
 * (docs/adr/005-workbench-shell.md).
 */
module org.mindis.workbench {
    exports org.mindis.workbench;

    requires java.logging;
    requires javafx.controls;
    requires org.kordamp.ikonli.javafx;
    // Icon pack resolved via ServiceLoader; must be in the module graph.
    requires org.kordamp.ikonli.materialdesign2;
}
