package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import io.javafmt.parser.Attribution;
import io.javafmt.parser.ChainPathScanner;
import io.javafmt.parser.ParsedUnit;
import java.util.List;
import java.util.stream.IntStream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;

/**
 * Replaces explicit local variable types with {@code var}. Three cases are handled:
 * <ul>
 *   <li>{@code new} expressions whose instantiated type exactly matches the declared
 *       type (no widening, no diamond inference).</li>
 *   <li>Static-factory method calls that already carry an explicit type witness matching the
 *       declared type arguments, where dropping the declared type changes nothing.</li>
 *   <li>(analyze only) Static-factory calls whose receiver textually matches the declared raw
 *       type (e.g. {@code Optional<Path> x = Optional.empty()}). The declared type arguments are
 *       lifted into a type witness on the call so the inferred type stays the same after the
 *       rewrite. This needs attribution to check the method's type-parameter count first — see
 *       {@link #witnessFitsTheMethod}.</li>
 *   <li>(analyze only) Any initializer — including method chains like
 *       {@code List<X> xs = stream.map(...).toList()} — whose javac-inferred type is exactly
 *       the declared type, so {@code var} means the same thing the author wrote. Runs only
 *       when neither narrow pattern fired, since both narrow patterns already handle
 *       their cases without needing attribution.</li>
 * </ul>
 *
 * <p>A multi-variable declaration is never rewritten: {@code var} is illegal in a compound
 * declaration, and the declarators share one type span, so the rule would emit the same edit once
 * each. {@link MultipleVariableDeclarations} splits the group and each declaration is then its own
 * candidate.
 *
 * <p>Widening declarations such as {@code List<X> xs = new ArrayList<X>()} are left alone. The
 * rewrite would silently narrow the static type the author chose, and breaks outright once the
 * local is reassigned from anything that satisfies the declared type but not the inferred one.
 * {@link RedundantTypeArguments} handles those by diamond-ifying the constructor instead.
 */
public final class LocalVarUseVar implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.edits(
                ctx,
                edits -> new ChainPathScanner<>() {

                    @Override
                    public Void visitVariable(final VariableTree node, final Void p) {
                        if (isLocalBlock(getCurrentPath())
                            && !isVarType(node, unit)
                            && !MultipleVariableDeclarations.isPartOfDeclarationGroup(getCurrentPath(), unit)
                            && !(node.getType() instanceof PrimitiveTypeTree)) {
                            final var sizeBefore = edits.size();
                            if (node.getInitializer() instanceof NewClassTree newClass && typesMatch(node, newClass, unit)) {
                                addReplaceTypeEdit(node, unit, edits);
                            } else if (node.getInitializer() instanceof MethodInvocationTree call) {
                                tryStaticFactoryRewrite(node, call, getCurrentPath(), ctx, edits);
                            }
                            if (edits.size() == sizeBefore) {
                                ctx.attribution().ifPresent(attribution -> tryAnalyzeRewrite(node, getCurrentPath(), attribution, unit, edits));
                            }
                        }
                        return super.visitVariable(node, null);
                    }
                });
    }

    /**
     * Replaces the declared type with {@code var} when javac's inferred type for the
     * initializer is exactly the declared type. Both types come from the same attribution so
     * {@link javax.lang.model.util.Types} comparisons are valid here (cross-context comparisons
     * would need string equivalence instead).
     */
    private static void tryAnalyzeRewrite(
            final VariableTree node,
            final TreePath path,
            final Attribution attribution,
            final ParsedUnit unit,
            final List<LintEdit> edits) {
        if (wouldAnalyzeRewrite(node, path, attribution)) {
            addReplaceTypeEdit(node, unit, edits);
        }
    }

    /**
     * Predicate form of the analyze-path decision. Exposed for {@link RedundantTypeArguments}
     * to coordinate: when this rule will rewrite a declaration to {@code var}, the constructor
     * inside it must keep its explicit type arguments (diamond inference has no target type
     * once the declared type is gone).
     */
    static boolean wouldAnalyzeRewrite(final VariableTree node, final TreePath path, final Attribution attribution) {
        final var initializer = node.getInitializer();
        if (initializer == null || isPolyExpression(initializer)) {
            return false;
        }
        final var declaredType = attribution.trees().getTypeMirror(new TreePath(path, node.getType()));
        if (declaredType == null) {
            return false;
        }
        final var initializerType = attribution.trees().getTypeMirror(new TreePath(path, initializer));
        if (initializerType == null || initializerType.getKind() == TypeKind.NULL) {
            return false;
        }
        return attribution.types().isSameType(initializerType, declaredType);
    }

    /**
     * Whether the initializer has no type of its own and takes one from the declared type. javac
     * reports the target type as the expression's type, so a type comparison would say the rewrite
     * is safe when in fact removing the declared type leaves nothing to infer from:
     *
     * <ul>
     *   <li>lambdas and method references need a functional interface as a target;</li>
     *   <li>a bare {@code {1, 2}} array initializer is only legal against a declared array type;</li>
     *   <li>a diamond {@code new ArrayList<>()} infers its type arguments from the target;</li>
     *   <li>an anonymous class body types the expression as the anonymous class, which no
     *       declaration can name.</li>
     * </ul>
     */
    private static boolean isPolyExpression(final ExpressionTree initializer) {
        return switch (initializer) {
            case LambdaExpressionTree ignored -> true;
            case MemberReferenceTree ignored -> true;
            case NewArrayTree array -> array.getType() == null;
            case NewClassTree newClass -> hasDiamond(newClass) || isAnonymousClassInstantiation(newClass);
            default -> false;
        };
    }

    private static boolean hasDiamond(final NewClassTree newClass) {
        return newClass.getIdentifier() instanceof ParameterizedTypeTree pt && pt.getTypeArguments().isEmpty();
    }

    /**
     * Whether {@code newClass} has an anonymous class body. Its expression type is the anonymous
     * class itself, not the named supertype being instantiated, so no declared type could name it
     * and {@code var} would narrow to something unnameable.
     */
    private static boolean isAnonymousClassInstantiation(final NewClassTree newClass) {
        return newClass.getClassBody() != null;
    }

    private static boolean isLocalBlock(final TreePath path) {
        final var parent = path.getParentPath();
        return parent != null && parent.getLeaf() instanceof BlockTree;
    }

    /**
     * Whether {@code node} is already declared as {@code var}, checked against the raw source
     * text rather than the resolved type: javac resolves {@code var} to its inferred type during
     * attribution, so the AST alone can't distinguish an explicit type from an inferred one.
     */
    private static boolean isVarType(final VariableTree node, final ParsedUnit unit) {
        final var typeStart = (int) unit.sourcePositions().getStartPosition(unit.tree(), node.getType());
        if (typeStart < 0 || typeStart + 3 > unit.source().length()) {
            return false;
        }
        final var src = unit.source();
        return src.startsWith("var", typeStart) && (typeStart + 3 >= src.length() || !Character.isJavaIdentifierPart(src.charAt(typeStart + 3)));
    }

    private static void addReplaceTypeEdit(final VariableTree node, final ParsedUnit unit, final List<LintEdit> edits) {
        final var typeStart = (int) unit.sourcePositions().getStartPosition(unit.tree(), node.getType());
        final var typeEnd = (int) unit.sourcePositions().getEndPosition(unit.tree(), node.getType());
        if (typeStart >= 0 && typeEnd > typeStart) {
            edits.add(new LintEdit(typeStart, typeEnd, "var"));
        }
    }

    private static void tryStaticFactoryRewrite(
            final VariableTree node,
            final MethodInvocationTree call,
            final TreePath path,
            final LintContext ctx,
            final List<LintEdit> edits) {
        final var unit = ctx.unit();
        if (!(node.getType() instanceof ParameterizedTypeTree pt) || !(pt.getType() instanceof IdentifierTree rawTypeId)) {
            return;
        }
        final var typeStart = (int) unit.sourcePositions().getStartPosition(unit.tree(), node.getType());
        final var typeEnd = (int) unit.sourcePositions().getEndPosition(unit.tree(), node.getType());
        final var rawEnd = (int) unit.sourcePositions().getEndPosition(unit.tree(), pt.getType());
        if (typeStart < 0 || typeEnd <= typeStart || rawEnd <= 0 || rawEnd >= typeEnd) {
            return;
        }
        if (!call.getTypeArguments().isEmpty()) {
            if (typeArgsMatchTextually(pt.getTypeArguments(), call.getTypeArguments(), unit)) {
                edits.add(new LintEdit(typeStart, typeEnd, "var"));
            }
            return;
        }
        if (!(call.getMethodSelect() instanceof MemberSelectTree mst) || !(mst.getExpression() instanceof IdentifierTree receiver)) {
            return;
        }
        if (!rawTypeId.getName().contentEquals(receiver.getName())) {
            return;
        }
        if (!witnessFitsTheMethod(call, path, ctx, pt.getTypeArguments().size())) {
            return;
        }
        final var selectEnd = (int) unit.sourcePositions().getEndPosition(unit.tree(), mst);
        final var namePos = insertionPointBeforeMethodName(mst, selectEnd);
        final var src = unit.source();
        if (selectEnd < 0
            || namePos <= 0
            || !src.startsWith(mst.getIdentifier().toString(), namePos)) {
            return;
        }
        final var witness = src.substring(rawEnd, typeEnd);
        edits.add(new LintEdit(typeStart, typeEnd, "var"));
        edits.add(LintEdit.insert(namePos, witness));
    }

    /**
     * Whether a witness of {@code witnessCount} type arguments would be accepted by the method
     * {@code call} resolves to. A receiver that happens to share the declared type's simple name
     * says nothing about the factory's own type parameters: {@code Comparator.comparing} declares
     * two where {@code Comparator<String>} supplies one, and javac rejects the call outright
     * ("wrong number of type arguments; required 2"). Only attribution knows the method's arity,
     * so without it the lift does not happen at all and the declaration keeps its written type.
     */
    private static boolean witnessFitsTheMethod(final MethodInvocationTree call, final TreePath path, final LintContext ctx, final int witnessCount) {
        return ctx.attribution()
                .map(attribution -> attribution.trees().getElement(new TreePath(path, call.getMethodSelect())))
                .filter(ExecutableElement.class::isInstance)
                .map(ExecutableElement.class::cast)
                .filter(method -> method.getTypeParameters().size() == witnessCount)
                .isPresent();
    }

    /**
     * The offset immediately before the method name — where the type witness is inserted —
     * derived from the member select's end minus the name length rather than by scanning forward
     * for a {@code '.'}, which would walk straight into any comment sitting between the receiver
     * and the dot.
     */
    private static int insertionPointBeforeMethodName(final MemberSelectTree mst, final int selectEnd) {
        return selectEnd - mst.getIdentifier().length();
    }

    private static boolean typeArgsMatchTextually(final List<? extends Tree> declared, final List<? extends Tree> witness, final ParsedUnit unit) {
        return declared.size() == witness.size()
            && IntStream.range(0, declared.size()).allMatch(i -> unit.sourceOf(declared.get(i)).trim().equals(unit.sourceOf(witness.get(i)).trim()));
    }

    private static boolean typesMatch(final VariableTree node, final NewClassTree newClass, final ParsedUnit unit) {
        final var identifier = newClass.getIdentifier();
        if (identifier == null || isAnonymousClassInstantiation(newClass)) {
            return false;
        }
        final var typeStart = (int) unit.sourcePositions().getStartPosition(unit.tree(), node.getType());
        final var identStart = (int) unit.sourcePositions().getStartPosition(unit.tree(), identifier);
        if (typeStart < 0 || identStart < 0) {
            return false;
        }
        final var declaredSrc = unit.sourceOf(node.getType()).trim();
        final var instantiatedSrc = unit.sourceOf(identifier).trim();
        return declaredSrc.equals(instantiatedSrc);
    }
}
