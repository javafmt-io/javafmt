package io.javafmt.intellij;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import io.javafmt.JavafmtConfig;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class FileScopeTest extends JavafmtIntellijTestCase {

    private static final Path ROOT = createRoot();

    private static final Path LIBRARY = ROOT.resolve("libraries/compile");

    private static final Path TEST_LIBRARY = ROOT.resolve("libraries/test");

    private static final Path RUNTIME_LIBRARY = ROOT.resolve("libraries/runtime");

    private static final Path OUTPUT = ROOT.resolve("classes/main");

    private static final Path TEST_OUTPUT = ROOT.resolve("classes/test");

    private static final Path TEST_SOURCES = ROOT.resolve("src/test/java");

    private static final LightProjectDescriptor MODULE_WITH_DEPENDENCIES = new DefaultLightProjectDescriptor() {
        @Override
        public void configureModule(@NotNull final Module module,
                                    @NotNull final ModifiableRootModel model,
                                    @NotNull final ContentEntry contentEntry) {
            super.configureModule(module, model, contentEntry);
            Stream.of(LIBRARY, TEST_LIBRARY, RUNTIME_LIBRARY, OUTPUT, TEST_OUTPUT, TEST_SOURCES)
                    .forEach(FileScopeTest::createDirectory);
            addLibrary(model, LIBRARY, DependencyScope.COMPILE);
            addLibrary(model, TEST_LIBRARY, DependencyScope.TEST);
            addLibrary(model, RUNTIME_LIBRARY, DependencyScope.RUNTIME);
            final var compiler = model.getModuleExtension(CompilerModuleExtension.class);
            compiler.inheritCompilerOutputPath(false);
            compiler.setCompilerOutputPath(url(OUTPUT));
            compiler.setCompilerOutputPathForTests(url(TEST_OUTPUT));
            model.addContentEntry(url(TEST_SOURCES)).addSourceFolder(url(TEST_SOURCES), true);
        }
    };

    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return MODULE_WITH_DEPENDENCIES;
    }

    @Test
    public void testAMainSourceGetsItsModulesProductionCompileClasspath() {
        final var file = myFixture.configureByText("Order.java", "class Order {}")
                .getVirtualFile();
        assertThat(classpath(FileScope.of(getProject(), file)))
                .contains(LIBRARY.toString(), OUTPUT.toString())
                .doesNotContain(TEST_LIBRARY.toString(), TEST_OUTPUT.toString(), RUNTIME_LIBRARY.toString());
    }

    @Test
    public void testATestSourceGetsItsModulesTestCompileClasspath() throws IOException {
        final var file = localFile(TEST_SOURCES.resolve("OrderTest.java"), "class OrderTest {}");
        assertThat(classpath(FileScope.of(getProject(), file)))
                .contains(LIBRARY.toString(), OUTPUT.toString(), TEST_LIBRARY.toString(), TEST_OUTPUT.toString())
                .doesNotContain(RUNTIME_LIBRARY.toString());
    }

    @Test
    public void testAFileOnDiskCarriesItsPath() throws IOException {
        final var path = TEST_SOURCES.resolve("InvoiceTest.java");
        assertThat(
                        FileScope.of(getProject(), localFile(path, "class InvoiceTest {}"))
                                .path())
                .contains(path);
    }

    @Test
    public void testAScratchFileIsParseOnly() {
        final var scratch = scratchFile("Customer.java", "class Customer {}").getVirtualFile();
        assertThat(FileScope.of(getProject(), scratch).analysis()).isEqualTo(new JavafmtConfig.Analysis.ParseOnly());
    }

    @Test
    public void testAFileOutsideEveryModuleIsParseOnly(@TempDir final Path dir) throws IOException {
        final var file = localFile(dir.resolve("Customer.java"), "class Customer {}");
        assertThat(FileScope.of(getProject(), file).analysis()).isEqualTo(new JavafmtConfig.Analysis.ParseOnly());
    }

    private static List<String> classpath(final FileScope scope) {
        return switch (scope.analysis()) {
            case JavafmtConfig.Analysis.Attributed(var classpath) -> List.of(classpath.split(File.pathSeparator));
            case JavafmtConfig.Analysis.ParseOnly _ -> List.of();
        };
    }

    private static void addLibrary(final ModifiableRootModel model, final Path classes, final DependencyScope scope) {
        final var library = model.getModuleLibraryTable()
                .createLibrary(classes.getFileName().toString());
        final var libraryModel = library.getModifiableModel();
        libraryModel.addRoot(url(classes), OrderRootType.CLASSES);
        libraryModel.commit();
        Objects.requireNonNull(model.findLibraryOrderEntry(library)).setScope(scope);
    }

    private static String url(final Path path) {
        return VfsUtilCore.pathToUrl(path.toString());
    }

    private static void createDirectory(final Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory);
    }

    private static Path createRoot() {
        try {
            return Files.createTempDirectory("javafmt-file-scope").toRealPath();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
