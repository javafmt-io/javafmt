package io.javafmt.lint;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import io.javafmt.parser.ChainPathScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.ParsedUnit;
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
 *
 * <p>An interface field group is left alone until {@link MultipleVariableDeclarations} has split
 * it. Every declarator is its own member carrying the same modifier list, so removing a modifier
 * in place emitted the same deletion once per declarator, and {@link LintEngine} cannot apply that.
 */
public final class RedundantModifier implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.edits(
                ctx,
                edits -> new ChainPathScanner<>() {

                    @Override
                    public Void visitClass(final ClassTree node, final Void p) {
                        final var path = getCurrentPath();
                        checkNestedTypeModifiers(node, path, unit, edits);
                        checkClassMemberModifiers(node, unit, edits);
                        return super.visitClass(node, null);
                    }
                });
    }

    private static void checkNestedTypeModifiers(final ClassTree node, final TreePath path, final ParsedUnit unit, final List<LintEdit> edits) {
        if (path.getParentPath() == null || !(path.getParentPath().getLeaf() instanceof ClassTree)) {
            return;
        }
        final var kind = node.getKind();
        if (kind == Tree.Kind.INTERFACE || kind == Tree.Kind.ENUM) {
            removeModifier(node.getModifiers(), Modifier.STATIC, unit).ifPresent(edits::add);
        }
    }

    private static void checkClassMemberModifiers(final ClassTree classNode, final ParsedUnit unit, final List<LintEdit> edits) {
        final var isInterface = classNode.getKind() == Tree.Kind.INTERFACE;
        final var isFinalClass = classNode.getKind() == Tree.Kind.CLASS && classNode.getModifiers().getFlags().contains(Modifier.FINAL);
        for (var member : classNode.getMembers()) {
            if (member instanceof MethodTree method) {
                if (isInterface) {
                    checkInterfaceMethodModifiers(method, unit, edits);
                } else if (isFinalClass) {
                    if (method.getReturnType() != null) {
                        removeModifier(method.getModifiers(), Modifier.FINAL, unit).ifPresent(edits::add);
                    }
                }
            } else if (member instanceof VariableTree field
                && isInterface
                && !MultipleVariableDeclarations.isPartOfDeclarationGroup(classNode.getMembers(), field, unit)) {
                checkInterfaceFieldModifiers(field, unit, edits);
            }
        }
    }

    private static void checkInterfaceMethodModifiers(final MethodTree method, final ParsedUnit unit, final List<LintEdit> edits) {
        final var flags = method.getModifiers().getFlags();
        removeModifier(method.getModifiers(), Modifier.PUBLIC, unit).ifPresent(edits::add);
        if (!flags.contains(Modifier.DEFAULT) && !flags.contains(Modifier.STATIC)) {
            removeModifier(method.getModifiers(), Modifier.ABSTRACT, unit).ifPresent(edits::add);
        }
    }

    private static void checkInterfaceFieldModifiers(final VariableTree field, final ParsedUnit unit, final List<LintEdit> edits) {
        removeModifier(field.getModifiers(), Modifier.PUBLIC, unit).ifPresent(edits::add);
        removeModifier(field.getModifiers(), Modifier.STATIC, unit).ifPresent(edits::add);
        removeModifier(field.getModifiers(), Modifier.FINAL, unit).ifPresent(edits::add);
    }

    private static Optional<LintEdit> removeModifier(final ModifiersTree modifiers, final Modifier mod, final ParsedUnit unit) {
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
            final var commentEnd = CommentScanner.skipComment(src, pos);
            if (commentEnd >= 0) {
                pos = commentEnd;
                continue;
            }
            if (src.charAt(pos) == '@') {
                pos = Modifiers.skipAnnotation(src, pos + 1);
                continue;
            }
            final var kwEnd = Modifiers.skipModifierKeyword(src, pos, kw);
            if (kwEnd >= 0) {
                var deleteEnd = kwEnd;
                if (deleteEnd < src.length() && src.charAt(deleteEnd) == ' ') {
                    deleteEnd++;
                }
                return Optional.of(new LintEdit(pos, deleteEnd, ""));
            }
            final var identifierEnd = skipIdentifier(src, pos);
            if (identifierEnd == pos) {
                return Optional.empty();
            }
            pos = identifierEnd;
        }
        return Optional.empty();
    }

    /**
     * Every branch of the scan in {@link #removeModifier} must move {@code pos} forward or leave the
     * loop, or a character it does not recognise spins the scan at 100% CPU forever. Returning
     * {@code from} unchanged here is the one case that cannot advance, and the caller gives up on it:
     * nothing that starts with a character which is neither whitespace, a comment, an annotation nor
     * an identifier belongs to a modifier list, so the scan has already run past the modifiers.
     */
    private static int skipIdentifier(final String src, final int from) {
        var pos = from;
        while (pos < src.length() && Character.isJavaIdentifierPart(src.charAt(pos))) {
            pos++;
        }
        return pos;
    }
}
