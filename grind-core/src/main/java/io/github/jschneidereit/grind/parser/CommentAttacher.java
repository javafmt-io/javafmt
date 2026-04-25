package io.github.jschneidereit.grind.parser;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;

import javax.lang.model.element.Modifier;

import org.jspecify.annotations.Nullable;

final class CommentAttacher {

    private enum Kind { FILE_HEADER, FILE_FOOTER, LEADING, INTERIOR, TAIL }

    private record Slot(@Nullable Tree prev, @Nullable Tree owner, long lo, long hi, Kind kind) {}

    static ParsedUnit attach(
            final CompilationUnitTree tree,
            final String source,
            final SourcePositions positions,
            final List<CommentToken> comments) {

        final var slots = new ArrayList<Slot>();
        collectCompilationUnit(tree, source, positions, slots);
        slots.sort(Comparator.<Slot>comparingLong(Slot::hi).thenComparingLong(Slot::lo));

        final var fileHeader = new ArrayList<CommentToken>();
        final var fileFooter = new ArrayList<CommentToken>();
        final var leading = new IdentityHashMap<Tree, List<CommentToken>>();
        final var trailing = new IdentityHashMap<Tree, List<CommentToken>>();
        final var interior = new IdentityHashMap<Tree, List<CommentToken>>();
        final var tail = new IdentityHashMap<Tree, List<CommentToken>>();

        var idx = 0;
        for (final var c : comments) {
            while (idx < slots.size() && slots.get(idx).hi() < c.end()) {
                idx++;
            }
            if (idx >= slots.size()) {
                break;
            }
            final var slot = slots.get(idx);
            if (c.start() < slot.lo()) {
                continue;
            }
            final var prev = slot.prev();
            if (prev != null && sameLine(source, (int) slot.lo(), c.start())) {
                trailing.computeIfAbsent(prev, k -> new ArrayList<>()).add(c);
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

    private static boolean sameLine(final String source, final int from, final int to) {
        if (from < 0 || to > source.length() || from > to) {
            return false;
        }
        for (var i = from; i < to; i++) {
            final var c = source.charAt(i);
            if (c != ' ' && c != '\t') {
                return false;
            }
        }
        return true;
    }

    private static void collectCompilationUnit(
            final CompilationUnitTree cu,
            final String source,
            final SourcePositions pos,
            final List<Slot> slots) {

        final var cuChildren = new ArrayList<Tree>();
        if (cu.getPackage() != null) {
            cuChildren.add(cu.getPackage());
        }
        cuChildren.addAll(cu.getImports());
        cuChildren.addAll(cu.getTypeDecls());
        final var ordered = cuChildren.stream()
            .filter(t -> pos.getStartPosition(cu, t) >= 0)
            .sorted(Comparator.comparingLong(t -> pos.getStartPosition(cu, t)))
            .toList();
        if (ordered.isEmpty()) {
            return;
        }

        slots.add(new Slot(null, null, 0L, pos.getStartPosition(cu, ordered.get(0)), Kind.FILE_HEADER));

        Tree prev = null;
        var prevEnd = 0L;
        for (final var curr : ordered) {
            final var currStart = pos.getStartPosition(cu, curr);
            if (prev != null) {
                final var kind = (cu.getImports().contains(curr) || cu.getTypeDecls().contains(curr))
                    ? Kind.LEADING : Kind.TAIL;
                slots.add(new Slot(prev, curr, prevEnd, currStart, kind));
            }
            prev = curr;
            prevEnd = pos.getEndPosition(cu, curr);
        }
        slots.add(new Slot(prev, null, prevEnd, source.length(), Kind.FILE_FOOTER));

        new Collector(cu, pos, slots).scan(cu, null);
    }

    private static final class Collector extends TreeScanner<Void, Void> {

        private final CompilationUnitTree cu;
        private final SourcePositions pos;
        private final List<Slot> slots;

        Collector(final CompilationUnitTree cu, final SourcePositions pos, final List<Slot> slots) {
            this.cu = cu;
            this.pos = pos;
            this.slots = slots;
        }

        @Override
        public Void visitClass(final ClassTree node, final Void p) {
            final var isRecord = node.getKind() == Tree.Kind.RECORD;
            collect(node, members(node, isRecord), false);
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
            collect(node, node.getParameters(), false);
            collect(node, node.getTypeParameters(), false);
            collect(node, node.getThrows(), false);
            return super.visitMethod(node, null);
        }

        @Override
        public Void visitMethodInvocation(final MethodInvocationTree node, final Void p) {
            collectAfter(node, node.getMethodSelect(), node.getArguments());
            return super.visitMethodInvocation(node, null);
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
            if (initStart >= 0 && varStart >= 0 && initStart > varStart) {
                slots.add(new Slot(null, init, varStart, initStart, Kind.LEADING));
            }
            if (initEnd >= 0 && varEnd > initEnd) {
                slots.add(new Slot(init, null, initEnd, varEnd, Kind.TAIL));
            }
            return super.visitVariable(node, null);
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
            return super.visitCase(node, null);
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
            if (children.isEmpty() && !emitTail) {
                return;
            }
            collectWithLo(container, pos.getStartPosition(cu, container), children, emitTail);
        }

        private void collectAfter(final Tree container, final Tree headEnd, final List<? extends Tree> children) {
            if (children.isEmpty()) {
                return;
            }
            collectWithLo(container, pos.getEndPosition(cu, headEnd), children, false);
        }

        private void collectWithLo(
                final Tree container,
                final long containerLo,
                final List<? extends Tree> children,
                final boolean emitTail) {
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
            for (final var child : real) {
                final var childStart = pos.getStartPosition(cu, child);
                slots.add(new Slot(prev, child, prevEnd, childStart, Kind.LEADING));
                prev = child;
                prevEnd = pos.getEndPosition(cu, child);
            }
            if (emitTail && containerHi > prevEnd) {
                slots.add(new Slot(prev, container, prevEnd, containerHi, Kind.TAIL));
            }
        }

        private List<Tree> members(final ClassTree ct, final boolean isRecord) {
            return ct.getMembers().stream()
                .filter(CommentAttacher::isAttachmentSite)
                .filter(m -> pos.getStartPosition(cu, m) >= 0)
                .filter(m -> !isRecordComponent(isRecord, m))
                .sorted(Comparator.comparingLong(t -> pos.getStartPosition(cu, t)))
                .<Tree>map(t -> t)
                .toList();
        }

        private List<Tree> recordComponents(final ClassTree ct) {
            return ct.getMembers().stream()
                .filter(m -> isRecordComponent(true, m))
                .filter(m -> pos.getStartPosition(cu, m) >= 0)
                .sorted(Comparator.comparingLong(t -> pos.getStartPosition(cu, t)))
                .<Tree>map(t -> t)
                .toList();
        }
    }

    private static boolean isAttachmentSite(final Tree t) {
        return t instanceof ClassTree
            || t instanceof MethodTree
            || t instanceof VariableTree
            || t instanceof BlockTree;
    }

    private static boolean isRecordComponent(final boolean isRecord, final Tree member) {
        return isRecord
            && member instanceof VariableTree v
            && !v.getModifiers().getFlags().contains(Modifier.STATIC);
    }

    private CommentAttacher() {}
}
