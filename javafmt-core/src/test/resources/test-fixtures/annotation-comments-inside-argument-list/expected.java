import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({
        ElementType.ANNOTATION_TYPE, // for combined annotations
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PARAMETER // for properties
})
@Retention(RetentionPolicy.RUNTIME)
@interface Alias {}

@Target({
        ElementType.TYPE,
        ElementType.METHOD,
        // since the second release
        ElementType.FIELD
})
@Retention(RetentionPolicy.RUNTIME)
@interface Command {
    String name();

    String description();
}

@Command(
        //
        name = "execute", //
        description = "Execute the invoices" //
)
class ExecuteCommand {
    @Option(names = "--details", description = "Select an output mode. " //
            + "Use one of the listed modes. If none is selected, " //
            + "only the summary is shown.")
    private String details;

    @Option(
            names = "--currency", //
            description = "The currency of every invoice")
    private String currency;
}

@SuppressWarnings(
        // the comparison is cheap, so both sides always run
        "ShortCircuitBoolean")
class Comparison {}

@SuppressWarnings(
        // the casts are checked by the caller
        {"unchecked", "rawtypes"})
class Conversion {}

@interface Option {
    String names();

    String description();
}
