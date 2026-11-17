package io.github.mortuusars.envelope.gametest;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.client.gui.screen.AddressTagScreen;
import io.github.mortuusars.envelope.client.gui.screen.LetterEditScreen;
import io.github.mortuusars.envelope.client.gui.screen.LetterViewScreen;
import io.github.mortuusars.envelope.client.gui.screen.MailboxScreen;
import io.github.mortuusars.envelope.client.gui.screen.PackingScreen;
import io.github.mortuusars.envelope.client.gui.screen.PaybackPackingScreen;
import io.github.mortuusars.envelope.client.gui.screen.PaybackTagScreen;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import io.github.mortuusars.envelope.world.item.component.Id;
import io.github.mortuusars.envelope.world.item.component.PaybackRequest;
import io.github.mortuusars.envelope.world.item.component.PaybackSubject;
import io.github.mortuusars.envelope.world.item.mail.Mail;
import io.github.mortuusars.envelope.world.inventory.StackIngredient;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Supplier;

/**
 * Opens every Envelope screen the way a player would (using the item or the block) and takes a screenshot.
 * A screen that does not open within the timeout is recorded as a problem. Looks are compared by eye in phase 3c.
 */
final class ScreenShots {
    private ScreenShots() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<String> problems) {
        useItem(context, singleplayer, problems, "screen_letter_edit", () -> new ItemStack(Envelope.Items.LETTER_AND_QUILL.get()),
              LetterEditScreen.class, () -> {
                  context.getInput().typeChars("Dear tester, hello from 1.21.11");
                  context.waitTicks(2);
              });
        useItem(context, singleplayer, problems, "screen_letter_view", () -> new ItemStack(Envelope.Items.LETTER.get()),
              LetterViewScreen.class, null);
        useItem(context, singleplayer, problems, "screen_address_tag", () -> new ItemStack(Envelope.Items.ADDRESS_TAG.get()),
              AddressTagScreen.class, () -> {
                  context.getInput().typeChars("env");
                  context.waitTicks(2);
              });
        useItem(context, singleplayer, problems, "screen_payback_tag", () -> new ItemStack(Envelope.Items.PAYBACK_TAG.get()),
              PaybackTagScreen.class, null);
        useItem(context, singleplayer, problems, "screen_packing", () -> new ItemStack(Envelope.Items.PAPER_BOX.get()),
              PackingScreen.class, null);
        useItem(context, singleplayer, problems, "screen_payback_packing", ScreenShots::paybackBox,
              PaybackPackingScreen.class, null);

        mailbox(context, singleplayer, problems);
        tooltips(context, singleplayer, problems);
    }

    /** A payback box only opens when it carries mail that asks for something back. */
    private static ItemStack paybackBox() {
        ItemStack mail = new ItemStack(Envelope.Items.LETTER.get());
        Mail.setPaybackRequest(mail, new PaybackRequest(List.of(StackIngredient.createDefault())));
        ItemStack box = new ItemStack(Envelope.Items.PAYBACK_BOX.get());
        box.set(Envelope.DataComponents.PAYBACK_SUBJECT, new PaybackSubject(Id.createUnsafe(1000, 1), mail,
              new BlockAddress("Port test sender"), Long.MAX_VALUE));
        return box;
    }

    private static void useItem(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<String> problems, String name,
                                Supplier<ItemStack> item, Class<? extends Screen> screen, Runnable whileOpen) {
        try {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.setItemInHand(InteractionHand.MAIN_HAND, item.get());
                player.inventoryMenu.broadcastChanges();
            });
            context.waitTicks(5);
            // Right click like a player would: some screens are opened by the client side of Item#use
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(screen);
            context.waitTicks(5);
            if (whileOpen != null) whileOpen.run();
            context.takeScreenshot(name);
        } catch (Throwable e) {
            problems.add(name + ": " + screen.getSimpleName() + " did not open by using the item -> " + firstLine(e));
        } finally {
            close(context, singleplayer);
        }
    }

    private static void mailbox(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<String> problems) {
        try {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = player.level();
                // At eye height, two blocks ahead: the player looks straight at it
                BlockPos pos = player.blockPosition().offset(0, 1, 2);
                level.setBlock(pos, Envelope.Blocks.MAILBOX.get().defaultBlockState(), 3);
                MailboxBlockEntity mailbox = (MailboxBlockEntity) level.getBlockEntity(pos);
                mailbox.setAddress(new BlockAddress("Port test mailbox"));
                mailbox.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 5));
                mailbox.addMail(new ItemStack(Envelope.Items.LETTER.get()));
                mailbox.addMail(new ItemStack(Envelope.Items.PACKAGE.get()));
                mailbox.addMail(new ItemStack(Items.PAPER));
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.inventoryMenu.broadcastChanges();
            });
            context.waitTicks(10);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(MailboxScreen.class);
            context.waitTicks(10);
            context.takeScreenshot("screen_mailbox");
        } catch (Throwable e) {
            problems.add("screen_mailbox: MailboxScreen did not open by using the block -> " + firstLine(e));
        } finally {
            close(context, singleplayer);
        }
    }

    /** Hovers hotbar slots in the inventory screen to show each custom tooltip component. */
    private static void tooltips(ClientGameTestContext context, TestSingleplayerContext singleplayer, List<String> problems) {
        try {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().clearContent();
                List<ItemStack> items = List.of(
                      new ItemStack(Envelope.Items.LETTER.get()),
                      new ItemStack(Envelope.Items.PACKAGE.get()),
                      new ItemStack(Envelope.Items.PAYBACK_TAG.get()),
                      new ItemStack(Envelope.Items.ADDRESS_TAG.get()),
                      new ItemStack(Envelope.Items.SEAL_STAMP.get()),
                      new ItemStack(Envelope.Items.PAPER_BOX.get()),
                      new ItemStack(Envelope.Items.MAILBOX.get()),
                      new ItemStack(Envelope.Items.LETTER_AND_QUILL.get()),
                      new ItemStack(Envelope.Items.PAYBACK_BOX.get()));
                for (int i = 0; i < items.size(); i++) {
                    player.getInventory().setItem(i, items.get(i));
                }
                player.inventoryMenu.broadcastChanges();
                // The creative inventory is a different screen; tooltips are checked in the survival one
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            });
            context.waitTicks(5);
            context.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
            context.waitForScreen(InventoryScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("inventory_items");

            for (int slot = 0; slot < 9; slot++) {
                int hotbar = slot;
                double[] cursor = context.computeOnClient(client -> {
                    int width = client.getWindow().getGuiScaledWidth();
                    int height = client.getWindow().getGuiScaledHeight();
                    double scale = client.getWindow().getGuiScale();
                    int left = (width - 176) / 2;
                    int top = (height - 166) / 2;
                    return new double[]{(left + 8 + hotbar * 18 + 8) * scale, (top + 142 + 8) * scale};
                });
                context.getInput().setCursorPos(cursor[0], cursor[1]);
                context.waitTicks(3);
                context.takeScreenshot("tooltip_slot_" + slot);
            }
        } catch (Throwable e) {
            problems.add("tooltips: " + firstLine(e));
        } finally {
            close(context, singleplayer);
        }
    }

    private static void close(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        context.runOnClient(client -> {
            if (client.screen != null) {
                client.screen.onClose();
            }
        });
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(3);
        context.setScreen(() -> null);
        singleplayer.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().closeContainer());
        context.waitTicks(3);
    }

    private static String firstLine(Throwable e) {
        String text = String.valueOf(e);
        int end = text.indexOf('\n');
        return end > 0 ? text.substring(0, end) : text;
    }
}
