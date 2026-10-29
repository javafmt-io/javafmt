class Parent {

    int value;

    Parent(final int value) {
        this.value = value;
    }
}

class Child extends Parent {

    Child(final int rawValue) {
        final int checked = rawValue > 0 ? rawValue : 0;
        super(checked);
    }
}

class GrandChild extends Child {

    GrandChild(final String text) {
        final int parsed = Integer.parseInt(text.trim());
        super(parsed);
    }
}