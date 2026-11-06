class InvoiceReader {

    Invoice read(final Path path) {
        try {
            return parse(path);
        } catch (final NoSuchFileException e) {
            log(e);
            return Invoice.empty();
        } catch (final IllegalStateException | IllegalArgumentException e) {
            throw new InvoiceException(e);
        } catch (IOException e) {
            e = new IOException("retry", e);
            throw new UncheckedIOException(e);
        } catch (final RuntimeException e) {
            throw e;
        } catch (@Audited final Exception e) {
            throw new InvoiceException(e);
        }
    }
}
