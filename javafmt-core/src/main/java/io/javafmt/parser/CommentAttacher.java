package io.javafmt.parser;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import javax.lang.model.element.Modifier;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

@UtilityClass
final class CommentAttacher {

    /**
     * Orders slots so the innermost slot fires first when two slots enclose the same comment: by
     * ascending {@code hi}, then by {@link Reach}, then ascending {@code lo}.
     */
    private static final Comparator<Slot> SLOT_ORDER = Comparator.<Slot>comparingLong(Slot::hi)
            .thenComparing(Slot::reach)
            .thenComparingLong(Slot::lo);

    ParsedUnit attach(final CompilationUnitTree tree, final String source, final SourcePositions positions, final List<CommentToken> comments) {
        if (comments.isEmpty()) {
            return new ParsedUnit(tree, source, positions, List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of());
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
        var idx = 0;
        for (var c : comments) {
            idx = advancePast(slots, idx, c);
            if (opaque.stream().anyMatch(span -> span[0] <= c.start() && c.end() <= span[1])) {
                continue;
            }
            final var slot = firstEnclosing(slots, idx, c, source);
            if (slot == null) {
                continue;
            }
            final var prev = slot.prev();
            if (prev != null && sameLine(source, (int) slot.lo(), c.start())) {
                trailing.computeIfAbsent(prev, k -> new ArrayList<>()).add(c);
                continue;
            }
            if (trailsPastTheSeparator(slot, c, source, tree, positions) && slot.owner() != null) {
                interior.computeIfAbsent(slot.owner(), k -> new ArrayList<>()).add(c);
                continue;
            }
            switch (slot.kind()) {
                case FILE_HEADER -> fileHeader.add(c);
                case FILE_FOOTER -> fileFooter.add(c);
                case LEADING -> {
                    if (slot.owner() != null) {
                        leading.computeIfAbsent(slot.owner(), k -> new ArrayList<>()).add(c);
                    }
                }
                case INTERIOR -> {
                    if (slot.owner() != null) {
                        interior.computeIfAbsent(slot.owner(), k -> new ArrayList<>()).add(c);
                    }
                }
                case TAIL -> {
                    if (slot.owner() != null) {
                        tail.computeIfAbsent(slot.owner(), k -> new ArrayList<>()).add(c);
                    }
                }
            }
        }
        return new ParsedUnit(tree, source, positions, fileHeader, fileFooter, leading, trailing, interior, tail);
    }

    /**
     * Advances {@code from} to the first slot whose {@code hi} is at least {@code c}'s end.
     * Comments arrive in source order, so {@code hi} is non-decreasing across successive calls
     * and the cursor this returns only ever moves forward.
     */
    private int advancePast(final List<Slot> slots, final int from, final CommentToken c) {
        var idx = from;
        while (idx < slots.size() && slots.get(idx).hi() < c.end()) {
            idx++;
        }
        return idx;
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
        for (var i = from; i < to; i++) {
            final var c = source.charAt(i);
            if (c != ' ' && c != '\t') {
                return false;
            }
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
     * <p>Only {@code //} qualifies. A block comment past the separator, <code>f(a, /* b *&#47; b)</code>,
     * reads as annotating what comes after it and is already attached that way.
     */
    private boolean trailsPastTheSeparator(
            final Slot slot,
            final CommentToken c,
            final String source,
            final CompilationUnitTree cu,
            final SourcePositions pos) {
        if (!slot.crossesSeparator() || slot.prev() == null || !c.text().startsWith("//")) {
            return false;
        }
        final var gap = source.substring((int) slot.lo(), c.start());
        return gap.indexOf('\n') < 0 && gap.chars().allMatch(ch -> ch == ' ' || ch == '\t' || ch == ',' || isTheEnumTerminator(ch, slot, cu, pos));
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
            && slot.prev() instanceof VariableTree v
            && v.getType() != null
            && pos.getEndPosition(cu, v.getType()) < 0;
    }

    private void collectCompilationUnit(
            final CompilationUnitTree cu,
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
        Tree prev = null;
        var prevEnd = 0L;
        for (var curr : ordered) {
            final var currStart = pos.getStartPosition(cu, curr);
            if (prev != null) {
                final var kind = curr != cu.getPackage() ? Kind.LEADING : Kind.TAIL;
                slots.add(new Slot(prev, curr, prevEnd, currStart, kind));
            }
            prev = curr;
            prevEnd = pos.getEndPosition(cu, curr);
        }
        slots.add(new Slot(prev, null, prevEnd, source.length(), Kind.FILE_FOOTER));
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

    private enum Kind { FILE_HEADER, FILE_FOOTER, LEADING, INTERIOR, TAIL }

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
    private record Slot(@Nullable Tree prev, @Nullable Tree owner, long lo, long hi, Kind kind, Reach reach, boolean crossesSeparator) {

        Slot(@Nullable final Tree prev, @Nullable final Tree owner, final long lo, final long hi, final Kind kind) {
            this(prev, owner, lo, hi, kind, Reach.PRECISE, false);
        }

        Slot(@Nullable final Tree prev, @Nullable final Tree owner, final long lo, final long hi, final Kind kind, final Reach reach) {
            this(prev, owner, lo, hi, kind, reach, false);
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
            if (isRecord) {
                collect(node, recordComponents(node), false);
            }
            if (node.getExtendsClause() != null) {
                collect(node, List.of(node.getExtendsClause()), false);
            }
            collect(node, node.getImplementsClause(), false);
            collect(node, node.getPermitsClause(), false);
            collect(node, node.getTypeParameters(), false);
            return super.visitClass(node, null);
        }

        @Override
        public Void visitBlock(final BlockTree node, final Void p) {
            collect(node, node.getStatements(), true);
            return super.visitBlock(node, null);
        }

        @Override
        public Void visitMethod(final MethodTree node, final Void p) {
            collectSeparatedList(node, pos.getStartPosition(cu, node), node.getParameters());
            collect(node, node.getTypeParameters(), false);
            collect(node, node.getThrows(), false);
            return super.visitMethod(node, null);
        }

        @Override
        protected void visitInvocation(final MethodInvocationTree node, final Void p) {
            collectSeparatedList(node, pos.getEndPosition(cu, node.getMethodSelect()), node.getArguments());
            collectChainJoint(node);
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
            collectAfter(node, node.getIdentifier(), node.getArguments());
            return super.visitNewClass(node, null);
        }

        @Override
        public Void visitVariable(final VariableTree node, final Void p) {
            final var init = node.getInitializer();
            if (init == null) {
                return super.visitVariable(node, null);
            }
            final var initStart = pos.getStartPosition(cu, init);
            final var initEnd = pos.getEndPosition(cu, init);
            final var varStart = pos.getStartPosition(cu, node);
            final var varEnd = pos.getEndPosition(cu, node);
            final var afterName = positionAfterTheDeclaredName(node, varStart);
            if (initStart >= 0 && afterName >= 0 && initStart > afterName) {
                slots.add(new Slot(null, init, afterName, initStart, Kind.LEADING));
            }
            if (initEnd >= 0 && varEnd > initEnd) {
                slots.add(new Slot(init, null, initEnd, varEnd, Kind.TAIL));
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
                collect(node, node.getInitializers(), false);
            }
            return super.visitNewArray(node, null);
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
            return super.visitCase(node, null);
        }

        @Override
        public Void visitModule(final ModuleTree node, final Void p) {
            collect(node, node.getDirectives(), false);
            return super.visitModule(node, null);
        }

        @Override
        public Void visitSwitch(final SwitchTree node, final Void p) {
            collect(node, node.getCases(), false);
            return super.visitSwitch(node, null);
        }

        @Override
        public Void visitSwitchExpression(final SwitchExpressionTree node, final Void p) {
            collect(node, node.getCases(), false);
            return super.visitSwitchExpression(node, null);
        }

        private void collectColonFormBody(final CaseTree node) {
            final var stmts = node.getStatements();
            if (stmts.isEmpty()) {
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
            collectWithLo(node, lastLabelEnd, stmts, false);
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
                    slots.add(new Slot(null, node, thenEnd, elseStart, Kind.TAIL));
                }
            }
            collectThenBraceLine(node);
            return super.visitIf(node, null);
        }

        /**
         * The rest of the line the then-branch's <code>{</code> opens, so <code>if (x) { // why</code> keeps
         * its comment on the header line. The slot starts one character past the brace, which is
         * what leaves it holding nothing but whitespace when the comment is on that same line;
         * a comment on any later line falls through to the block's own leading slot and stays
         * above the first statement.
         */
        private void collectThenBraceLine(final IfTree node) {
            if (!(node.getThenStatement() instanceof BlockTree block) || block.getStatements().isEmpty()) {
                return;
            }
            final var braceStart = pos.getStartPosition(cu, block);
            final var firstStatementStart = pos.getStartPosition(cu, block.getStatements().getFirst());
            if (braceStart >= 0 && firstStatementStart > braceStart + 1) {
                slots.add(new Slot(null, node, braceStart + 1, firstStatementStart, Kind.INTERIOR, Reach.SAME_LINE));
            }
        }

        @Override
        public Void visitForLoop(final ForLoopTree node, final Void p) {
            final var parts = new ArrayList<Tree>(node.getInitializer());
            if (node.getCondition() != null) {
                parts.add(node.getCondition());
            }
            parts.addAll(node.getUpdate());
            collect(node, parts, false);
            return super.visitForLoop(node, null);
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

        private void collectAfter(final Tree container, final Tree headEnd, final List<? extends Tree> children) {
            if (children.isEmpty()) {
                return;
            }
            collectWithLo(container, pos.getEndPosition(cu, headEnd), children, false);
        }

        /**
         * A parameter or argument list, whose renderer places a {@code //} comment left after a
         * comma rather than pushing it down onto the next element. Only lists whose renderer reads
         * the {@code interior} channel may be collected this way — an enum's constants are the
         * third, through {@link #visitClass} — because a comment routed there by a renderer that
         * never asks for it is a comment the output does not contain.
         */
        private void collectSeparatedList(final Tree container, final long containerLo, final List<? extends Tree> children) {
            if (children.isEmpty()) {
                return;
            }
            collectWithLo(container, containerLo, children, false, true);
        }

        private void collectWithLo(final Tree container, final long containerLo, final List<? extends Tree> children, final boolean emitTail) {
            collectWithLo(container, containerLo, children, emitTail, false);
        }

        private void collectWithLo(
                final Tree container,
                final long containerLo,
                final List<? extends Tree> children,
                final boolean emitTail,
                final boolean crossesSeparator) {
            final var real = children.stream()
                    .filter(c -> pos.getStartPosition(cu, c) >= 0)
                    .sorted(Comparator.comparingLong(t -> pos.getStartPosition(cu, t)))
                    .toList();
            final var containerHi = pos.getEndPosition(cu, container);
            if (real.isEmpty()) {
                if (emitTail && containerHi > containerLo) {
                    slots.add(new Slot(null, container, containerLo, containerHi, Kind.INTERIOR));
                }
                return;
            }
            Tree prev = null;
            var prevEnd = containerLo;
            for (var child : real) {
                final var childStart = pos.getStartPosition(cu, child);
                slots.add(new Slot(prev, child, prevEnd, childStart, Kind.LEADING, Reach.PRECISE, crossesSeparator));
                final var childEnd = pos.getEndPosition(cu, child);
                addCatchAllSlot(child, childStart, childEnd);
                prev = child;
                prevEnd = childEnd;
            }
            if (emitTail && containerHi > prevEnd) {
                // Separator-aware too: the last element's own comma is inside this slot, so a
                // comment past it — `CLOSED, // the stream is closed` — belongs to that element
                // exactly as it would between two of them.
                slots.add(new Slot(prev, container, prevEnd, containerHi, Kind.TAIL, Reach.PRECISE, crossesSeparator));
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
