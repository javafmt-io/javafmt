package io.github.jschneidereit.grind.ir;

import java.util.List;
import java.util.Objects;

public sealed interface Doc
    permits Doc.Text, Doc.Line, Doc.SoftLine, Doc.HardLine,
    Doc.Indent, Doc.Group, Doc.Concat, Doc.IfBreak {

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
    }

    record IfBreak(Doc breakContents, Doc flatContents) implements Doc {
        public IfBreak {
            Objects.requireNonNull(breakContents, "breakContents");
            Objects.requireNonNull(flatContents, "flatContents");
        }
    }
}
