package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.javafmt.parser.AttributedUnit;
import io.javafmt.parser.Attribution;
import io.javafmt.parser.JavaParser;
import io.javafmt.parser.ParsedUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.type.TypeKind;
import org.jspecify.annotations.Nullable;

/**
 * Drops explicit type arguments that the Java compiler would infer anyway.
 *
 * <p>Fast path (always-on): {@code new Foo<T>(...)} becomes {@code new Foo<>()} when the
 * surrounding context syntactically pins {@code Foo<T>} as the target type — variable
 * declarations and field initializers in v1. Detection is textual: the constructor's type
 * arguments must be identical to the target's, so widening, wildcards, and mismatched type
 * arguments are all left alone.
 *
 * <p>Analyze path (when {@link LintContext#attribution()} is present): also drops type
 * witnesses on generic method calls such as {@code Stream.<T>of(...)} when removing the
 * witness leaves javac inferring the same type. Attribution is required to compute the
 * inferred type both with and without the witness; without it the rule cannot prove the
 * rewrite is safe and skips the call.
 *
 * <p><b>Anonymous classes.</b> {@code new Comparator<String>() {…}} becomes
 * {@code new Comparator<>() {…}}, which Java has allowed since 9 but only where the inferred type
 * is denotable (JLS 15.9.1). The textual-match requirement above is what makes that safe: the rule
 * fires only when the target type's arguments are written identically to the constructor's, so the
 * type javac infers is one the author already wrote down and can therefore be named. The shapes
 * where inference would produce something unnameable — an intersection, a captured wildcard — reach
 * the diamond only with no target type to match against, which is to say through {@code var} or an
 * argument position, and neither is a context this rule looks at. {@code LintCompileSafetyTest}
 * holds that argument to account.
 */
public final class RedundantTypeArguments implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        final var attribution = ctx.attribution();
        final var edits = new ArrayList<LintEdit>();
        final var witnessCalls = new ArrayList<WitnessCall>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitNewClass(final NewClassTree node, final Void p) {
                tryDiamondify(node, getCurrentPath(), unit, attribution).ifPresent(edits::add);
                return super.visitNewClass(node, null);
            }

            @Override
            public Void visitMethodInvocation(final MethodInvocationTree node, final Void p) {
                witnessOf(node, getCurrentPath(), unit).ifPresent(witnessCalls::add);
                return super.visitMethodInvocation(node, null);
            }
        }.scan(new TreePath(unit.tree()), null);
        attribution.ifPresent(a -> dropRedundantWitnesses(unit, a, witnessCalls, edits));
        return LintResult.ofEdits(edits);
    }

    private static Optional<LintEdit> tryDiamondify(
            final NewClassTree node,
            final TreePath path,
            final ParsedUnit unit,
            final Optional<Attribution> attribution) {
        if (!(node.getIdentifier() instanceof ParameterizedTypeTree pt) || pt.getTypeArguments().isEmpty()) {
            return Optional.empty();
        }
        final var target = findTargetTypeArgs(path, node, unit, attribution);
        if (target.isEmpty() || !typeArgsMatch(pt.getTypeArguments(), target.get(), unit)) {
            return Optional.empty();
        }
        return diamondEdit(pt, unit);
    }

    private static Optional<List<? extends Tree>> findTargetTypeArgs(
            final TreePath path,
            final NewClassTree node,
            final ParsedUnit unit,
            final Optional<Attribution> attribution) {
        final var parent = path.getParentPath();
        if (parent == null) {
            return Optional.empty();
        }
        if (parent.getLeaf() instanceof VariableTree var
            && var.getInitializer() == node
            && var.getType() instanceof ParameterizedTypeTree targetPt) {
            if (localVarUseVarWillRewrite(parent, var, node, unit, attribution)) {
                return Optional.empty();
            }
            return Optional.of(targetPt.getTypeArguments());
        }
        if (parent.getLeaf() instanceof ReturnTree ret && ret.getExpression() == node) {
            return enclosingMethodReturnTypeArgs(parent);
        }
        return Optional.empty();
    }

    /**
     * Predicts whether {@link LocalVarUseVar} will rewrite this local declaration to
     * {@code var}. When both rules would fire on the same iteration, their edits compose
     * into {@code var x = new Foo<>()}, which won't compile (diamond inference has no
     * target type once the declared type is gone). Defer to {@link LocalVarUseVar} in
     * that case. Two paths fire: the narrow textual match (declared type source equals
     * constructor identifier source), and the analyze-path assignability check that also
     * rewrites widening declarations like {@code List<X> xs = new ArrayList<X>()}.
     */
    private static boolean localVarUseVarWillRewrite(
            final TreePath varPath,
            final VariableTree var,
            final NewClassTree newClass,
            final ParsedUnit unit,
            final Optional<Attribution> attribution) {
        final var grandparent = varPath.getParentPath();
        if (grandparent == null || !(grandparent.getLeaf() instanceof BlockTree)) {
            return false;
        }
        if (unit.sourceOf(var.getType())
                .trim()
                .equals(unit.sourceOf(newClass.getIdentifier()).trim())) {
            return true;
        }
        return attribution.isPresent() && LocalVarUseVar.wouldAnalyzeRewrite(var, varPath, attribution.get());
    }

    /**
     * Walks up the path looking for the {@link MethodTree} this {@code return} belongs to,
     * stopping at a {@link LambdaExpressionTree} since a lambda's return type can't be
     * read syntactically without attribution.
     */
    private static Optional<List<? extends Tree>> enclosingMethodReturnTypeArgs(final TreePath returnPath) {
        var path = returnPath.getParentPath();
        while (path != null) {
            final var leaf = path.getLeaf();
            if (leaf instanceof LambdaExpressionTree) {
                return Optional.empty();
            }
            if (leaf instanceof MethodTree method) {
                return method.getReturnType() instanceof ParameterizedTypeTree pt ? Optional.of(pt.getTypeArguments()) : Optional.empty();
            }
            path = path.getParentPath();
        }
        return Optional.empty();
    }

    private static boolean typeArgsMatch(final List<? extends Tree> ctorArgs, final List<? extends Tree> targetArgs, final ParsedUnit unit) {
        return ctorArgs.size() == targetArgs.size()
            && IntStream.range(0, ctorArgs.size())
                    .allMatch(i -> unit.sourceOf(ctorArgs.get(i)).trim().equals(unit.sourceOf(targetArgs.get(i)).trim()));
    }

    private static Optional<LintEdit> diamondEdit(final ParameterizedTypeTree pt, final ParsedUnit unit) {
        final var positions = unit.sourcePositions();
        final var root = unit.tree();
        final var src = unit.source();
        final var rawEnd = (int) positions.getEndPosition(root, pt.getType());
        final var typeEnd = (int) positions.getEndPosition(root, pt);
        if (rawEnd < 0 || typeEnd <= rawEnd || typeEnd > src.length()) {
            return Optional.empty();
        }
        var ltPos = rawEnd;
        while (ltPos < src.length() && Character.isWhitespace(src.charAt(ltPos))) {
            ltPos++;
        }
        if (ltPos >= src.length() || src.charAt(ltPos) != '<') {
            return Optional.empty();
        }
        return Optional.of(new LintEdit(ltPos, typeEnd, "<>"));
    }

    private static Optional<WitnessCall> witnessOf(final MethodInvocationTree node, final TreePath path, final ParsedUnit unit) {
        final var typeArgs = node.getTypeArguments();
        if (typeArgs.isEmpty()) {
            return Optional.empty();
        }
        final var positions = unit.sourcePositions();
        final var root = unit.tree();
        final var src = unit.source();
        final var firstArgStart = (int) positions.getStartPosition(root, typeArgs.getFirst());
        final var lastArgEnd = (int) positions.getEndPosition(root, typeArgs.getLast());
        if (firstArgStart < 0 || lastArgEnd < 0 || lastArgEnd > src.length()) {
            return Optional.empty();
        }
        var lt = firstArgStart - 1;
        while (lt >= 0 && Character.isWhitespace(src.charAt(lt))) {
            lt--;
        }
        if (lt < 0 || src.charAt(lt) != '<') {
            return Optional.empty();
        }
        var gt = lastArgEnd;
        while (gt < src.length() && Character.isWhitespace(src.charAt(gt))) {
            gt++;
        }
        if (gt >= src.length() || src.charAt(gt) != '>') {
            return Optional.empty();
        }
        final var nameStart = methodNameStart(node, unit);
        if (nameStart < 0) {
            return Optional.empty();
        }
        return Optional.of(new WitnessCall(path, nameStart, lt, gt + 1));
    }

    /**
     * Where the invoked method's simple name begins. Only a qualified call can carry a witness
     * ({@code <T>foo()} is not legal Java), so the name always follows a member select, and its
     * offset is the select's end minus the name length.
     */
    private static int methodNameStart(final MethodInvocationTree node, final ParsedUnit unit) {
        if (!(node.getMethodSelect() instanceof MemberSelectTree select)) {
            return -1;
        }
        final var selectEnd = (int) unit.sourcePositions().getEndPosition(unit.tree(), select);
        return selectEnd < 0 ? -1 : selectEnd - select.getIdentifier().length();
    }

    /**
     * Strips every candidate witness in one pass, re-attributes once, then decides each
     * candidate against the shared re-attributed unit. Cost is one extra
     * {@link JavaParser#parseAndAttribute} per file regardless of candidate count — the
     * per-candidate variant of this rule was unusable on real files (5–20+ witness sites
     * each paying ~50–200ms for a full attribution).
     *
     * <p>Cascading degradation is safe: stripping a witness whose presence was genuinely
     * needed makes that call's re-attributed type drift (to an error type or to a wider
     * inference). The string comparison sees "different" and the rule keeps the witness.
     * Behaviour is at least as conservative as the per-candidate variant.
     *
     * <p>Types from the original and re-attributed contexts can't be compared with
     * {@code Types.isSameType} (different javac sessions), so we compare
     * {@code TypeMirror.toString()} representations.
     */
    private static void dropRedundantWitnesses(
            final ParsedUnit unit,
            final Attribution attribution,
            final List<WitnessCall> candidates,
            final List<LintEdit> edits) {
        if (candidates.isEmpty()) {
            return;
        }
        final var sorted = candidates.stream()
                .sorted(Comparator.comparingInt(WitnessCall::witnessStart))
                .toList();
        final var originalTypeStrings = sorted.stream()
                .map(c -> attribution.trees().getTypeMirror(c.path()))
                .map(t -> t == null || t.getKind() == TypeKind.ERROR ? null : t.toString())
                .toList();
        final var modifiedSource = stripAllWitnesses(unit.source(), sorted);
        final AttributedUnit reattributed;
        try {
            reattributed = JavaParser.parseAndAttribute(modifiedSource, attribution.classpath());
        } catch (RuntimeException ignored) {
            return;
        }
        IntStream.range(0, sorted.size())
                .filter(i -> originalTypeStrings.get(i) != null)
                .forEach(i -> tryEmitDrop(sorted, i, originalTypeStrings.get(i), reattributed, edits));
    }

    private static String stripAllWitnesses(final String src, final List<WitnessCall> sorted) {
        final var segmentEdges = Stream.concat(Stream.of(0), sorted.stream().flatMap(c -> Stream.of(c.witnessStart(), c.witnessEnd())))
                .toList();
        return IntStream.range(0, segmentEdges.size() / 2 + 1)
                .mapToObj(i -> src.substring(segmentEdges.get(2 * i), 2 * i + 1 < segmentEdges.size() ? segmentEdges.get(2 * i + 1) : src.length()))
                .collect(Collectors.joining());
    }

    private static void tryEmitDrop(
            final List<WitnessCall> sorted,
            final int index,
            final String originalTypeString,
            final AttributedUnit reattributed,
            final List<LintEdit> edits) {
        final var candidate = sorted.get(index);
        final var translatedNameStart = candidate.methodNameStart() - strippedLengthBefore(sorted, candidate.methodNameStart());
        final var newPath = findCallByMethodNameAt(reattributed.unit(), translatedNameStart);
        if (newPath == null) {
            return;
        }
        final var newType = reattributed.attribution().trees().getTypeMirror(newPath);
        if (newType == null || newType.getKind() == TypeKind.ERROR) {
            return;
        }
        if (Objects.equals(originalTypeString, newType.toString())) {
            edits.add(new LintEdit(candidate.witnessStart(), candidate.witnessEnd(), ""));
        }
    }

    /**
     * Sum of strip lengths for every witness that lies strictly before {@code offset} in the
     * original source. Linear in candidate count (small N — typical real files have under 30),
     * intentionally direct rather than indexed.
     */
    private static int strippedLengthBefore(final List<WitnessCall> sorted, final int offset) {
        return sorted.stream()
                .filter(c -> c.witnessStart() < offset)
                .mapToInt(c -> c.witnessEnd() - c.witnessStart())
                .sum();
    }

    @Nullable
    private static TreePath findCallByMethodNameAt(final ParsedUnit unit, final int nameStart) {
        final var matches = new ArrayList<TreePath>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitMethodInvocation(final MethodInvocationTree node, final Void p) {
                if (methodNameStart(node, unit) == nameStart) {
                    matches.add(getCurrentPath());
                }
                return super.visitMethodInvocation(node, null);
            }
        }.scan(new TreePath(unit.tree()), null);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    /**
     * A method invocation that carries an explicit type witness; candidate for the analyze
     * path to test whether the witness can be dropped without changing the call's inferred
     * type. {@code witnessStart}/{@code witnessEnd} bracket {@code "<...>"} inclusive.
     *
     * <p>{@code methodNameStart} rather than the call's start offset identifies the call in the
     * re-attributed tree: a chain link and the call it wraps report the same start offset, so
     * {@code Stream.<T>of(x).count()} would be matched by the enclosing {@code count()} and
     * verified against the wrong node's type.
     */
    private record WitnessCall(TreePath path, int methodNameStart, int witnessStart, int witnessEnd) {}
}
