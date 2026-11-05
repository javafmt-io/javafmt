package io.javafmt.parser;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.PackageTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;

@UtilityClass
final class CommentAttacher {
    /**
     * Orders slots so the innermost slot fires first when two slots enclose the same comment: by
     * ascending {@code hi}, then by {@link Reach}, then ascending {@code lo}.
     */
    private static final Comparator<Slot> SLOT_ORDER = Comparator.comparingLong(Slot::hi)
            .thenComparing(Slot::reach)
            .thenComparingLong(Slot::lo);

    ParsedUnit attach(final CompilationUnitTree tree, final String source, final SourcePositions positions, final List<CommentToken> comments) {
        if (comments.isEmpty()) {
            return new ParsedUnit(tree, source, positions, List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of());
        }
        final var slots = new ArrayList<Slot>();
        final var opaque = new ArrayList<long[]>();
        collectCompilationUnit(tree, source, positions, slots, opaque);
        slots.sort(SLOT_ORDER);
        final var fileHeader = new ArrayList<CommentToken>();
        final var fileFooter = new ArrayList<CommentToken>();
        final var leading = new IdentityHashMap<Tree, List<CommentToken>>();
        final var trailing = new IdentityHashMap<Tree, List<CommentToken>>();
        final var interior = new IdentityHashMap<Tree, List<CommentToken>>();
        final var tail = new IdentityHashMap<Tree, List<CommentToken>>();
        final var braceLine = new IdentityHashMap<Tree, List<CommentToken>>();
        final var loose = new ArrayList<CommentToken>();
        final var tokens = new TokenPositions(source);
        var index = 0;
        for (final var c : comments) {
            index = advancePast(slots, index, c);
            if (opaque.stream().anyMatch(span -> span[0] <= c.start() && c.end() <= span[1])) {
                continue;
            }
            final var slot = firstEnclosing(slots, index, c, source);
            if (slot == null || slot.reach() == Reach.CATCH_ALL) {
                loose.add(c);
                continue;
            }
            final var previous = slot.previous();
            if (previous != null && sameLine(source, (int) slot.lo(), c.start())) {
                if (positions.getEndPosition(tree, previous) >= tokens.endBefore(c)) {
                    trailing.computeIfAbsent(previous, _ -> new ArrayList<>()).add(c);
                } else {
                    loose.add(c);
                }
                continue;
            }
            if (followsTheLastElementsComma(slot, c, source)) {
                trailing.computeIfAbsent(previous, _ -> new ArrayList<>()).add(c);
                continue;
            }
            if (trailsPastTheSeparator(slot, c, source, tree, positions) && slot.owner() != null) {
                interior.computeIfAbsent(slot.owner(), _ -> new ArrayList<>()).add(c);
                continue;
            }
            switch (slot.kind()) {
                case FILE_HEADER -> fileHeader.add(c);
                case FILE_FOOTER -> fileFooter.add(c);
                case LEADING -> {
                    if (slot.owner() != null && positions.getStartPosition(tree, slot.owner()) == tokens.startAfter(c)) {
                        leading.computeIfAbsent(slot.owner(), _ -> new ArrayList<>()).add(c);
                    } else {
                        loose.add(c);
                    }
                }
                case INTERIOR -> {
                    if (slot.owner() != null) {
                        interior.computeIfAbsent(slot.owner(), _ -> new ArrayList<>()).add(c);
                    }
                }
                case TAIL -> {
                    if (slot.owner() != null) {
                        tail.computeIfAbsent(slot.owner(), _ -> new ArrayList<>()).add(c);
                    }
                }
                case BRACE_LINE -> {
                    if (slot.owner() != null) {
                        braceLine.computeIfAbsent(slot.owner(), _ -> new ArrayList<>()).add(c);
                    }
                }
                case LOOSE -> loose.add(c);
                case BEFORE_BRACE -> {
                    if (slot.owner() != null && c.text().startsWith("//")) {
                        braceLine.computeIfAbsent(slot.owner(), _ -> new ArrayList<>()).add(c);
                    } else {
                        loose.add(c);
                    }
                }
            }
        }
        return new ParsedUnit(tree, source, positions, fileHeader, fileFooter, leading, trailing, interior, tail, braceLine, loose);
    }

    /**
     * Advances {@code from} to the first slot whose {@code hi} is at least {@code c}'s end.
     * Comments arrive in source order, so {@code hi} is non-decreasing across successive calls
     * and the cursor this returns only ever moves forward.
     */
    private int advancePast(final List<Slot> slots, final int from, final CommentToken c) {
        var index = from;
        while (index < slots.size() && slots.get(index).hi() < c.end()) {
            index++;
        }
        return index;
    }

    /**
     * The tightest slot at or after {@code from} that fully encloses {@code c}, or {@code null} if
     * none does. Slots are sorted by ascending {@code hi}, so the first enclosing match is also the
     * innermost. Scanning forward past a non-enclosing candidate matters: a comment can sit inside
     * a construct whose own gap slots all start after it, and the enclosing self-span slot for that
     * construct sits further along the list.
     */
    @Nullable
    private Slot firstEnclosing(final List<Slot> slots, final int from, final CommentToken c, final String source) {
        return IntStream.range(from, slots.size())
                .mapToObj(slots::get)
                .filter(s -> s.lo() <= c.start() && s.hi() >= c.end())
                .filter(s -> s.reach() != Reach.SAME_LINE || sameLine(source, (int) s.lo(), c.start()))
                .findFirst()
                .orElse(null);
    }

    private boolean sameLine(final String source, final int from, final int to) {
        var i = from;
        while (i < to) {
            final var c = source.charAt(i);
            if (c == ' ' || c == '\t') {
                i++;
                continue;
            }
            final var commentEnd = CommentScanner.skipComment(source, i);
            if (commentEnd < 0 || commentEnd > to || source.startsWith("//", i) || source.substring(i, commentEnd).indexOf('\n') >= 0) {
                return false;
            }
            i = commentEnd;
        }
        return true;
    }

    /**
     * Whether {@code c} is a {@code //} comment the author wrote just past the separator of the
     * list element before it, as in {@code f(first, // about first}. Nothing else can share that
     * line, so the comment belongs to the element it follows, not to the one on the next line —
     * which is where it used to end up, dragging the whole list one element per line with it. The
     * comment goes to the {@code interior} channel of the element that follows, because the list's
     * renderer has to emit it after the separator: emitted before, a {@code //} comment would
     * swallow the comma.
     *
     * <p>A one-line block comment qualifies only when nothing follows it on its line. One with code
     * after it, <code>f(a, /* b *&#47; b)</code>, reads as annotating what comes after it and is
     * already attached that way.
     */
    private boolean trailsPastTheSeparator(final Slot slot,
                                           final CommentToken c,
                                           final String source,
                                           final CompilationUnitTree cu,
                                           final SourcePositions pos) {
        if (!slot.crossesSeparator() || slot.previous() == null || !(c.text().startsWith("//") || endsItsLine(c, source))) {
            return false;
        }
        final var gap = source.substring((int) slot.lo(), c.start());
        return gap.indexOf('\n') < 0 && gap.chars().allMatch(ch -> ch == ' ' || ch == '\t' || ch == ',' || isTheEnumTerminator(ch, slot, cu, pos));
    }

    private boolean followsTheLastElementsComma(final Slot slot, final CommentToken c, final String source) {
        if (slot.kind() != Kind.TAIL || !(slot.owner() instanceof NewArrayTree) || slot.previous() == null || c.text().startsWith("//")) {
            return false;
        }
        final var gap = source.substring((int) slot.lo(), c.start());
        return gap.indexOf('\n') < 0 && gap.chars().allMatch(ch -> ch == ' ' || ch == '\t' || ch == ',');
    }

    private boolean endsItsLine(final CommentToken c, final String source) {
        final var newline = source.indexOf('\n', c.end());
        final var rest = source.substring(c.end(), newline < 0 ? source.length() : newline);
        return c.text().indexOf('\n') < 0 && rest.isBlank();
    }

    /**
     * Whether a {@code ;} in the gap is the one that ends an enum's constant list rather than the
     * one that ends a declaration. It separates the last constant from what follows exactly as the
     * commas separate the others, so a comment written past it annotates that constant —
     * {@code CLOSED; // the stream is closed} in {@code java/lang/StackStreamFactory.java}.
     *
     * <p>The check is on the element before the gap, not on the character, because an enum body
     * holds ordinary declarations too and each of those ends in a {@code ;} of its own. Reading one
     * of those as a separator would route the comment after a field to a channel the member
     * renderers never ask for, and it would be dropped.
     *
     * <p>{@link Kind#TAIL} for the same reason: only there is the enum itself the owner, and only
     * {@code EnumRenderer} reads that channel. When a body declaration follows the constants the
     * owner is that declaration, and the comment stays a leading comment on it.
     *
     * <p>An enum constant is a {@link VariableTree} whose type node has no source position, which
     * is how {@code TreeFacts.isEnumConstant} tells one from a field.
     */
    private boolean isTheEnumTerminator(final int ch, final Slot slot, final CompilationUnitTree cu, final SourcePositions pos) {
        return ch == ';'
            && slot.kind() == Kind.TAIL
            && slot.previous() instanceof VariableTree v
            && v.getType() != null
            && pos.getEndPosition(cu, v.getType()) < 0;
    }

    private void collectCompilationUnit(final CompilationUnitTree cu,
                                        final String source,
                                        final SourcePositions pos,
                                        final List<Slot> slots,
                                        final List<long[]> opaque) {
        final var cuChildren = new ArrayList<Tree>();
        if (cu.getPackage() != null) {
            cuChildren.add(cu.getPackage());
        }
        cuChildren.addAll(cu.getImports());
        if (cu.getModule() != null) {
            cuChildren.add(cu.getModule());
        }
        cuChildren.addAll(cu.getTypeDecls());
        final var ordered = cuChildren.stream()
                .filter(t -> pos.getStartPosition(cu, t) >= 0)
                .sorted(Comparator.comparingLong(t -> pos.getStartPosition(cu, t)))
                .toList();
        if (ordered.isEmpty()) {
            addWholeSourceFileHeaderSlot(slots, source);
            return;
        }
        slots.add(new Slot(null, null, 0L, pos.getStartPosition(cu, ordered.getFirst()), Kind.FILE_HEADER));
        Tree previous = null;
        var previousEnd = 0L;
        for (final var current : ordered) {
            final var currentStart = pos.getStartPosition(cu, current);
            if (previous != null) {
                final var kind = current != cu.getPackage() ? Kind.LEADING : Kind.TAIL;
                slots.add(new Slot(previous, current, previousEnd, currentStart, kind));
            }
            previous = current;
            previousEnd = pos.getEndPosition(cu, current);
        }
        slots.add(new Slot(previous, null, previousEnd, source.length(), Kind.FILE_FOOTER));
        new Collector(cu, pos, source, slots, opaque).scan(cu, null);
    }

    /**
     * Adds a slot spanning the whole source when there are no top-level declarations at all — a
     * compilation unit needn't declare anything, so the source may be nothing but comments, and
     * without a slot that spans it every one of those comments would be dropped.
     */
    private void addWholeSourceFileHeaderSlot(final List<Slot> slots, final String source) {
        slots.add(new Slot(null, null, 0L, source.length(), Kind.FILE_HEADER));
    }

    private boolean isAttachmentSite(final Tree t) {
        return t instanceof ClassTree
            || t instanceof MethodTree
            || t instanceof VariableTree
            || t instanceof BlockTree;
    }

    private boolean isRecordComponent(final boolean isRecord, final Tree member) {
        return isRecord
            && member instanceof VariableTree v
            && !v.getModifiers().getFlags().contains(Modifier.STATIC);
    }

    private enum Kind { FILE_HEADER, FILE_FOOTER, LEADING, INTERIOR, TAIL, BRACE_LINE, BEFORE_BRACE, LOOSE }

    /**
     * How readily a slot claims a comment that several slots enclose. {@code SAME_LINE} only
     * matches a comment the author wrote on the slot's opening line, so it can be tried first
     * without stealing anything; {@code CATCH_ALL} spans a whole child and is the last resort,
     * losing to every precise slot that encloses the same comment, including ones that end at the
     * same offset. Declaration order is the tie-break order.
     */
    private enum Reach { SAME_LINE, PRECISE, CATCH_ALL }

    /**
     * A source range that a comment falling inside it is attached through. {@code crossesSeparator}
     * marks a slot in a comma-separated list whose renderer knows how to place a comment the author
     * left after the comma.
     */
    private record Slot(@Nullable Tree previous, @Nullable Tree owner, long lo, long hi, Kind kind, Reach reach, boolean crossesSeparator) {
        Slot(@Nullable final Tree previous, @Nullable final Tree owner, final long lo, final long hi, final Kind kind) {
            this(previous, owner, lo, hi, kind, Reach.PRECISE, false);
        }

        Slot(@Nullable final Tree previous, @Nullable final Tree owner, final long lo, final long hi, final Kind kind, final Reach reach) {
            this(previous, owner, lo, hi, kind, reach, false);
        }
    }

    private static final class TokenPositions {
        private final int[] starts;

        private final int[] ends;

        TokenPositions(final String source) {
            final var tokens = TokenScanner.scan(source);
            this.starts = tokens.stream().mapToInt(SourceToken::start).toArray();
            this.ends = tokens.stream().mapToInt(SourceToken::end).toArray();
        }

        long endBefore(final CommentToken comment) {
            final var next = nextIndex(comment);
            return next == 0 ? -1 : ends[next - 1];
        }

        long startAfter(final CommentToken comment) {
            final var next = nextIndex(comment);
            return next == starts.length ? -1 : starts[next];
        }

        private int nextIndex(final CommentToken comment) {
            final var found = Arrays.binarySearch(starts, comment.end());
            return found >= 0 ? found : -found - 1;
        }
    }

    private static final class Collector extends ChainScanner<Void, Void> {
        private final CompilationUnitTree cu;

        private final SourcePositions pos;

        private final String source;

        private final List<Slot> slots;

        private final List<long[]> opaque;

        Collector(final CompilationUnitTree cu, final SourcePositions pos, final String source, final List<Slot> slots, final List<long[]> opaque) {
            this.cu = cu;
            this.pos = pos;
            this.source = source;
            this.slots = slots;
            this.opaque = opaque;
        }

        /**
         * Records the source span of a literal the renderer prints verbatim from source, which is any
         * literal whose own span covers more than one line. A comment inside one of those is already
         * in the text that gets printed, so attaching it anywhere would emit it a second time.
         *
         * <p>That is not hypothetical: javac constant-folds {@code "aa" + "AAA" // Alpha} and the
         * lines below it into a <em>single</em> {@link LiteralTree} carrying the whole expression's
         * span, so {@code java/util/LocaleISOData.java} came out with each of its 437 trailing
         * comments twice.
         */
        @Override
        public Void visitLiteral(final LiteralTree node, final Void p) {
            final var start = pos.getStartPosition(cu, node);
            final var end = pos.getEndPosition(cu, node);
            if (start >= 0 && end > start && source.lastIndexOf('\n', (int) end - 1) > start) {
                opaque.add(new long[] {start, end,});
            }
            return null;
        }

        @Override
        public Void visitClass(final ClassTree node, final Void p) {
            final var isRecord = node.getKind() == Tree.Kind.RECORD;
            // emitTail: a comment after the last member and before the closing brace has no member
            // to hang off. Without a slot for it the class's catch-all claimed it and nothing
            // emitted it, so the file came back refused.
            // An enum's members are the one class body that is comma-separated, so a `//` comment
            // written past a constant's comma annotates that constant rather than the next one.
            // Nothing else in the list can match: the gap a comment has to cross is spaces, tabs
            // and commas only, and every other member is separated by a `;` or a `}`.
            collect(node, members(node, isRecord), true, node.getKind() == Tree.Kind.ENUM);
            collectAnnotations(node.getModifiers());
            collectHeaderBraceLine(node, isRecord);
            members(node, isRecord)
                    .stream()
                    .flatMap(member -> member instanceof VariableTree v && !isEnumConstant(v) ? Stream.of(v.getModifiers()) : Stream.empty())
                    .forEach(this::collectAnnotations);
            if (isRecord) {
                collect(node, recordComponents(node), false);
                collectBeforeClosingParenthesis(node, recordComponents(node), Kind.LOOSE);
                recordComponents(node)
                        .stream()
                        .flatMap(component -> component instanceof VariableTree v ? Stream.of(v.getModifiers()) : Stream.empty())
                        .forEach(modifiers -> collectAnnotations(modifiers, false));
            }
            if (node.getExtendsClause() != null) {
                collect(node, List.of(node.getExtendsClause()), false);
            }
            collect(node, node.getImplementsClause(), false, true);
            collect(node, node.getPermitsClause(), false, true);
            collect(node, node.getTypeParameters(), false);
            return super.visitClass(node, null);
        }

        @Override
        public Void visitBlock(final BlockTree node, final Void p) {
            collect(node, node.getStatements(), true);
            collectBraceLine(node, pos.getStartPosition(cu, node), node.getStatements().stream().map(statement -> (Tree) statement).toList());
            node.getStatements()
                    .stream()
                    .flatMap(statement -> statement instanceof VariableTree v ? Stream.of(v.getModifiers()) : Stream.empty())
                    .forEach(this::collectAnnotations);
            return super.visitBlock(node, null);
        }

        @Override
        public Void visitMethod(final MethodTree node, final Void p) {
            collectAnnotations(node.getModifiers());
            node.getParameters().forEach(parameter -> collectAnnotations(parameter.getModifiers()));
            collectSeparatedList(node, pos.getStartPosition(cu, node), node.getParameters());
            collect(node, node.getTypeParameters(), false);
            collect(node, node.getThrows(), false, true);
            return super.visitMethod(node, null);
        }

        @Override
        protected void visitInvocation(final MethodInvocationTree node, final Void p) {
            collectArguments(node, pos.getEndPosition(cu, node.getMethodSelect()), node.getArguments());
            collectInsideEmptyArguments(node, pos.getEndPosition(cu, node.getMethodSelect()), node.getArguments());
            collectChainJoint(node);
        }

        private void collectAnnotationLineEnds(final ModifiersTree modifiers) {
            modifiers.getAnnotations().forEach(this::collectLineEnd);
        }

        private void collectLineEnd(final AnnotationTree annotation) {
            final var end = pos.getEndPosition(cu, annotation);
            if (end < 0) {
                return;
            }
            final var newline = source.indexOf('\n', (int) end);
            final var lineEnd = newline < 0 ? source.length() : newline;
            if (source.substring((int) end, lineEnd).stripLeading().startsWith("//")) {
                slots.add(new Slot(annotation, null, end, lineEnd, Kind.LEADING, Reach.SAME_LINE));
            }
        }

        private void collectAnnotations(final ModifiersTree modifiers) {
            collectAnnotations(modifiers, true);
        }

        private void collectAnnotations(final ModifiersTree modifiers, final boolean withHeaderGap) {
            collectAnnotationLineEnds(modifiers);
            final var annotations = modifiers.getAnnotations();
            final var gaps = annotations.stream().map(this::gapAfter).toList();
            final var chained = IntStream.range(0, annotations.size() - 1)
                    .filter(i -> gaps.get(i) == pos.getStartPosition(cu, annotations.get(i + 1)))
                    .boxed()
                    .toList();
            chained.forEach(
                    i -> slots.add(
                            new Slot(
                                    annotations.get(i), annotations.get(i + 1), pos.getEndPosition(cu, annotations.get(i)), gaps.get(i),
                                    Kind.LEADING)));
            final var leadsTheDeclaration = !annotations.isEmpty()
                && pos.getStartPosition(cu, modifiers) == pos.getStartPosition(cu, annotations.getFirst());
            if (!withHeaderGap || !leadsTheDeclaration) {
                return;
            }
            final var lastOfTheLeadingRun = IntStream.range(0, annotations.size())
                    .filter(i -> !chained.contains(i))
                    .findFirst()
                    .orElse(annotations.size() - 1);
            final var end = pos.getEndPosition(cu, annotations.get(lastOfTheLeadingRun));
            final var headerStart = gaps.get(lastOfTheLeadingRun);
            if (end >= 0 && headerStart > end) {
                slots.add(new Slot(annotations.get(lastOfTheLeadingRun), modifiers, end, headerStart, Kind.TAIL));
            }
        }

        private long gapAfter(final AnnotationTree annotation) {
            final var end = pos.getEndPosition(cu, annotation);
            return end < 0 ? -1 : skipWhitespaceAndComments((int) end);
        }

        private int skipWhitespaceAndComments(final int from) {
            var at = from;
            while (at < source.length()) {
                if (Character.isWhitespace(source.charAt(at))) {
                    at++;
                } else if (source.startsWith("//", at)) {
                    final var newline = source.indexOf('\n', at);
                    at = newline < 0 ? source.length() : newline;
                } else if (source.startsWith("/*", at)) {
                    final var close = source.indexOf("*/", at + 2);
                    at = close < 0 ? source.length() : close + 2;
                } else {
                    return at;
                }
            }
            return at;
        }

        @Override
        public Void visitPackage(final PackageTree node, final Void p) {
            collect(node, Stream.concat(node.getAnnotations().stream(), Stream.of(node.getPackageName())).toList(), false);
            return super.visitPackage(node, null);
        }

        private boolean isEnumConstant(final VariableTree variable) {
            return variable.getType() != null && pos.getEndPosition(cu, variable.getType()) < 0;
        }

        /**
         * The gap between one call in a chain and the {@code .} that starts the next, so a comment
         * the author wrote at the end of a link trails that link instead of being hoisted above
         * the whole statement. Only a comment on the link's own line is claimed, and only where
         * the receiver is itself a call: that is the shape the chain renderer walks link by link,
         * and so the only one that can place the comment without letting a {@code //} swallow what
         * follows it.
         */
        private void collectChainJoint(final MethodInvocationTree node) {
            if (!(node.getMethodSelect() instanceof MemberSelectTree ms) || !(ms.getExpression() instanceof MethodInvocationTree receiver)) {
                return;
            }
            final var receiverEnd = pos.getEndPosition(cu, receiver);
            final var selectEnd = pos.getEndPosition(cu, ms);
            if (receiverEnd >= 0 && selectEnd > receiverEnd) {
                slots.add(new Slot(receiver, ms, receiverEnd, selectEnd, Kind.LEADING, Reach.SAME_LINE));
            }
        }

        @Override
        public Void visitNewClass(final NewClassTree node, final Void p) {
            collectArguments(node, pos.getEndPosition(cu, node.getIdentifier()), node.getArguments());
            collectInsideEmptyArguments(node, pos.getEndPosition(cu, node.getIdentifier()), node.getArguments());
            return super.visitNewClass(node, null);
        }

        private void collectInsideEmptyArguments(final Tree call, final long nameEnd, final List<? extends Tree> arguments) {
            if (!arguments.isEmpty() || nameEnd < 0) {
                return;
            }
            final var open = skipWhitespaceAndComments((int) nameEnd);
            if (open >= source.length() || source.charAt(open) != '(') {
                return;
            }
            final var close = skipWhitespaceAndComments(open + 1);
            if (close > open + 1 && close < source.length() && source.charAt(close) == ')') {
                slots.add(new Slot(null, call, open + 1L, close, Kind.INTERIOR));
            }
        }

        @Override
        public Void visitVariable(final VariableTree node, final Void p) {
            final var initializer = node.getInitializer();
            if (initializer == null) {
                return super.visitVariable(node, null);
            }
            final var initializerStart = pos.getStartPosition(cu, initializer);
            final var initializerEnd = pos.getEndPosition(cu, initializer);
            final var varStart = pos.getStartPosition(cu, node);
            final var varEnd = pos.getEndPosition(cu, node);
            final var afterName = positionAfterTheDeclaredName(node, varStart);
            if (initializerStart >= 0 && afterName >= 0 && initializerStart > afterName) {
                slots.add(new Slot(null, initializer, afterName, initializerStart, Kind.LEADING));
            }
            if (initializerEnd >= 0 && varEnd > initializerEnd) {
                slots.add(new Slot(initializer, node, initializerEnd, varEnd, Kind.TAIL));
            }
            return super.visitVariable(node, null);
        }

        /**
         * Just past the declared name, which is where the slot leading the initializer begins.
         *
         * <p>It used to begin at the declaration's start, which swallowed a comment the author
         * wrote in front of the <em>name</em> and rendered it after the {@code =}. That is wrong on
         * its own — the comment explains the constant, not its value — and it does not survive a
         * second run: a multi-line comment there is re-indented against a column that moves, so
         * {@code format(format(x))} differed from {@code format(x)}.
         */
        private long positionAfterTheDeclaredName(final VariableTree node, final long varStart) {
            final var type = node.getType();
            final var from = type == null ? varStart : pos.getEndPosition(cu, type);
            if (from < 0) {
                return -1;
            }
            final var name = node.getName().toString();
            final var at = source.indexOf(name, (int) from);
            return at < 0 ? -1 : at + name.length();
        }

        @Override
        public Void visitNewArray(final NewArrayTree node, final Void p) {
            if (node.getInitializers() != null) {
                collectWithLo(node, pos.getStartPosition(cu, node), node.getInitializers(), true, true);
                collectBeforeInitializerBrace(node);
            }
            return super.visitNewArray(node, null);
        }

        private void collectBeforeInitializerBrace(final NewArrayTree node) {
            if (node.getType() == null) {
                return;
            }
            final var typeEnd = Stream.concat(Stream.of(node.getType()), node.getDimensions().stream())
                    .mapToLong(t -> pos.getEndPosition(cu, t))
                    .max()
                    .orElse(-1);
            if (typeEnd < 0) {
                return;
            }
            final var brace = headerBrace((int) typeEnd);
            final var lastTokenEnd = lastTokenEndBefore((int) typeEnd, brace);
            if (brace > lastTokenEnd) {
                slots.add(new Slot(null, node, lastTokenEnd, brace, Kind.BRACE_LINE));
            }
        }

        @Override
        public Void visitAnnotation(final AnnotationTree node, final Void p) {
            collectWithLo(node, pos.getEndPosition(cu, node.getAnnotationType()), node.getArguments(), true, true);
            return super.visitAnnotation(node, null);
        }

        @Override
        protected void visitBinaryOperator(final BinaryTree node, final Void p) {
            final var leftEnd = pos.getEndPosition(cu, node.getLeftOperand());
            final var rightStart = pos.getStartPosition(cu, node.getRightOperand());
            if (leftEnd >= 0 && rightStart > leftEnd) {
                final var operator = skipWhitespaceAndComments((int) leftEnd);
                slots.add(new Slot(operandBefore(node), node, leftEnd, operator, Kind.TAIL));
                slots.add(new Slot(null, node.getRightOperand(), endOfOperator(operator), rightStart, Kind.LEADING));
            }
        }

        private Tree operandBefore(final BinaryTree node) {
            final var logical = node.getKind() == Tree.Kind.CONDITIONAL_AND || node.getKind() == Tree.Kind.CONDITIONAL_OR;
            Tree operand = node.getLeftOperand();
            while (logical && operand instanceof BinaryTree inner && inner.getKind() == node.getKind()) {
                operand = inner.getRightOperand();
            }
            return operand;
        }

        private int endOfOperator(final int from) {
            var at = from;
            while (at < source.length()
                && "&|+-*/%<>=!^?:".indexOf(source.charAt(at)) >= 0
                && !source.startsWith("//", at)
                && !source.startsWith("/*", at)) {
                at++;
            }
            return at;
        }

        @Override
        public Void visitConditionalExpression(final ConditionalExpressionTree node, final Void p) {
            final var conditionEnd = pos.getEndPosition(cu, node.getCondition());
            final var trueStart = pos.getStartPosition(cu, node.getTrueExpression());
            final var trueEnd = pos.getEndPosition(cu, node.getTrueExpression());
            final var falseStart = pos.getStartPosition(cu, node.getFalseExpression());
            if (conditionEnd >= 0 && trueStart > conditionEnd) {
                final var question = skipWhitespaceAndComments((int) conditionEnd);
                slots.add(new Slot(node.getCondition(), node, conditionEnd, question, Kind.TAIL));
                slots.add(new Slot(null, node.getTrueExpression(), question + 1L, trueStart, Kind.LEADING));
            }
            if (trueEnd >= 0 && falseStart > trueEnd) {
                final var colon = skipWhitespaceAndComments((int) trueEnd);
                slots.add(new Slot(node.getTrueExpression(), node, trueEnd, colon, Kind.INTERIOR));
                slots.add(new Slot(null, node.getFalseExpression(), colon + 1L, falseStart, Kind.LEADING));
            }
            return super.visitConditionalExpression(node, null);
        }

        @Override
        public Void visitTypeCast(final TypeCastTree node, final Void p) {
            collect(node, List.of(node.getType()), false);
            return super.visitTypeCast(node, null);
        }

        @Override
        public Void visitCase(final CaseTree node, final Void p) {
            collect(node, node.getLabels(), false);
            if (node.getCaseKind() != CaseTree.CaseKind.RULE) {
                collectColonFormBody(node);
            }
            collectAfterTheLabels(node);
            return super.visitCase(node, null);
        }

        private void collectAfterTheLabels(final CaseTree node) {
            final var labelsEnd = Stream.concat(node.getLabels().stream(), Stream.ofNullable(node.getGuard()))
                    .mapToLong(t -> pos.getEndPosition(cu, t))
                    .max()
                    .orElse(-1);
            if (labelsEnd < 0) {
                return;
            }
            final var punctuation = skipWhitespaceAndComments((int) labelsEnd);
            if (node.getCaseKind() == CaseTree.CaseKind.RULE) {
                final var body = node.getBody();
                if (body != null && source.startsWith("->", punctuation)) {
                    slots.add(
                            new Slot(
                                    null, node, punctuation + 2L, pos.getStartPosition(cu, body),
                                    body instanceof BlockTree ? Kind.BEFORE_BRACE : Kind.BRACE_LINE));
                }
                return;
            }
            if (punctuation < source.length() && source.charAt(punctuation) == ':') {
                final var firstStatement = node.getStatements().stream().mapToLong(s -> pos.getStartPosition(cu, s)).min().orElse(-1);
                if (firstStatement > punctuation + 1) {
                    slots.add(new Slot(null, node, punctuation + 1L, firstStatement, Kind.BRACE_LINE, Reach.SAME_LINE));
                }
            }
        }

        @Override
        public Void visitModule(final ModuleTree node, final Void p) {
            collect(node, node.getDirectives(), true);
            return super.visitModule(node, null);
        }

        @Override
        public Void visitSwitch(final SwitchTree node, final Void p) {
            collect(node, node.getCases(), false);
            collectBraceLine(node, pos.getEndPosition(cu, node.getExpression()), node.getCases().stream().map(c -> (Tree) c).toList());
            return super.visitSwitch(node, null);
        }

        @Override
        public Void visitSwitchExpression(final SwitchExpressionTree node, final Void p) {
            collect(node, node.getCases(), false);
            collectBraceLine(node, pos.getEndPosition(cu, node.getExpression()), node.getCases().stream().map(c -> (Tree) c).toList());
            return super.visitSwitchExpression(node, null);
        }

        private void collectColonFormBody(final CaseTree node) {
            final var statements = node.getStatements();
            if (statements.isEmpty()) {
                return;
            }
            final var labels = node.getLabels();
            if (labels.isEmpty()) {
                return;
            }
            final var lastLabelEnd = pos.getEndPosition(cu, labels.getLast());
            if (lastLabelEnd < 0) {
                return;
            }
            collectWithLo(node, lastLabelEnd, statements, false);
        }

        /**
         * The gap between the then-branch and the else-branch, so a comment the author wrote at
         * the {@code else} joint stays there. Without this slot the only enclosing slot is the
         * catch-all spanning the whole {@code if}, which makes the comment lead the statement and
         * reads as annotating the first branch instead of the second.
         */
        @Override
        public Void visitIf(final IfTree node, final Void p) {
            final var elseStatement = node.getElseStatement();
            if (elseStatement != null) {
                final var thenEnd = pos.getEndPosition(cu, node.getThenStatement());
                final var elseStart = pos.getStartPosition(cu, elseStatement);
                if (thenEnd >= 0 && elseStart > thenEnd) {
                    final var elseKeyword = skipWhitespaceAndComments((int) thenEnd);
                    slots.add(new Slot(null, node, thenEnd, elseKeyword, Kind.BRACE_LINE, Reach.SAME_LINE));
                    slots.add(new Slot(null, node, thenEnd, elseKeyword, Kind.INTERIOR));
                    slots.add(new Slot(null, node, elseKeyword + 4L, elseStart, Kind.TAIL));
                }
            }
            return super.visitIf(node, null);
        }

        @Override
        public Void visitForLoop(final ForLoopTree node, final Void p) {
            final var parts = new ArrayList<Tree>(node.getInitializer());
            if (node.getCondition() != null) {
                parts.add(node.getCondition());
            }
            parts.addAll(node.getUpdate());
            collect(node, parts, false);
            IntStream.range(0, parts.size() - 1).forEach(i -> collectBeforeInitializerSeparator(parts.get(i), parts.get(i + 1)));
            IntStream.range(0, parts.size() - 1).forEach(i -> collectPastTheSeparator(parts.get(i), parts.get(i + 1)));
            collectBeforeClosingParenthesis(node, parts);
            return super.visitForLoop(node, null);
        }

        private void collectPastTheSeparator(final Tree part, final Tree next) {
            final var end = pos.getEndPosition(cu, part);
            if (end < 0) {
                return;
            }
            final var separator = isSeparator((int) end - 1) ? (int) end - 1 : skipWhitespaceAndComments((int) end);
            if (isSeparator(separator)) {
                slots.add(new Slot(null, next, separator + 1L, skipWhitespaceAndComments(separator + 1), Kind.LEADING, Reach.SAME_LINE));
            }
        }

        private void collectBraceLine(final Tree owner, final long searchFrom, final List<Tree> contents) {
            final var end = pos.getEndPosition(cu, owner);
            if (searchFrom < 0 || end < 0) {
                return;
            }
            final var brace = headerBrace((int) searchFrom);
            if (brace < 0 || brace >= end) {
                return;
            }
            final var firstContent = contents.stream().mapToLong(t -> pos.getStartPosition(cu, t)).filter(start -> start >= 0).min().orElse(end - 1);
            if (firstContent > brace + 1) {
                slots.add(new Slot(null, owner, brace + 1L, firstContent, Kind.BRACE_LINE, Reach.SAME_LINE));
            }
        }

        private void collectHeaderBraceLine(final ClassTree node, final boolean isRecord) {
            final var classStart = pos.getStartPosition(cu, node);
            final var classEnd = pos.getEndPosition(cu, node);
            if (classStart < 0 || classEnd < 0) {
                return;
            }
            final var extendsClause = node.getExtendsClause() == null ? List.<Tree>of() : List.of(node.getExtendsClause());
            final var searchFrom = Stream.of(
                    node.getModifiers().getAnnotations(), node.getTypeParameters(), extendsClause, node.getImplementsClause(),
                    node.getPermitsClause(), isRecord ? recordComponents(node) : List.<Tree>of())
                    .flatMap(List::stream)
                    .mapToLong(t -> pos.getEndPosition(cu, t))
                    .max()
                    .orElse(classStart);
            final var brace = headerBrace((int) Math.max(searchFrom, classStart));
            if (brace < 0) {
                return;
            }
            final var lastTokenEnd = lastTokenEndBefore((int) Math.max(searchFrom, classStart), brace);
            if (brace > lastTokenEnd) {
                slots.add(new Slot(null, node, lastTokenEnd, brace, Kind.BEFORE_BRACE));
            }
            final var firstMember = members(node, isRecord).stream().mapToLong(m -> pos.getStartPosition(cu, m)).min().orElse(classEnd - 1);
            if (firstMember > brace + 1) {
                slots.add(new Slot(null, node, brace + 1L, firstMember, Kind.BRACE_LINE, Reach.SAME_LINE));
            }
        }

        private int headerBrace(final int from) {
            var at = skipWhitespaceAndComments(from);
            while (at < source.length() && source.charAt(at) != '{') {
                at = skipWhitespaceAndComments(at + 1);
            }
            return at < source.length() ? at : -1;
        }

        private int lastTokenEndBefore(final int from, final int brace) {
            var at = skipWhitespaceAndComments(from);
            var lastTokenEnd = from;
            while (at < brace) {
                lastTokenEnd = at + 1;
                at = skipWhitespaceAndComments(at + 1);
            }
            return lastTokenEnd;
        }

        private boolean isSeparator(final int at) {
            return at >= 0 && at < source.length() && (source.charAt(at) == ',' || source.charAt(at) == ';');
        }

        private void collectBeforeInitializerSeparator(final Tree part, final Tree next) {
            if (!(part instanceof VariableTree variable) || variable.getInitializer() == null) {
                return;
            }
            final var initializerEnd = pos.getEndPosition(cu, variable.getInitializer());
            final var end = pos.getEndPosition(cu, variable);
            if (initializerEnd >= 0 && end > initializerEnd) {
                slots.add(new Slot(variable, next, initializerEnd, end, Kind.LEADING));
            }
        }

        @Override
        public Void visitTry(final TryTree node, final Void p) {
            final var resources = node.getResources();
            if (!resources.isEmpty()) {
                final var openParenthesis = source.indexOf('(', (int) pos.getStartPosition(cu, node));
                collectWithLo(node, openParenthesis + 1L, resources, false);
                IntStream.range(0, resources.size()).forEach(i -> collectBeforeResourceSeparator(node, resources, i));
            }
            collectBeforeClosingParenthesis(node, resources);
            return super.visitTry(node, null);
        }

        private void collectBeforeResourceSeparator(final TryTree node, final List<? extends Tree> resources, final int index) {
            final var resource = resources.get(index);
            final var start = pos.getStartPosition(cu, resource);
            final var end = pos.getEndPosition(cu, resource);
            if (start < 0) {
                return;
            }
            final var nameEnd = resource instanceof VariableTree variable && variable.getInitializer() != null
                    ? pos.getEndPosition(cu, variable.getInitializer())
                    : endOfQualifiedName((int) start);
            if (nameEnd < 0 || nameEnd >= end) {
                return;
            }
            final var last = index + 1 == resources.size();
            slots.add(new Slot(resource, last ? node : resources.get(index + 1), nameEnd, end, last ? Kind.TAIL : Kind.LEADING));
        }

        private int endOfQualifiedName(final int from) {
            var at = from;
            while (at < source.length() && (Character.isJavaIdentifierPart(source.charAt(at)) || source.charAt(at) == '.')) {
                at++;
            }
            return at;
        }

        private void collectBeforeClosingParenthesis(final Tree owner, final List<? extends Tree> header) {
            collectBeforeClosingParenthesis(owner, header, Kind.TAIL);
        }

        private void collectBeforeClosingParenthesis(final Tree owner, final List<? extends Tree> header, final Kind kind) {
            if (header.isEmpty()) {
                return;
            }
            final var last = header.getLast();
            final var end = pos.getEndPosition(cu, last);
            if (end < 0) {
                return;
            }
            final var closing = skipWhitespaceAndComments((int) end);
            if (closing > end && closing < source.length() && source.charAt(closing) == ')') {
                slots.add(new Slot(last, owner, end, closing, kind));
            }
        }

        private void collect(final Tree container, final List<? extends Tree> children, final boolean emitTail) {
            collect(container, children, emitTail, false);
        }

        private void collect(final Tree container, final List<? extends Tree> children, final boolean emitTail, final boolean crossesSeparator) {
            if (children.isEmpty() && !emitTail) {
                return;
            }
            collectWithLo(container, pos.getStartPosition(cu, container), children, emitTail, crossesSeparator);
        }

        /**
         * A parameter or argument list, whose renderer places a {@code //} comment left after a
         * comma rather than pushing it down onto the next element. Only lists whose renderer reads
         * the {@code interior} channel may be collected this way — an enum's constants, a type's
         * {@code implements} and {@code permits} lists, a method's {@code throws} list, an
         * annotation's arguments and an array's initializers are the others, through
         * {@link #visitClass}, {@link #visitMethod}, {@link #visitAnnotation} and
         * {@link #visitNewArray} — because a comment routed there by a renderer that never asks for
         * it is a comment the output does not contain.
         */
        private void collectArguments(final Tree call, final long argumentsLo, final List<? extends ExpressionTree> arguments) {
            collectSeparatedList(call, argumentsLo, arguments);
            if (arguments.isEmpty()) {
                return;
            }
            final var lastEnd = pos.getEndPosition(cu, arguments.getLast());
            final var callEnd = pos.getEndPosition(cu, call);
            if (lastEnd >= 0 && callEnd > lastEnd) {
                slots.add(new Slot(arguments.getLast(), call, lastEnd, callEnd, Kind.TAIL, Reach.SAME_LINE));
            }
        }

        private void collectSeparatedList(final Tree container, final long containerLo, final List<? extends Tree> children) {
            if (children.isEmpty()) {
                return;
            }
            collectWithLo(container, containerLo, children, false, true);
        }

        private void collectWithLo(final Tree container, final long containerLo, final List<? extends Tree> children, final boolean emitTail) {
            collectWithLo(container, containerLo, children, emitTail, false);
        }

        private void collectWithLo(final Tree container,
                                   final long containerLo,
                                   final List<? extends Tree> children,
                                   final boolean emitTail,
                                   final boolean crossesSeparator) {
            final var real = children.stream()
                    .filter(c -> pos.getStartPosition(cu, c) >= 0 && pos.getEndPosition(cu, c) >= 0)
                    .sorted(Comparator.comparingLong(t -> pos.getStartPosition(cu, t)))
                    .toList();
            final var containerHi = pos.getEndPosition(cu, container);
            if (real.isEmpty()) {
                if (emitTail && containerHi > containerLo) {
                    slots.add(new Slot(null, container, containerLo, containerHi, Kind.INTERIOR));
                }
                return;
            }
            Tree previous = null;
            var previousEnd = containerLo;
            for (final var child : real) {
                final var childStart = pos.getStartPosition(cu, child);
                slots.add(new Slot(previous, child, previousEnd, childStart, Kind.LEADING, Reach.PRECISE, crossesSeparator));
                final var childEnd = pos.getEndPosition(cu, child);
                addCatchAllSlot(child, childStart, childEnd);
                previous = child;
                previousEnd = childEnd;
            }
            if (emitTail && containerHi > previousEnd) {
                // Separator-aware too: the last element's own comma is inside this slot, so a
                // comment past it — `CLOSED, // the stream is closed` — belongs to that element
                // exactly as it would between two of them.
                slots.add(new Slot(previous, container, previousEnd, containerHi, Kind.TAIL, Reach.PRECISE, crossesSeparator));
            }
        }

        /**
         * A slot spanning {@code child} itself, so a comment sitting somewhere inside it that no
         * finer slot claims — mid-expression, between {@code else} and {@code if}, between the
         * links of a method chain — becomes a leading comment on {@code child} instead of being
         * dropped. Sorting slots by ascending {@code hi} means every finer slot still wins over
         * this one, so precisely placed comments keep their existing placement.
         */
        private void addCatchAllSlot(final Tree child, final long childStart, final long childEnd) {
            if (childEnd > childStart) {
                slots.add(new Slot(null, child, childStart, childEnd, Kind.LEADING, Reach.CATCH_ALL));
            }
        }

        private List<Tree> members(final ClassTree ct, final boolean isRecord) {
            return ct.getMembers()
                    .stream()
                    .filter(CommentAttacher::isAttachmentSite)
                    .filter(m -> pos.getStartPosition(cu, m) >= 0)
                    .filter(m -> !isRecordComponent(isRecord, m))
                    .sorted(Comparator.comparingLong(t -> pos.getStartPosition(cu, t)))
                    .<Tree>map(t -> t)
                    .toList();
        }

        private List<Tree> recordComponents(final ClassTree ct) {
            return ct.getMembers()
                    .stream()
                    .filter(m -> isRecordComponent(true, m))
                    .filter(m -> pos.getStartPosition(cu, m) >= 0)
                    .sorted(Comparator.comparingLong(t -> pos.getStartPosition(cu, t)))
                    .<Tree>map(t -> t)
                    .toList();
        }
    }
}
