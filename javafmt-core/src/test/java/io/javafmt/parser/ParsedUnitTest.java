package io.javafmt.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

class ParsedUnitTest {

    @Test
    void leadingListsAreDeeplyImmutableAfterConstruction() {
        final var parsed = JavaParser.parseUnit("class C {}");
        final var cls = (ClassTree) parsed.tree().getTypeDecls().getFirst();
        final var comment = new CommentToken(0, 2, "//", 0);
        final var mutableList = new ArrayList<CommentToken>();
        mutableList.add(comment);
        final var leadingMap = new IdentityHashMap<Tree, List<CommentToken>>();
        leadingMap.put(cls, mutableList);

        final var unit = new ParsedUnit(
            parsed.tree(), parsed.source(), parsed.sourcePositions(),
            List.of(), List.of(),
            leadingMap, new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>());

        mutableList.add(new CommentToken(3, 5, "//", 0));

        assertThat(unit.leadingOf(cls)).hasSize(1);
    }

    @Test
    void trailingListsAreDeeplyImmutableAfterConstruction() {
        final var parsed = JavaParser.parseUnit("class C {}");
        final var cls = (ClassTree) parsed.tree().getTypeDecls().getFirst();
        final var comment = new CommentToken(0, 2, "//", 0);
        final var mutableList = new ArrayList<CommentToken>();
        mutableList.add(comment);
        final var trailingMap = new IdentityHashMap<Tree, List<CommentToken>>();
        trailingMap.put(cls, mutableList);

        final var unit = new ParsedUnit(
            parsed.tree(), parsed.source(), parsed.sourcePositions(),
            List.of(), List.of(),
            new IdentityHashMap<>(), trailingMap, new IdentityHashMap<>(), new IdentityHashMap<>());

        mutableList.add(new CommentToken(3, 5, "//", 0));

        assertThat(unit.trailingOf(cls)).hasSize(1);
    }

    @Test
    void interiorListsAreDeeplyImmutableAfterConstruction() {
        final var parsed = JavaParser.parseUnit("class C {}");
        final var cls = (ClassTree) parsed.tree().getTypeDecls().getFirst();
        final var comment = new CommentToken(0, 2, "//", 0);
        final var mutableList = new ArrayList<CommentToken>();
        mutableList.add(comment);
        final var interiorMap = new IdentityHashMap<Tree, List<CommentToken>>();
        interiorMap.put(cls, mutableList);

        final var unit = new ParsedUnit(
            parsed.tree(), parsed.source(), parsed.sourcePositions(),
            List.of(), List.of(),
            new IdentityHashMap<>(), new IdentityHashMap<>(), interiorMap, new IdentityHashMap<>());

        mutableList.add(new CommentToken(3, 5, "//", 0));

        assertThat(unit.interiorOf(cls)).hasSize(1);
    }

    @Test
    void tailListsAreDeeplyImmutableAfterConstruction() {
        final var parsed = JavaParser.parseUnit("class C {}");
        final var cls = (ClassTree) parsed.tree().getTypeDecls().getFirst();
        final var comment = new CommentToken(0, 2, "//", 0);
        final var mutableList = new ArrayList<CommentToken>();
        mutableList.add(comment);
        final var tailMap = new IdentityHashMap<Tree, List<CommentToken>>();
        tailMap.put(cls, mutableList);

        final var unit = new ParsedUnit(
            parsed.tree(), parsed.source(), parsed.sourcePositions(),
            List.of(), List.of(),
            new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>(), tailMap);

        mutableList.add(new CommentToken(3, 5, "//", 0));

        assertThat(unit.tailOf(cls)).hasSize(1);
    }
}
