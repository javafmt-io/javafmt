package io.javafmt.lint;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import io.javafmt.parser.ParsedUnit;
import lombok.experimental.UtilityClass;

@UtilityClass
final class Declarations {

    boolean writesItsType(final VariableTree variable, final ParsedUnit unit) {
        return variable.getType() != null && unit.endOf(variable.getType()) >= 0;
    }

    boolean isStarImport(final ImportTree imp) {
        return imp.getQualifiedIdentifier() instanceof MemberSelectTree ms && ms.getIdentifier().contentEquals("*");
    }

    boolean isOneArgumentEquals(final MethodTree method) {
        return method.getName().contentEquals("equals") && method.getParameters().size() == 1;
    }

    boolean isEqualsObject(final MethodTree method) {
        return isOneArgumentEquals(method)
            && method.getParameters().getFirst().getType() instanceof IdentifierTree id
            && id.getName().contentEquals("Object");
    }
}
