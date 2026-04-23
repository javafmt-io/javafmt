package io.github.jschneidereit.grind.ir;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public sealed interface Doc
    permits Doc.Text, Doc.Line, Doc.SoftLine, Doc.HardLine,
    Doc.Indent, Doc.Group, Doc.Concat, Doc.IfBreak, Doc.Fill {

    record Text(String value) implements Doc {
        public Text {
            Objects.requireNonNull(value, "value");
            if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Text value must not contain newline characters");
            }
        }
    }

    record Line() implements Doc {
    }

    record SoftLine() implements Doc {
    }

    record HardLine() implements Doc {
    }

    record Indent(Doc contents) implements Doc {
        public Indent {
            Objects.requireNonNull(contents, "contents");
        }
    }

    record Group(Doc contents) implements Doc {
        public Group {
            Objects.requireNonNull(contents, "contents");
        }
    }

    record Concat(List<Doc> parts) implements Doc {
        public Concat {
            Objects.requireNonNull(parts, "parts");
            parts = List.copyOf(parts);
        }

        public Concat(final Stream<Doc> docs) {
            this(docs.toList());
        }
    }

    record IfBreak(Doc breakContents, Doc flatContents) implements Doc {
        public IfBreak {
            Objects.requireNonNull(breakContents, "breakContents");
            Objects.requireNonNull(flatContents, "flatContents");
        }
    }

    record Fill(List<Doc> parts) implements Doc {
        public Fill {
            Objects.requireNonNull(parts, "parts");
            parts = List.copyOf(parts);
            if ((parts.size() & 1) == 0) {
                throw new IllegalArgumentException("Fill parts must alternate content/separator/.../content (odd length)");
            }
            for (var i = 1; i < parts.size(); i += 2) {
                final var sep = parts.get(i);
                if (!(sep instanceof Line) && !(sep instanceof SoftLine)) {
                    throw new IllegalArgumentException("Fill separators must be Line or SoftLine, got: " + sep.getClass().getSimpleName());
                }
            }
        }
    }
}
