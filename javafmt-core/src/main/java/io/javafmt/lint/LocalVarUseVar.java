package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.util.TreePath;
import io.javafmt.parser.Attribution;
import io.javafmt.parser.ChainPathScanner;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.ParsedUnit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Replaces the declared type of a local with {@code var} when the initializer's type is evident or
 * provably keeps every use of the local the same.
 *
 * <p>From the source alone the rule rewrites a local whose initializer is:
 * <ul>
 *   <li>a {@code new} of exactly the declared type, reassigned or not;</li>
 *   <li>a static factory call that already carries a type witness matching the declared type
 *       arguments, or (with attribution only) a factory on the declared raw type, which gains the
 *       declared type arguments as its witness — see {@link #witnessFitsTheMethod};</li>
 *   <li>for a local that is never reassigned: a {@code new} of any named type, including a
 *       wider declaration; a diamond, which gains the declared type arguments when they are known
 *       to line up with the instantiated class's own; a cast to exactly the declared type; or a
 *       literal of exactly the declared type.</li>
 * </ul>
 *
 * <p>With attribution it also offers every other local that is never reassigned and whose
 * initializer's type equals the declared type or is a subtype of it, then lets
 * {@link VarRetypingCheck} re-attribute the rewritten file and keep only the rewrites under which
 * nothing in the local's scope changes type, symbol or compiles differently.
 *
 * <p>A multi-variable declaration is never rewritten: {@code var} is illegal in a compound
 * declaration, and the declarators share one type span, so the rule would emit the same edit once
 * each. {@link MultipleVariableDeclarations} splits the group and each declaration is then its own
 * candidate. An annotated local keeps its type too, since a type-use annotation cannot sit on
 * {@code var}.
 */
public final class LocalVarUseVar implements LintRule {

    private static final Map<String, Integer> DIAMOND_TARGET_ARITY = Map.ofEntries(
            Map.entry("Iterable", 1),
            Map.entry("Collection", 1),
            Map.entry("SequencedCollection", 1),
            Map.entry("List", 1),
            Map.entry("Set", 1),
            Map.entry("SequencedSet", 1),
            Map.entry("SortedSet", 1),
            Map.entry("NavigableSet", 1),
            Map.entry("Queue", 1),
            Map.entry("Deque", 1),
            Map.entry("BlockingQueue", 1),
            Map.entry("BlockingDeque", 1),
            Map.entry("TransferQueue", 1),
            Map.entry("Map", 2),
            Map.entry("SequencedMap", 2),
            Map.entry("SortedMap", 2),
            Map.entry("NavigableMap", 2),
            Map.entry("ConcurrentMap", 2),
            Map.entry("ConcurrentNavigableMap", 2));

    private static final Map<String, Integer> DIAMOND_SOURCE_ARITY = Map.ofEntries(
            Map.entry("ArrayList", 1),
            Map.entry("LinkedList", 1),
            Map.entry("CopyOnWriteArrayList", 1),
            Map.entry("HashSet", 1),
            Map.entry("LinkedHashSet", 1),
            Map.entry("TreeSet", 1),
            Map.entry("CopyOnWriteArraySet", 1),
            Map.entry("ConcurrentSkipListSet", 1),
            Map.entry("ArrayDeque", 1),
            Map.entry("PriorityQueue", 1),
            Map.entry("ConcurrentLinkedQueue", 1),
            Map.entry("ConcurrentLinkedDeque", 1),
            Map.entry("LinkedBlockingQueue", 1),
            Map.entry("LinkedBlockingDeque", 1),
            Map.entry("ArrayBlockingQueue", 1),
            Map.entry("PriorityBlockingQueue", 1),
            Map.entry("LinkedTransferQueue", 1),
            Map.entry("HashMap", 2),
            Map.entry("LinkedHashMap", 2),
            Map.entry("TreeMap", 2),
            Map.entry("IdentityHashMap", 2),
            Map.entry("WeakHashMap", 2),
            Map.entry("ConcurrentHashMap", 2),
            Map.entry("ConcurrentSkipListMap", 2));

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        final var reassignments = new ReassignmentDetector();
        final var proposals = LintScan.<Proposal>collect(
                ctx,
                sink -> new ChainPathScanner<>() {

                    @Override
                    public Void visitVariable(final VariableTree node, final Void p) {
                        final var path = getCurrentPath();
                        if (isCandidate(path, unit)) {
                            final var reassigned = reassignments.reassigned(path.getParentPath().getLeaf(), node.getName().toString());
                            final var proven = provenEdits(node, path, ctx, reassigned);
                            if (!proven.isEmpty()) {
                                sink.add(new Proposal(path, proven, true));
                            } else if (!reassigned) {
                                ctx.attribution()
                                        .filter(attribution -> isAnalyzeCandidate(node, path, attribution))
                                        .flatMap(_ -> replaceTypeEdit(node, unit))
                                        .ifPresent(edit -> sink.add(new Proposal(path, List.of(edit), false)));
                            }
                        }
                        return super.visitVariable(node, null);
                    }
                });
        final var proven = proposals.stream().filter(Proposal::proven).toList();
        final var unproven = proposals.stream().filter(proposal -> !proposal.proven()).toList();
        final var verified = unproven.isEmpty()
                ? List.<Proposal>of()
                : ctx.attribution().map(attribution -> VarRetypingCheck.verified(unit, attribution, proven, unproven)).orElse(List.of());
        return LintResult.ofEdits(Stream.concat(proven.stream(), verified.stream()).flatMap(proposal -> proposal.edits().stream()).toList());
    }

    static boolean rewritesNewInitializer(final TreePath varPath, final ParsedUnit unit) {
        if (!(varPath.getLeaf() instanceof VariableTree node)
            || !(node.getInitializer() instanceof NewClassTree newClass)
            || !isCandidate(varPath, unit)) {
            return false;
        }
        final var reassigned = new ReassignmentDetector().reassigned(varPath.getParentPath().getLeaf(), node.getName().toString());
        return !newClassEdits(node, newClass, unit, reassigned).isEmpty();
    }

    private static boolean isCandidate(final TreePath path, final ParsedUnit unit) {
        return path.getLeaf() instanceof VariableTree node
            && isLocalBlock(path)
            && node.getType() != null
            && node.getInitializer() != null
            && node.getModifiers().getAnnotations().isEmpty()
            && !isVarType(node, unit)
            && !MultipleVariableDeclarations.isPartOfDeclarationGroup(path, unit);
    }

    private static List<LintEdit> provenEdits(final VariableTree node, final TreePath path, final LintContext ctx, final boolean reassigned) {
        final var unit = ctx.unit();
        return switch (node.getInitializer()) {
            case NewClassTree newClass -> newClassEdits(node, newClass, unit, reassigned);
            case MethodInvocationTree call -> staticFactoryEdits(node, call, path, ctx);
            case TypeCastTree cast when !reassigned && sameText(cast.getType(), node.getType(), unit) ->
                replaceTypeEdit(node, unit).stream().toList();
            case LiteralTree literal when !reassigned && literalMatches(literal, node.getType(), unit) ->
                replaceTypeEdit(node, unit).stream().toList();
            case null, default -> List.of();
        };
    }

    private static List<LintEdit> newClassEdits(
            final VariableTree node,
            final NewClassTree newClass,
            final ParsedUnit unit,
            final boolean reassigned) {
        if (newClass.getClassBody() != null || newClass.getIdentifier() == null) {
            return List.of();
        }
        if (sameText(newClass.getIdentifier(), node.getType(), unit)) {
            return replaceTypeEdit(node, unit).stream().toList();
        }
        if (reassigned) {
            return List.of();
        }
        final var declaredParameterized = node.getType() instanceof ParameterizedTypeTree;
        return switch (newClass.getIdentifier()) {
            case ParameterizedTypeTree diamond when diamond.getTypeArguments().isEmpty() -> diamondEdits(node, diamond, unit);
            case ParameterizedTypeTree _ when declaredParameterized -> replaceTypeEdit(node, unit).stream().toList();
            case ParameterizedTypeTree _ -> List.of();
            default -> declaredParameterized ? List.of() : replaceTypeEdit(node, unit).stream().toList();
        };
    }

    private static List<LintEdit> diamondEdits(final VariableTree node, final ParameterizedTypeTree diamond, final ParsedUnit unit) {
        if (!(node.getType() instanceof ParameterizedTypeTree declared)
            || declared.getTypeArguments().isEmpty()
            || declared.getTypeArguments().stream().anyMatch(WildcardTree.class::isInstance)
            || !typeParametersLineUp(declared, diamond, unit)) {
            return List.of();
        }
        final var closeAt = unit.endOf(diamond) - 1;
        final var argumentsStart = unit.startOf(declared.getTypeArguments().getFirst());
        final var argumentsEnd = unit.endOf(declared.getTypeArguments().getLast());
        if (closeAt <= 0 || argumentsStart < 0 || argumentsEnd <= argumentsStart || unit.source().charAt(closeAt) != '>') {
            return List.of();
        }
        return replaceTypeEdit(node, unit)
                .map(edit -> List.of(edit, LintEdit.insert(closeAt, unit.source().substring(argumentsStart, argumentsEnd))))
                .orElse(List.of());
    }

    private static boolean typeParametersLineUp(final ParameterizedTypeTree declared, final ParameterizedTypeTree diamond, final ParsedUnit unit) {
        if (sameText(declared.getType(), diamond.getType(), unit)) {
            return true;
        }
        if (!(declared.getType() instanceof IdentifierTree target) || !(diamond.getType() instanceof IdentifierTree source)) {
            return false;
        }
        final var targetArity = DIAMOND_TARGET_ARITY.get(target.getName().toString());
        return targetArity != null
            && targetArity == declared.getTypeArguments().size()
            && targetArity.equals(DIAMOND_SOURCE_ARITY.get(source.getName().toString()));
    }

    private static boolean literalMatches(final LiteralTree literal, final Tree declared, final ParsedUnit unit) {
        return switch (declared) {
            case PrimitiveTypeTree primitive -> switch (primitive.getPrimitiveTypeKind()) {
                case INT -> literal.getKind() == Tree.Kind.INT_LITERAL;
                case LONG -> literal.getKind() == Tree.Kind.LONG_LITERAL;
                case FLOAT -> literal.getKind() == Tree.Kind.FLOAT_LITERAL;
                case DOUBLE -> literal.getKind() == Tree.Kind.DOUBLE_LITERAL;
                case BOOLEAN -> literal.getKind() == Tree.Kind.BOOLEAN_LITERAL;
                case CHAR -> literal.getKind() == Tree.Kind.CHAR_LITERAL;
                default -> false;
            };
            case IdentifierTree _ -> literal.getKind() == Tree.Kind.STRING_LITERAL && unit.sourceOf(declared).trim().equals("String");
            default -> false;
        };
    }

    private static boolean isAnalyzeCandidate(final VariableTree node, final TreePath path, final Attribution attribution) {
        final var initializer = withoutParentheses(node.getInitializer());
        if (hasNoTypeOfItsOwn(initializer, path, attribution) || carriesTypeWitness(initializer)) {
            return false;
        }
        final var declaredType = attribution.trees().getTypeMirror(new TreePath(path, node.getType()));
        final var initializerType = attribution.trees().getTypeMirror(new TreePath(path, node.getInitializer()));
        if (!isUsable(declaredType) || !isUsable(initializerType) || initializerType.getKind() == TypeKind.NULL) {
            return false;
        }
        return declaredType.getKind().isPrimitive()
                ? attribution.types().isSameType(initializerType, declaredType)
                : !initializerType.getKind().isPrimitive() && attribution.types().isSubtype(initializerType, declaredType);
    }

    private static boolean isUsable(final TypeMirror type) {
        return type != null && type.getKind() != TypeKind.ERROR && type.getKind() != TypeKind.NONE;
    }

    /**
     * Whether the initializer has no type of its own and takes one from the declared type, or would
     * take a different one without it:
     *
     * <ul>
     *   <li>lambdas and method references need a functional interface as a target;</li>
     *   <li>a bare {@code {1, 2}} array initializer is only legal against a declared array type;</li>
     *   <li>a {@code new} is decided without attribution, and an anonymous class body types the
     *       expression as the anonymous class, which no declaration can name;</li>
     *   <li>a conditional whose arms differ, or a switch expression, takes the declared type as the
     *       type of its result.</li>
     * </ul>
     */
    private static boolean hasNoTypeOfItsOwn(final ExpressionTree initializer, final TreePath path, final Attribution attribution) {
        return switch (initializer) {
            case LambdaExpressionTree _ -> true;
            case MemberReferenceTree _ -> true;
            case NewArrayTree array -> array.getType() == null;
            case NewClassTree _ -> true;
            case SwitchExpressionTree _ -> true;
            case ConditionalExpressionTree conditional -> !armsShareAType(conditional, path, attribution);
            default -> false;
        };
    }

    private static boolean armsShareAType(final ConditionalExpressionTree conditional, final TreePath path, final Attribution attribution) {
        final var conditionalPath = new TreePath(path, conditional);
        final var whenTrue = attribution.trees().getTypeMirror(new TreePath(conditionalPath, conditional.getTrueExpression()));
        final var whenFalse = attribution.trees().getTypeMirror(new TreePath(conditionalPath, conditional.getFalseExpression()));
        return isUsable(whenTrue) && isUsable(whenFalse) && attribution.types().isSameType(whenTrue, whenFalse);
    }

    private static boolean carriesTypeWitness(final ExpressionTree initializer) {
        final var found = new AtomicBoolean();
        new ChainScanner<Void, Void>() {

            @Override
            protected void visitInvocation(final MethodInvocationTree node, final Void p) {
                if (!node.getTypeArguments().isEmpty()) {
                    found.set(true);
                }
            }
        }.scan(initializer, null);
        return found.get();
    }

    private static ExpressionTree withoutParentheses(final ExpressionTree expression) {
        return expression instanceof ParenthesizedTree parenthesized ? withoutParentheses(parenthesized.getExpression()) : expression;
    }

    private static boolean sameText(final Tree a, final Tree b, final ParsedUnit unit) {
        return unit.startOf(a) >= 0 && unit.startOf(b) >= 0 && withoutWhitespace(unit.sourceOf(a)).equals(withoutWhitespace(unit.sourceOf(b)));
    }

    private static String withoutWhitespace(final String text) {
        return text.codePoints()
                .filter(c -> !Character.isWhitespace(c))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
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
        final var typeStart = unit.startOf(node.getType());
        if (typeStart < 0 || typeStart + 3 > unit.source().length()) {
            return false;
        }
        final var src = unit.source();
        return src.startsWith("var", typeStart) && (typeStart + 3 >= src.length() || !Character.isJavaIdentifierPart(src.charAt(typeStart + 3)));
    }

    private static Optional<LintEdit> replaceTypeEdit(final VariableTree node, final ParsedUnit unit) {
        final var typeStart = unit.startOf(node.getType());
        final var typeEnd = unit.endOf(node.getType());
        return typeStart >= 0 && typeEnd > typeStart ? Optional.of(new LintEdit(typeStart, typeEnd, "var")) : Optional.empty();
    }

    private static List<LintEdit> staticFactoryEdits(
            final VariableTree node,
            final MethodInvocationTree call,
            final TreePath path,
            final LintContext ctx) {
        final var unit = ctx.unit();
        if (!(node.getType() instanceof ParameterizedTypeTree pt) || !(pt.getType() instanceof IdentifierTree rawTypeId)) {
            return List.of();
        }
        final var typeStart = unit.startOf(node.getType());
        final var typeEnd = unit.endOf(node.getType());
        final var rawEnd = unit.endOf(pt.getType());
        if (typeStart < 0 || typeEnd <= typeStart || rawEnd <= 0 || rawEnd >= typeEnd) {
            return List.of();
        }
        if (!call.getTypeArguments().isEmpty()) {
            return typeArgsMatchTextually(pt.getTypeArguments(), call.getTypeArguments(), unit)
                    ? List.of(new LintEdit(typeStart, typeEnd, "var"))
                    : List.of();
        }
        if (!(call.getMethodSelect() instanceof MemberSelectTree mst)
            || !(mst.getExpression() instanceof IdentifierTree receiver)
            || !rawTypeId.getName().contentEquals(receiver.getName())
            || !witnessFitsTheMethod(call, path, ctx, pt.getTypeArguments().size())) {
            return List.of();
        }
        final var selectEnd = unit.endOf(mst);
        final var namePos = insertionPointBeforeMethodName(mst, selectEnd);
        final var src = unit.source();
        if (selectEnd < 0
            || namePos <= 0
            || !src.startsWith(mst.getIdentifier().toString(), namePos)) {
            return List.of();
        }
        final var witness = src.substring(rawEnd, typeEnd);
        return List.of(new LintEdit(typeStart, typeEnd, "var"), LintEdit.insert(namePos, witness));
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
                .map(attribution -> attribution.trees().getElement(new TreePath(new TreePath(path, call), call.getMethodSelect())))
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

    record Proposal(TreePath path, List<LintEdit> edits, boolean proven) {

        Proposal {
            edits = List.copyOf(edits);
        }

        VariableTree variable() {
            return (VariableTree) path.getLeaf();
        }

        BlockTree scope() {
            return (BlockTree) path.getParentPath().getLeaf();
        }
    }
}
