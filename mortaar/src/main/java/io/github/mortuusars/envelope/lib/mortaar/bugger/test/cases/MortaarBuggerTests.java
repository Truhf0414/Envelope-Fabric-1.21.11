package io.github.mortuusars.envelope.lib.mortaar.bugger.test.cases;

import io.github.mortuusars.envelope.lib.mortaar.bugger.test.BuggerTests;

public class MortaarBuggerTests {
    public static BuggerTests createTests() {
        return new BuggerTests()
              .addFrom(new GameTimeIdTests());
    }
}
