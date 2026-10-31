package io.javafmt.lint;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import io.javafmt.parser.AttributedUnit;
import io.javafmt.parser.Attribution;
import io.javafmt.parser.AttributionError;
import io.javafmt.parser.ChainPathScanner;
import io.javafmt.parser.JavaParser;
import io.javafmt.parser.ParsedUnit;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

@UtilityClass
class VarRetypingCheck {

    private final Pattern CAPTURE_NUMBER = Pattern.compile("capture#\\d+");

    List<LocalVarUseVar.Proposal> verified(final ParsedUnit unit,
                                           final Attribution attribution,
                                           final List<LocalVarUseVar.Proposal> proven,
                                           final List<LocalVarUseVar.Proposal> unproven) {
        final var accepted = accepted(unit, attribution, proven, unproven);
        return unproven.stream().filter(proposal -> proposal.keepsItsTypeWithoutTarget() || accepted.contains(proposal)).toList();
    }

    private List<LocalVarUseVar.Proposal> accepted(final ParsedUnit unit,
                                                   final Attribution attribution,
                                                   final List<LocalVarUseVar.Proposal> proven,
                                                   final List<LocalVarUseVar.Proposal> unproven) {
        final var edits = Stream.concat(proven.stream(), unproven.stream()).flatMap(proposal -> proposal.edits().stream()).toList();
        final AttributedUnit rewritten;
        try {
            rewritten = JavaParser.reattribute(LintEngine.applyEdits(unit.source(), edits), attribution);
        } catch (final RuntimeException e) {
            if (attribution.session().hasEnded()) {
                throw e;
            }
            return List.of();
        }
        final var shift = new Shift(edits);
        final var failures = failures(unit, attribution, rewritten, shift, Stream.concat(proven.stream(), unproven.stream()).toList(), unproven);
        if (failures.isEmpty()) {
            return unproven;
        }
        if (failures.contains(-1L)) {
            return List.of();
        }
        final var rejected = unproven.stream().filter(proposal -> failures.stream().anyMatch(f -> inScope(proposal, f, unit, shift))).toList();
        return unproven.stream()
                .filter(proposal -> !rejected.contains(proposal))
                .filter(proposal -> rejected.stream().noneMatch(outer -> inScope(outer, shift.start(unit.startOf(proposal.variable())), unit, shift)))
                .toList();
    }

    private boolean inScope(final LocalVarUseVar.Proposal proposal, final long offset, final ParsedUnit unit, final Shift shift) {
        return offset >= shift.start(unit.startOf(proposal.scope())) && offset < shift.end(unit.endOf(proposal.scope()));
    }

    private Set<Long> failures(final ParsedUnit unit,
                               final Attribution attribution,
                               final AttributedUnit rewritten,
                               final Shift shift,
                               final List<LocalVarUseVar.Proposal> proposals,
                               final List<LocalVarUseVar.Proposal> unproven) {
        final var knownErrors = attribution.errorsIn(unit.tree())
                .stream()
                .map(error -> new AttributionError(error.offset() < 0 ? -1 : shift.start((int) error.offset()), normalized(error.message())))
                .collect(Collectors.toSet());
        final var newErrors = rewritten.attribution()
                .errorsIn(rewritten.unit().tree())
                .stream()
                .map(error -> new AttributionError(error.offset() < 0 ? -1 : error.offset(), normalized(error.message())))
                .filter(Predicate.not(knownErrors::contains))
                .map(AttributionError::offset);
        final var retypedVariables = proposals.stream()
                .map(proposal -> attribution.trees().getElement(proposal.path()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> Collections.newSetFromMap(new IdentityHashMap<>())));
        final var retypedDeclarations = proposals.stream()
                .map(LocalVarUseVar.Proposal::variable)
                .collect(Collectors.toCollection(() -> Collections.<Tree>newSetFromMap(new IdentityHashMap<>())));
        final var after = expressionsOf(rewritten.unit(), Set.of())
                .stream()
                .collect(Collectors.toMap(path -> key(path.getLeaf(), rewritten.unit()), path -> path, (first, _) -> first));
        final var changed = expressionsOf(unit, retypedDeclarations)
                .stream()
                .filter(path -> unproven.stream().anyMatch(proposal -> inScope(proposal, shift.start(unit.startOf(path.getLeaf())), unit, shift)))
                .filter(
                        path -> !matches(
                                path, after.get(shiftedKey(path.getLeaf(), unit, shift)), attribution, rewritten.attribution(), retypedVariables))
                .map(path -> (long) shift.start(unit.startOf(path.getLeaf())));
        return Stream.concat(newErrors, changed).collect(Collectors.toSet());
    }

    private boolean matches(final TreePath before,
                            @Nullable final TreePath after,
                            final Attribution beforeAttribution,
                            final Attribution afterAttribution,
                            final Set<Element> retypedVariables) {
        if (after == null) {
            return false;
        }
        final var beforeElement = beforeAttribution.trees().getElement(before);
        final var afterElement = afterAttribution.trees().getElement(after);
        final var isRetypedReference = withoutParentheses(before.getLeaf()) instanceof IdentifierTree
            && beforeElement != null
            && retypedVariables.contains(beforeElement);
        return (isRetypedReference
            || typeName(beforeAttribution.trees().getTypeMirror(before)).equals(typeName(afterAttribution.trees().getTypeMirror(after))))
            && sameSymbol(beforeElement, afterElement, afterAttribution);
    }

    private Tree withoutParentheses(final Tree tree) {
        return tree instanceof ParenthesizedTree parenthesized ? withoutParentheses(parenthesized.getExpression()) : tree;
    }

    private boolean sameSymbol(@Nullable final Element before, @Nullable final Element after, final Attribution afterAttribution) {
        if (before == null || after == null) {
            return before == null && after == null;
        }
        return symbolName(before).equals(symbolName(after)) || overrides(after, before, afterAttribution);
    }

    private boolean overrides(final Element after, final Element before, final Attribution afterAttribution) {
        if (!(after instanceof ExecutableElement overrider)
            || !(before instanceof ExecutableElement overridden)
            || overrider.getKind() != ElementKind.METHOD
            || overridden.getModifiers().contains(Modifier.STATIC)
            || !(overrider.getEnclosingElement() instanceof TypeElement overriderOwner)
            || !(overridden.getEnclosingElement() instanceof TypeElement overriddenOwner)
            || overriddenOwner.getQualifiedName().isEmpty()) {
            return false;
        }
        final var ownerAfter = afterAttribution.elements().getTypeElement(overriddenOwner.getQualifiedName());
        return ownerAfter != null
            && ElementFilter.methodsIn(ownerAfter.getEnclosedElements())
                    .stream()
                    .filter(method -> method.toString().equals(overridden.toString()))
                    .anyMatch(method -> afterAttribution.elements().overrides(overrider, method, overriderOwner));
    }

    private String symbolName(final Element element) {
        return element.getKind() + " " + element.getEnclosingElement() + " " + element;
    }

    private String typeName(@Nullable final TypeMirror type) {
        return type == null ? "" : normalized(type.toString());
    }

    private String normalized(final String text) {
        return CAPTURE_NUMBER.matcher(text).replaceAll("capture#");
    }

    private Key key(final Tree tree, final ParsedUnit unit) {
        return new Key(tree.getKind(), unit.startOf(tree), unit.endOf(tree));
    }

    private Key shiftedKey(final Tree tree, final ParsedUnit unit, final Shift shift) {
        return new Key(tree.getKind(), shift.start(unit.startOf(tree)), shift.end(unit.endOf(tree)));
    }

    private List<TreePath> expressionsOf(final ParsedUnit unit, final Set<Tree> retypedDeclarations) {
        final var found = new ArrayList<TreePath>();
        new ChainPathScanner<Void, Void>() {

            @Override
            public Void scan(@Nullable final Tree tree, @Nullable final Void p) {
                if (tree instanceof ExpressionTree) {
                    final var path = new TreePath(getCurrentPath(), tree);
                    found.add(path);
                    addSelectLinks(path);
                }
                return super.scan(tree, null);
            }

            @Override
            public Void visitVariable(final VariableTree node, final Void p) {
                if (!retypedDeclarations.contains(node)) {
                    return super.visitVariable(node, null);
                }
                scan(node.getModifiers(), null);
                return scan(node.getInitializer(), null);
            }

            @Override
            protected void visitInvocation(final MethodInvocationTree node, final Void p) {
                found.add(getCurrentPath());
                addSelectLinks(getCurrentPath());
            }

            @Override
            protected void visitBinaryOperator(final BinaryTree node, final Void p) {
                found.add(getCurrentPath());
            }

            private void addSelectLinks(final TreePath path) {
                final var inner = switch (path.getLeaf()) {
                    case MethodInvocationTree call -> call.getMethodSelect();
                    case MemberSelectTree select -> select.getExpression();
                    default -> null;
                };
                if (inner instanceof MemberSelectTree select) {
                    final var selectPath = new TreePath(path, select);
                    found.add(selectPath);
                    addSelectLinks(selectPath);
                }
            }
        }.scan(new TreePath(unit.tree()), null);
        return List.copyOf(found);
    }

    private record Key(Tree.Kind kind, long start, long end) {}

    private record Shift(List<LintEdit> edits) {

        int start(final int offset) {
            return offset
                    + edits.stream()
                            .filter(edit -> isInsertion(edit) ? edit.start() <= offset : edit.end() <= offset)
                            .mapToInt(Shift::delta)
                            .sum();
        }

        int end(final int offset) {
            return offset
                    + edits.stream()
                            .filter(edit -> isInsertion(edit) ? edit.start() < offset : edit.end() <= offset)
                            .mapToInt(Shift::delta)
                            .sum();
        }

        private static boolean isInsertion(final LintEdit edit) {
            return edit.start() == edit.end();
        }

        private static int delta(final LintEdit edit) {
            return edit.replacement().length() - (edit.end() - edit.start());
        }
    }
}
