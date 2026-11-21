package io.javafmt;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

final class FailingWritesFileSystem extends FileSystem {

    private static final FileSystem DEFAULT = FileSystems.getDefault();

    private final Provider provider;

    FailingWritesFileSystem(final Throwable failure) {
        if (!(failure instanceof RuntimeException || failure instanceof Error)) {
            throw new IllegalArgumentException("failure must be unchecked: " + failure);
        }
        provider = new Provider(this, failure);
    }

    Path wrap(final Path path) {
        return (Path) Proxy.newProxyInstance(Path.class.getClassLoader(), new Class<?>[]{Path.class}, new Wrapped(this, path));
    }

    private static Path unwrap(final Path path) {
        return Proxy.isProxyClass(path.getClass()) && Proxy.getInvocationHandler(path) instanceof Wrapped wrapped ? wrapped.delegate() : path;
    }

    private static Object unwrapAny(final Object argument) {
        return argument instanceof Path path ? unwrap(path) : argument;
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("close");
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return DEFAULT.getSeparator();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return StreamSupport.stream(DEFAULT.getRootDirectories().spliterator(), false)
                .map(this::wrap)
                .toList();
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return DEFAULT.getFileStores();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return DEFAULT.supportedFileAttributeViews();
    }

    @Override
    public Path getPath(final String first, final String... more) {
        return wrap(DEFAULT.getPath(first, more));
    }

    @Override
    public PathMatcher getPathMatcher(final String syntaxAndPattern) {
        final var matcher = DEFAULT.getPathMatcher(syntaxAndPattern);
        return path -> matcher.matches(unwrap(path));
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return DEFAULT.getUserPrincipalLookupService();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException("newWatchService");
    }

    private record Wrapped(FailingWritesFileSystem fileSystem, Path delegate) implements InvocationHandler {

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            if (method.getName().equals("getFileSystem")) {
                return fileSystem;
            }
            final var unwrapped = args == null
                    ? new Object[0]
                    : Arrays.stream(args)
                            .map(FailingWritesFileSystem::unwrapAny)
                            .toArray();
            try {
                final var result = method.invoke(delegate, unwrapped);
                return result instanceof Path path ? fileSystem.wrap(path) : result;
            } catch (final InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static final class Provider extends FileSystemProvider {

        private final FailingWritesFileSystem fileSystem;

        private final Throwable failure;

        private final FileSystemProvider delegate = DEFAULT.provider();

        private Provider(final FailingWritesFileSystem fileSystem, final Throwable failure) {
            this.fileSystem = fileSystem;
            this.failure = failure;
        }

        @Override
        public OutputStream newOutputStream(final Path path, final OpenOption... options) {
            if (failure instanceof RuntimeException unchecked) {
                throw unchecked;
            }
            throw (Error) failure;
        }

        @Override
        public String getScheme() {
            return "failing-writes";
        }

        @Override
        public FileSystem newFileSystem(final URI uri, final Map<String, ?> env) {
            throw new UnsupportedOperationException("newFileSystem");
        }

        @Override
        public FileSystem getFileSystem(final URI uri) {
            return fileSystem;
        }

        @Override
        public Path getPath(final URI uri) {
            return fileSystem.wrap(delegate.getPath(uri));
        }

        @Override
        public SeekableByteChannel newByteChannel(final Path path, final Set<? extends OpenOption> options, final FileAttribute<?>... attrs)
                throws IOException {
            return delegate.newByteChannel(unwrap(path), options, attrs);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(final Path dir, final DirectoryStream.Filter<? super Path> filter) {
            throw new UnsupportedOperationException("newDirectoryStream");
        }

        @Override
        public void createDirectory(final Path dir, final FileAttribute<?>... attrs) throws IOException {
            delegate.createDirectory(unwrap(dir), attrs);
        }

        @Override
        public void delete(final Path path) throws IOException {
            delegate.delete(unwrap(path));
        }

        @Override
        public void copy(final Path source, final Path target, final CopyOption... options) throws IOException {
            delegate.copy(unwrap(source), unwrap(target), options);
        }

        @Override
        public void move(final Path source, final Path target, final CopyOption... options) throws IOException {
            delegate.move(unwrap(source), unwrap(target), options);
        }

        @Override
        public boolean isSameFile(final Path path, final Path path2) throws IOException {
            return delegate.isSameFile(unwrap(path), unwrap(path2));
        }

        @Override
        public boolean isHidden(final Path path) throws IOException {
            return delegate.isHidden(unwrap(path));
        }

        @Override
        public FileStore getFileStore(final Path path) throws IOException {
            return delegate.getFileStore(unwrap(path));
        }

        @Override
        public void checkAccess(final Path path, final AccessMode... modes) throws IOException {
            delegate.checkAccess(unwrap(path), modes);
        }

        @Override
        public <V extends FileAttributeView> V getFileAttributeView(final Path path, final Class<V> type, final LinkOption... options) {
            return delegate.getFileAttributeView(unwrap(path), type, options);
        }

        @Override
        public <A extends BasicFileAttributes> A readAttributes(final Path path, final Class<A> type, final LinkOption... options)
                throws IOException {
            return delegate.readAttributes(unwrap(path), type, options);
        }

        @Override
        public Map<String, Object> readAttributes(final Path path, final String attributes, final LinkOption... options) throws IOException {
            return delegate.readAttributes(unwrap(path), attributes, options);
        }

        @Override
        public void setAttribute(final Path path, final String attribute, final Object value, final LinkOption... options) throws IOException {
            delegate.setAttribute(unwrap(path), attribute, value, options);
        }
    }
}
