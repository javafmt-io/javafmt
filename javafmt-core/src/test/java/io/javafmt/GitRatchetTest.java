package io.javafmt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitRatchetTest {
    @TempDir Path dir;

    private GitRepository committedRepository() {
        final var repository = GitRepository.init(dir);
        repository.write("Order.java", "class Order {}\n");
        repository.write("Invoice.java", "class Invoice {}\n");
        repository.write(".gitignore", "Ignored.java\n");
        repository.commitAll("initial");
        return repository;
    }

    @Test
    void keepsATrackedFileModifiedInTheWorkingTreeAndDropsAnUnchangedOne() {
        final var repository = committedRepository();
        final var order = repository.write("Order.java", "class Order { int total; }\n");
        final var invoice = dir.resolve("Invoice.java");
        assertThat(GitRatchet.since("main", dir).keep(List.of(order, invoice))).containsExactly(order);
    }

    @Test
    void keepsAStagedFile() {
        final var repository = committedRepository();
        final var invoice = repository.write("Invoice.java", "class Invoice { int total; }\n");
        repository.git("add", "Invoice.java");
        assertThat(GitRatchet.since("main", dir).keep(List.of(invoice, dir.resolve("Order.java")))).containsExactly(invoice);
    }

    @Test
    void keepsAnUntrackedFile() {
        final var repository = committedRepository();
        final var customer = repository.write("src/Customer.java", "class Customer {}\n");
        assertThat(GitRatchet.since("main", dir).keep(List.of(customer))).containsExactly(customer);
    }

    @Test
    void dropsAnIgnoredFile() {
        final var repository = committedRepository();
        final var ignored = repository.write("Ignored.java", "class Ignored {}\n");
        assertThat(GitRatchet.since("main", dir).keep(List.of(ignored))).isEmpty();
    }

    @Test
    void keepsAFileCommittedOnTheBranchSinceItLeftTheRef() {
        final var repository = committedRepository();
        repository.git("checkout", "--quiet", "-b", "feature");
        final var order = repository.write("Order.java", "class Order { int total; }\n");
        repository.commitAll("change order");
        assertThat(GitRatchet.since("main", dir).keep(List.of(order, dir.resolve("Invoice.java")))).containsExactly(order);
    }

    @Test
    void dropsAFileChangedOnlyOnTheRefAfterTheMergeBase() {
        final var repository = committedRepository();
        repository.git("checkout", "--quiet", "-b", "feature");
        repository.git("checkout", "--quiet", "main");
        repository.write("Invoice.java", "class Invoice { int total; }\n");
        repository.commitAll("change invoice on main");
        repository.git("checkout", "--quiet", "feature");
        assertThat(GitRatchet.since("main", dir).keep(List.of(dir.resolve("Invoice.java"), dir.resolve("Order.java")))).isEmpty();
    }

    @Test
    void leavesADeletedFileOut() throws Exception {
        final var repository = committedRepository();
        repository.delete("Invoice.java");
        assertThat(GitRatchet.since("main", dir).changedFiles()).doesNotContain(dir.toRealPath().resolve("Invoice.java")).isEmpty();
    }

    @Test
    void worksFromAProjectInASubdirectoryOfTheRepository() {
        final var repository = committedRepository();
        final var customer = repository.write("services/billing/src/Customer.java", "class Customer {}\n");
        repository.commitAll("add billing");
        repository.write("services/billing/src/Customer.java", "class Customer { int id; }\n");
        assertThat(GitRatchet.since("HEAD", dir.resolve("services/billing")).keep(List.of(customer, dir.resolve("Order.java"))))
                .containsExactly(customer);
    }

    @Test
    void anUnknownRefIsAnError() {
        committedRepository();
        assertThatThrownBy(() -> GitRatchet.since("no-such-branch", dir))
                .isInstanceOf(GitRatchetException.class)
                .hasMessage("unknown git ref: no-such-branch");
    }

    @Test
    void aRefThatLooksLikeAnOptionIsAnUnknownRef() {
        committedRepository();
        assertThatThrownBy(() -> GitRatchet.since("--all", dir))
                .isInstanceOf(GitRatchetException.class)
                .hasMessage("unknown git ref: --all");
    }

    @Test
    void aDirectoryOutsideAnyRepositoryIsAnError() {
        assertThatThrownBy(() -> GitRatchet.since("main", dir))
                .isInstanceOf(GitRatchetException.class)
                .hasMessageStartingWith("not inside a git repository: " + dir);
    }

    @Test
    void aMissingGitExecutableIsAnError() {
        committedRepository();
        assertThatThrownBy(() -> GitRatchet.since("main", dir, "javafmt-no-such-git"))
                .isInstanceOf(GitRatchetException.class)
                .hasMessage("git is not installed or not on the PATH: javafmt-no-such-git");
    }
}
