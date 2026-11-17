package io.github.mortuusars.envelope.test.cases;

import io.github.mortuusars.envelope.lib.mortaar.bugger.test.BuggerTests;

public class EnvelopeBuggerTests {
    public static BuggerTests create() {
        return new BuggerTests()
              .addFrom(new StackIngredientTests())
              .addFrom(new CourierDeliveryTests())
              .addFrom(new MailCraftingRecipeTests())
              .addFrom(new MailCraftingTests())
              // Port tests (1.21.11, phase 2 D7)
              .addFrom(new SaveLoadTests())
              .addFrom(new BlockRemovalTests());
    }
}
