package io.javafmt.lint;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;

import org.jspecify.annotations.Nullable;

/**
 * Enforces the Java Language Specification modifier order:
 * {@code public protected private abstract default static final transient volatile
 * synchronized native strictfp}.
 *
 * <p>Skips declarations with annotations in their modifier list because reordering
 * keyword modifiers around annotations would require moving annotation nodes, which
 * is outside the scope of a text-edit rule.
 */
public final class ModifierOrder implements LintRule {

    private static final List<Modifier> CANONICAL = List.of(
        Modifier.PUBLIC, Modifier.PROTECTED, Modifier.PRIVATE,
        Modifier.ABSTRACT, Modifier.DEFAULT, Modifier.STATIC, Modifier.FINAL,
        Modifier.TRANSIENT, Modifier.VOLATILE, Modifier.SYNCHRONIZED,
        Modifier.NATIVE, Modifier.STRICTFP
    );

    @Override
    public String name() {
        return "ModifierOrder";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var edits = new ArrayList<LintEdit>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitClass(final ClassTree node, final Void p) {
                checkModifiers(node.getModifiers(), unit).ifPresent(edits::add);
                return super.visitClass(node, null);
            }

            @Override
            public Void visitMethod(final MethodTree node, final Void p) {
                checkModifiers(node.getModifiers(), unit).ifPresent(edits::add);
                return super.visitMethod(node, null);
            }

            @Override
            public Void visitVariable(final VariableTree node, final Void p) {
                checkModifiers(node.getModifiers(), unit).ifPresent(edits::add);
                return super.visitVariable(node, null);
            }
        }.scan(new TreePath(unit.tree()), null);
        return LintResult.ofEdits(edits);
    }

    private static Optional<LintEdit> checkModifiers(
            final ModifiersTree modifiers, final ParsedUnit unit) {
        final var flags = modifiers.getFlags();
        if (flags.size() < 2) {
            return Optional.empty();
        }
        // Annotations complicate reordering — skip to stay safe
        if (!modifiers.getAnnotations().isEmpty()) {
            return Optional.empty();
        }
        final var modStart = (int) unit.sourcePositions().getStartPosition(unit.tree(), modifiers);
        if (modStart < 0) {
            return Optional.empty();
        }
        final var src = unit.source();
        final var sourceOrder = new ArrayList<Modifier>();
        var firstKwStart = -1;
        var lastKwEnd = -1;
        var pos = modStart;

        while (pos < src.length()) {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
            if (pos >= src.length()) {
                break;
            }
            final var matched = matchModifier(src, pos, flags);
            if (matched == null) {
                break;
            }
            if (firstKwStart < 0) {
                firstKwStart = pos;
            }
            pos += matched.toString().length();
            lastKwEnd = pos;
            sourceOrder.add(matched);
        }

        if (sourceOrder.size() < 2) {
            return Optional.empty();
        }
        final var canonicalOrder = CANONICAL.stream().filter(flags::contains).toList();
        if (sourceOrder.equals(canonicalOrder)) {
            return Optional.empty();
        }
        final var replacement = canonicalOrder.stream()
            .map(Modifier::toString)
            .collect(Collectors.joining(" "));
        return Optional.of(new LintEdit(firstKwStart, lastKwEnd, replacement));
    }

    private static @Nullable Modifier matchModifier(
            final String src, final int pos, final java.util.Set<Modifier> flags) {
        for (final var mod : CANONICAL) {
            if (!flags.contains(mod)) {
                continue;
            }
            final var kw = mod.toString();
            if (!src.startsWith(kw, pos)) {
                continue;
            }
            final var kwEnd = pos + kw.length();
            if (kwEnd < src.length() && Character.isJavaIdentifierPart(src.charAt(kwEnd))) {
                continue;
            }
            return mod;
        }
        return null;
    }
}
