package io.javafmt.lint;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.lang.model.element.Modifier;

/**
 * Removes modifiers that are redundant per the Java spec:
 * <ul>
 *   <li>{@code public} and {@code abstract} on non-default, non-static interface methods</li>
 *   <li>{@code public} on default and static interface methods</li>
 *   <li>{@code public}, {@code static}, {@code final} on interface fields</li>
 *   <li>{@code static} on nested interfaces and enums (always implicitly static)</li>
 *   <li>{@code final} on a method in a {@code final} class</li>
 * </ul>
 */
public final class RedundantModifier implements LintRule {

    @Override
    public String name() {
        return "RedundantModifier";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var edits = new ArrayList<LintEdit>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitClass(final ClassTree node, final Void p) {
                final var path = getCurrentPath();
                checkNestedTypeModifiers(node, path, unit, edits);
                checkClassMemberModifiers(node, unit, edits);
                return super.visitClass(node, null);
            }
        }.scan(new TreePath(unit.tree()), null);
        return LintResult.ofEdits(edits);
    }

    private static void checkNestedTypeModifiers(
            final ClassTree node,
            final TreePath path,
            final ParsedUnit unit,
            final List<LintEdit> edits) {
        if (path.getParentPath() == null
                || !(path.getParentPath().getLeaf() instanceof ClassTree)) {
            return;
        }
        // Nested interface or enum: 'static' is implicitly true
        final var kind = node.getKind();
        if (kind == Tree.Kind.INTERFACE || kind == Tree.Kind.ENUM) {
            removeModifier(node.getModifiers(), Modifier.STATIC, unit).ifPresent(edits::add);
        }
    }

    private static void checkClassMemberModifiers(
            final ClassTree classNode,
            final ParsedUnit unit,
            final List<LintEdit> edits) {
        final var isInterface = classNode.getKind() == Tree.Kind.INTERFACE;
        final var isFinalClass = classNode.getKind() == Tree.Kind.CLASS
            && classNode.getModifiers().getFlags().contains(Modifier.FINAL);

        for (final var member : classNode.getMembers()) {
            if (member instanceof MethodTree method) {
                if (isInterface) {
                    checkInterfaceMethodModifiers(method, unit, edits);
                } else if (isFinalClass) {
                    // 'final' on a method in a final class is redundant
                    if (method.getReturnType() != null) { // skip constructors
                        removeModifier(method.getModifiers(), Modifier.FINAL, unit)
                            .ifPresent(edits::add);
                    }
                }
            } else if (member instanceof VariableTree field && isInterface) {
                checkInterfaceFieldModifiers(field, unit, edits);
            }
        }
    }

    private static void checkInterfaceMethodModifiers(
            final MethodTree method, final ParsedUnit unit, final List<LintEdit> edits) {
        final var flags = method.getModifiers().getFlags();
        // 'public' is redundant on all interface methods
        removeModifier(method.getModifiers(), Modifier.PUBLIC, unit).ifPresent(edits::add);
        // 'abstract' is redundant only on non-default, non-static methods
        if (!flags.contains(Modifier.DEFAULT) && !flags.contains(Modifier.STATIC)) {
            removeModifier(method.getModifiers(), Modifier.ABSTRACT, unit).ifPresent(edits::add);
        }
    }

    private static void checkInterfaceFieldModifiers(
            final VariableTree field, final ParsedUnit unit, final List<LintEdit> edits) {
        removeModifier(field.getModifiers(), Modifier.PUBLIC, unit).ifPresent(edits::add);
        removeModifier(field.getModifiers(), Modifier.STATIC, unit).ifPresent(edits::add);
        removeModifier(field.getModifiers(), Modifier.FINAL, unit).ifPresent(edits::add);
    }

    private static Optional<LintEdit> removeModifier(
            final ModifiersTree modifiers,
            final Modifier mod,
            final ParsedUnit unit) {
        if (!modifiers.getFlags().contains(mod)) {
            return Optional.empty();
        }
        final var modStart = (int) unit.sourcePositions().getStartPosition(unit.tree(), modifiers);
        if (modStart < 0) {
            return Optional.empty();
        }
        final var kw = mod.toString();
        final var src = unit.source();
        var pos = modStart;

        while (pos < src.length()) {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
            if (pos >= src.length()) {
                break;
            }
            final var ch = src.charAt(pos);
            if (ch == '@') {
                pos = skipAnnotation(src, pos + 1);
                continue;
            }
            if (src.startsWith(kw, pos)) {
                final var kwEnd = pos + kw.length();
                if (kwEnd >= src.length() || !Character.isJavaIdentifierPart(src.charAt(kwEnd))) {
                    var deleteEnd = kwEnd;
                    if (deleteEnd < src.length() && src.charAt(deleteEnd) == ' ') {
                        deleteEnd++;
                    }
                    return Optional.of(new LintEdit(pos, deleteEnd, ""));
                }
            }
            // skip this word
            while (pos < src.length() && Character.isJavaIdentifierPart(src.charAt(pos))) {
                pos++;
            }
        }
        return Optional.empty();
    }

    private static int skipAnnotation(final String src, final int afterAt) {
        var pos = afterAt;
        while (pos < src.length() && Character.isJavaIdentifierPart(src.charAt(pos))) {
            pos++;
        }
        if (pos < src.length() && src.charAt(pos) == '(') {
            var depth = 0;
            while (pos < src.length()) {
                final var c = src.charAt(pos++);
                if (c == '(') {
                    depth++;
                } else if (c == ')' && --depth == 0) {
                    break;
                }
            }
        }
        return pos;
    }
}
