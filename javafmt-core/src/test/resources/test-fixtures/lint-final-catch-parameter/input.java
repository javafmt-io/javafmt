class InvoiceReader {

    Invoice read(Path path) {
        try {
            return parse(path);
        } catch (NoSuchFileException e) {
            log(e);
            return Invoice.empty();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new InvoiceException(e);
        } catch (IOException e) {
            e = new IOException("retry", e);
            throw new UncheckedIOException(e);
        } catch (final RuntimeException e) {
            throw e;
        } catch (@Audited Exception e) {
            throw new InvoiceException(e);
        }
    }
}
