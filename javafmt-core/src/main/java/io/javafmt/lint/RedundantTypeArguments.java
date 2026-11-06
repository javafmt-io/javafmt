package io.javafmt.lint;

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
import io.javafmt.parser.AttributedUnit;
import io.javafmt.parser.Attribution;
import io.javafmt.parser.ChainPathScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.JavaParser;
import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.type.TypeKind;

/**
 * Drops explicit type arguments that the Java compiler would infer anyway.
 *
 * <p>Fast path (always-on): {@code new Foo<T>(...)} becomes {@code new Foo<>()} when the
 * surrounding context syntactically pins {@code Foo<T>} as the target type — variable
 * declarations and field initializers in v1. Detection is textual: the constructor's type
 * arguments must be identical to the target's once spacing and comments are set aside, so
 * widening, wildcards, and mismatched type arguments are all left alone.
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
public final class RedundantTypeArguments implements RewriteVerifyingRule {

    @Override
    public LintResult apply(final LintContext context) {
        final var edits = new ArrayList<LintEdit>();
        final var witnessCalls = new ArrayList<WitnessCall>();
        scan(context, edits, witnessCalls);
        context.attributionFor(RedundantTypeArguments.class).ifPresent(a -> dropRedundantWitnesses(context, a, witnessCalls, edits));
        return LintResult.ofEdits(edits);
    }

    @Override
    public Optional<String> rewriteToVerify(final LintContext context) {
        if (context.attributionFor(RedundantTypeArguments.class).isEmpty()) {
            return Optional.empty();
        }
        final var witnessCalls = new ArrayList<WitnessCall>();
        scan(context, new ArrayList<>(), witnessCalls);
        return witnessCalls.isEmpty() ? Optional.empty() : Optional.of(stripAllWitnesses(context.unit().source(), sortedByWitness(witnessCalls)));
    }

    private static void scan(final LintContext context, final List<LintEdit> edits, final List<WitnessCall> witnessCalls) {
        final var unit = context.unit();
        new ChainPathScanner<Void, Void>() {
            @Override
            public Void visitNewClass(final NewClassTree node, final Void p) {
                tryDiamondify(node, getCurrentPath(), context).ifPresent(edits::add);
                return super.visitNewClass(node, null);
            }

            @Override
            protected void visitInvocation(final MethodInvocationTree node, final Void p) {
                if (!isInitializerOfALocalBecomingVar(node, getCurrentPath(), context)) {
                    witnessOf(node, getCurrentPath(), unit).ifPresent(witnessCalls::add);
                }
            }
        }.scan(new TreePath(unit.tree()), null);
    }

    private static List<WitnessCall> sortedByWitness(final List<WitnessCall> candidates) {
        return candidates.stream().sorted(Comparator.comparingInt(WitnessCall::witnessStart)).toList();
    }

    @Override
    public boolean usesAnalysis() {
        return true;
    }

    private static Optional<LintEdit> tryDiamondify(final NewClassTree node, final TreePath path, final LintContext context) {
        final var unit = context.unit();
        if (!(node.getIdentifier() instanceof ParameterizedTypeTree pt) || pt.getTypeArguments().isEmpty()) {
            return Optional.empty();
        }
        final var target = findTargetTypeArguments(path, node, context);
        if (target.isEmpty() || !typeArgumentsMatch(pt.getTypeArguments(), target.get())) {
            return Optional.empty();
        }
        return diamondEdit(pt, unit);
    }

    private static boolean isInitializerOfALocalBecomingVar(final MethodInvocationTree call, final TreePath path, final LintContext context) {
        final var parent = path.getParentPath();
        return parent != null
            && parent.getLeaf() instanceof VariableTree variable
            && variable.getInitializer() == call
            && LocalVarUseVar.rewritesInitializer(parent, context);
    }

    private static Optional<List<? extends Tree>> findTargetTypeArguments(final TreePath path, final NewClassTree node, final LintContext context) {
        final var parent = path.getParentPath();
        if (parent == null) {
            return Optional.empty();
        }
        if (parent.getLeaf() instanceof VariableTree var
            && var.getInitializer() == node
            && Declarations.writesItsType(var, context.unit())
            && var.getType() instanceof ParameterizedTypeTree targetPt) {
            if (LocalVarUseVar.rewritesInitializer(parent, context)) {
                return Optional.empty();
            }
            return Optional.of(targetPt.getTypeArguments());
        }
        if (parent.getLeaf() instanceof ReturnTree returnTree && returnTree.getExpression() == node) {
            return enclosingMethodReturnTypeArguments(parent);
        }
        return Optional.empty();
    }

    /**
     * Walks up the path looking for the {@link MethodTree} this {@code return} belongs to,
     * stopping at a {@link LambdaExpressionTree} since a lambda's return type can't be
     * read syntactically without attribution.
     */
    private static Optional<List<? extends Tree>> enclosingMethodReturnTypeArguments(final TreePath returnPath) {
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

    private static boolean typeArgumentsMatch(final List<? extends Tree> constructorArguments, final List<? extends Tree> targetArguments) {
        return constructorArguments.size() == targetArguments.size()
            && IntStream.range(0, constructorArguments.size())
                    .allMatch(i -> constructorArguments.get(i).toString().equals(targetArguments.get(i).toString()));
    }

    private static Optional<LintEdit> diamondEdit(final ParameterizedTypeTree pt, final ParsedUnit unit) {
        final var src = unit.source();
        final var rawEnd = unit.endOf(pt.getType());
        final var typeEnd = unit.endOf(pt);
        if (rawEnd < 0 || typeEnd <= rawEnd || typeEnd > src.length()) {
            return Optional.empty();
        }
        var ltPos = rawEnd;
        while (ltPos < src.length() && Character.isWhitespace(src.charAt(ltPos))) {
            ltPos++;
        }
        if (ltPos >= src.length() || src.charAt(ltPos) != '<' || !CommentScanner.scan(src.substring(ltPos, typeEnd)).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LintEdit(ltPos, typeEnd, "<>"));
    }

    private static Optional<WitnessCall> witnessOf(final MethodInvocationTree node, final TreePath path, final ParsedUnit unit) {
        final var typeArguments = node.getTypeArguments();
        if (typeArguments.isEmpty()) {
            return Optional.empty();
        }
        final var src = unit.source();
        final var firstArgumentStart = unit.startOf(typeArguments.getFirst());
        final var lastArgumentEnd = unit.endOf(typeArguments.getLast());
        if (firstArgumentStart < 0 || lastArgumentEnd < 0 || lastArgumentEnd > src.length()) {
            return Optional.empty();
        }
        var lt = firstArgumentStart - 1;
        while (lt >= 0 && Character.isWhitespace(src.charAt(lt))) {
            lt--;
        }
        if (lt < 0 || src.charAt(lt) != '<') {
            return Optional.empty();
        }
        var gt = lastArgumentEnd;
        while (gt < src.length() && Character.isWhitespace(src.charAt(gt))) {
            gt++;
        }
        if (gt >= src.length()
            || src.charAt(gt) != '>'
            || src.substring(lt, gt).indexOf('@') >= 0
            || !CommentScanner.scan(src.substring(lt, gt)).isEmpty()) {
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
        final var selectEnd = unit.endOf(select);
        return selectEnd < 0 ? -1 : selectEnd - select.getIdentifier().length();
    }

    /**
     * Strips every candidate witness in one pass, re-attributes once, then decides each
     * candidate against the shared re-attributed unit. Cost is one extra
     * {@link JavaParser#reattribute} per file regardless of candidate count — the
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
    private static void dropRedundantWitnesses(final LintContext context,
                                               final Attribution attribution,
                                               final List<WitnessCall> candidates,
                                               final List<LintEdit> edits) {
        if (candidates.isEmpty()) {
            return;
        }
        final var unit = context.unit();
        final var sorted = sortedByWitness(candidates);
        final var originalTypeStrings = sorted.stream()
                .map(c -> attribution.trees().getTypeMirror(c.path()))
                .map(t -> t == null || t.getKind() == TypeKind.ERROR ? null : t.toString())
                .toList();
        final var modifiedSource = stripAllWitnesses(unit.source(), sorted);
        final AttributedUnit reattributed;
        try {
            reattributed = context.reattributed(modifiedSource, attribution);
        } catch (final RuntimeException e) {
            if (attribution.session().hasEnded()) {
                throw e;
            }
            return;
        }
        final var callsByNameStart = callsByMethodNameStart(reattributed.unit());
        IntStream.range(0, sorted.size())
                .filter(i -> originalTypeStrings.get(i) != null)
                .forEach(i -> tryEmitDrop(sorted, i, originalTypeStrings.get(i), reattributed, callsByNameStart, edits));
    }

    private static String stripAllWitnesses(final String src, final List<WitnessCall> sorted) {
        final var segmentEdges = Stream.concat(Stream.of(0), sorted.stream().flatMap(c -> Stream.of(c.witnessStart(), c.witnessEnd()))).toList();
        return IntStream.range(0, segmentEdges.size() / 2 + 1)
                .mapToObj(i -> src.substring(segmentEdges.get(2 * i), 2 * i + 1 < segmentEdges.size() ? segmentEdges.get(2 * i + 1) : src.length()))
                .collect(Collectors.joining());
    }

    private static void tryEmitDrop(final List<WitnessCall> sorted,
                                    final int index,
                                    final String originalTypeString,
                                    final AttributedUnit reattributed,
                                    final Map<Integer, TreePath> callsByNameStart,
                                    final List<LintEdit> edits) {
        final var candidate = sorted.get(index);
        final var translatedNameStart = candidate.methodNameStart() - strippedLengthBefore(sorted, candidate.methodNameStart());
        final var newPath = callsByNameStart.get(translatedNameStart);
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
        return sorted.stream().filter(c -> c.witnessStart() < offset).mapToInt(c -> c.witnessEnd() - c.witnessStart()).sum();
    }

    private static Map<Integer, TreePath> callsByMethodNameStart(final ParsedUnit unit) {
        final var calls = new ArrayList<TreePath>();
        new ChainPathScanner<Void, Void>() {
            @Override
            protected void visitInvocation(final MethodInvocationTree node, final Void p) {
                calls.add(getCurrentPath());
            }
        }.scan(new TreePath(unit.tree()), null);
        return calls.stream()
                .collect(Collectors.toMap(path -> methodNameStart((MethodInvocationTree) path.getLeaf(), unit), path -> path, (first, _) -> first));
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
