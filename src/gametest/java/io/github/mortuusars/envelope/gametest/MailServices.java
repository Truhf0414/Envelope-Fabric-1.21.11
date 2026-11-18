package io.github.mortuusars.envelope.gametest;

import io.github.mortuusars.envelope.Config;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.entity.Pigeon;
import io.github.mortuusars.envelope.world.inventory.StackIngredient;
import io.github.mortuusars.envelope.world.item.PaybackBoxItem;
import io.github.mortuusars.envelope.world.item.SealStampItem;
import io.github.mortuusars.envelope.world.item.SealableItem;
import io.github.mortuusars.envelope.world.item.component.PackageContents;
import io.github.mortuusars.envelope.world.item.component.PaybackDuration;
import io.github.mortuusars.envelope.world.item.component.PaybackRequest;
import io.github.mortuusars.envelope.world.item.component.PaybackSubject;
import io.github.mortuusars.envelope.world.item.component.ServiceLetterMeaning;
import io.github.mortuusars.envelope.world.item.mail.Mail;
import io.github.mortuusars.envelope.world.mail.MailService;
import io.github.mortuusars.envelope.world.mail.address.Address;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import io.github.mortuusars.envelope.world.mail.address.type.ServiceAddress;
import io.github.mortuusars.envelope.world.mail.delivery.CourierOrigin;
import io.github.mortuusars.envelope.world.mail.service.cloud_depository.CloudDepository;
import io.github.mortuusars.envelope.world.mail.service.cloud_depository.CloudDepositoryData;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Port tests (1.21.11): mail sent to the service addresses, through the same entry a player uses
 * (mail in a mailbox's send slot, MailboxBlockEntity#tryStartDelivery with a pigeon), then carried by the real
 * background delivery, drop-off handlers and payback department. Only the waiting is cut: the delivery phase duration
 * modifier is set to 0 (every phase lasts one tick) and couriers are kept from re-appearing in the world, so the whole
 * trip stays in the background. Flying, pathfinding and the courier picking the mail up are not covered here.
 */
final class MailServices {
    private static final BlockAddress SENDER = new BlockAddress("envelope_port_services_sender");
    private static final BlockAddress BUYER = new BlockAddress("envelope_port_services_buyer");

    private MailServices() {
    }

    private interface Case {
        String run() throws Exception;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer, Path results, List<String> problems) {
        List<String> lines = new ArrayList<>();
        singleplayer.getServer().runOnServer(MailServices::setUp);
        try {
            check(lines, problems, "automated supply service: rotten flesh in 6 slots (3,2,2,2,2,2) -> 2 leather; the odd one comes back as returned",
                  () -> supplyService(context, singleplayer));
            check(lines, problems, "mail service: address tag + red dye -> payback tag",
                  () -> mailServicePaybackTag(context, singleplayer));
            check(lines, problems, "equine assurance bureau: gold block -> golden horse armor",
                  () -> equine(context, singleplayer));
            check(lines, problems, "cloud depository: unsealed package is returned (no identity)",
                  () -> cloudUnsealed(context, singleplayer));
            check(lines, problems, "cloud depository: sealed deposit -> report; status request -> report; withdrawal -> the package back",
                  () -> cloudRoundTrip(context, singleplayer));
            check(lines, problems, "payback: seller's letter is held, buyer gets a payback box; short payment is returned; full payment -> letter to buyer, payment to seller",
                  () -> paybackFulfilled(context, singleplayer));
            check(lines, problems, "payback: request that runs out of time returns the letter to the seller",
                  () -> paybackExpired(context, singleplayer));
        } finally {
            singleplayer.getServer().runOnServer(MailServices::tearDown);
        }
        EnvelopeClientGameTest.write(results.resolve("mail_services.txt"), lines);
    }

    private static void check(List<String> lines, List<String> problems, String name, Case test) {
        String failure;
        try {
            failure = test.run();
        } catch (Throwable e) {
            failure = "threw " + e;
        }
        if (failure == null) {
            lines.add("OK    " + name);
        } else {
            lines.add("FAIL  " + name + ": " + failure);
            problems.add("mail services: " + name + ": " + failure);
        }
    }

    // -- set up

    private static void setUp(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Config.Server.DELIVERY_PHASE_DURATION_MODIFIER.set(0.0);
        Config.Server.DELIVERY_SPAWNING_IGNORES_DOMOBSPAWNING_RULE.set(false);
        level.getGameRules().set(GameRules.SPAWN_MOBS, false, server);

        placeMailbox(level, SENDER, senderPos(server));
        placeMailbox(level, BUYER, buyerPos(server));
        // The cloud depository serves known players only; the account is the seal's signature (the player's name)
        MailService.of(level).getKnownPlayers().add(player(server));
    }

    private static void tearDown(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Config.Server.DELIVERY_PHASE_DURATION_MODIFIER.set(1.0);
        Config.Server.DELIVERY_SPAWNING_IGNORES_DOMOBSPAWNING_RULE.set(true);
        level.getGameRules().set(GameRules.SPAWN_MOBS, true, server);
        // Leave nothing behind for the save/reopen comparison that runs later
        MailService service = MailService.of(level);
        service.getPaybackDepartment().getData().getPaybackPendingSubjects().clear();
        service.getPaybackDepartment().getData().setDirty();
        service.getCloudService().getData().accounts().remove(player(server).getScoreboardName());
        service.getCloudService().getData().setDirty();
        level.setBlock(senderPos(server), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(buyerPos(server), Blocks.AIR.defaultBlockState(), 3);
    }

    private static void placeMailbox(ServerLevel level, BlockAddress address, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, Envelope.Blocks.MAILBOX.get().defaultBlockState(), 3);
        ((MailboxBlockEntity) level.getBlockEntity(pos)).setAddress(address);
    }

    private static BlockPos senderPos(MinecraftServer server) {
        return server.overworld().getRespawnData().pos().offset(6, 0, 6);
    }

    private static BlockPos buyerPos(MinecraftServer server) {
        return server.overworld().getRespawnData().pos().offset(9, 0, 6);
    }

    // -- cases

    private static String supplyService(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        int before = inboxSize(singleplayer, SENDER);
        // Like a crafting grid: one slot per ingredient (MailRecipe#matches wants as many filled slots as ingredients),
        // and the stack sizes set how many times it is crafted. 3+2+2+2+2+2 crafts twice and leaves one flesh over.
        send(context, singleplayer, SENDER, server -> {
            List<ItemStack> items = new ArrayList<>();
            items.add(new ItemStack(Items.ROTTEN_FLESH, 3));
            for (int i = 0; i < 5; i++) items.add(new ItemStack(Items.ROTTEN_FLESH, 2));
            return packageTo(server, service(server, ServiceAddress.AUTOMATED_SUPPLY_SERVICE), items);
        });
        List<String> arrived = newMail(singleplayer, SENDER, before);
        List<String> wrong = new ArrayList<>();
        expect(arrived, "package [2 leather]", wrong);
        expect(arrived, "returned package [1 rotten_flesh]", wrong);
        return finish(arrived, wrong, 2);
    }

    private static String mailServicePaybackTag(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        int before = inboxSize(singleplayer, SENDER);
        send(context, singleplayer, SENDER, server -> packageTo(server, service(server, ServiceAddress.MAIL_SERVICE),
              List.of(new ItemStack(Envelope.Items.ADDRESS_TAG.get()), new ItemStack(Items.RED_DYE))));
        List<String> arrived = newMail(singleplayer, SENDER, before);
        List<String> wrong = new ArrayList<>();
        expect(arrived, "package [1 envelope:payback_tag]", wrong);
        return finish(arrived, wrong, 1);
    }

    private static String equine(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        int before = inboxSize(singleplayer, SENDER);
        send(context, singleplayer, SENDER, server -> packageTo(server, service(server, ServiceAddress.EQUINE_ASSURANCE_BUREAU),
              List.of(new ItemStack(Items.GOLD_BLOCK))));
        List<String> arrived = newMail(singleplayer, SENDER, before);
        List<String> wrong = new ArrayList<>();
        expect(arrived, "package [1 golden_horse_armor]", wrong);
        return finish(arrived, wrong, 1);
    }

    private static String cloudUnsealed(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        int before = inboxSize(singleplayer, SENDER);
        send(context, singleplayer, SENDER, server -> packageTo(server, service(server, ServiceAddress.CLOUD_DEPOSITORY),
              List.of(new ItemStack(Items.DIAMOND))));
        List<String> arrived = newMail(singleplayer, SENDER, before);
        List<String> wrong = new ArrayList<>();
        expect(arrived, "returned package [1 diamond]", wrong);
        String stored = singleplayer.getServer().computeOnServer(MailServices::cloudAccount);
        if (!stored.equals("no account")) wrong.add("the account holds " + stored);
        return finish(arrived, wrong, 1);
    }

    private static String cloudRoundTrip(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        List<String> wrong = new ArrayList<>();

        // Deposit
        int before = inboxSize(singleplayer, SENDER);
        send(context, singleplayer, SENDER, server -> sealed(server,
              packageTo(server, service(server, ServiceAddress.CLOUD_DEPOSITORY), List.of(new ItemStack(Items.DIAMOND, 3)))));
        List<String> arrived = newMail(singleplayer, SENDER, before);
        expect(arrived, "sealed letter deposit_report", wrong); // the depository seals its letters
        String stored = singleplayer.getServer().computeOnServer(MailServices::cloudAccount);
        if (!stored.equals("capacity 8, sealed package [3 diamond]")) wrong.add("after deposit the account holds " + stored);
        if (arrived.size() != 1) wrong.add("deposit: " + arrived.size() + " pieces arrived: " + arrived);

        // Status request
        before = inboxSize(singleplayer, SENDER);
        send(context, singleplayer, SENDER, server -> sealed(server,
              serviceLetter(server, CloudDepository.LETTER_MEANING_STATUS_REQUEST)));
        arrived = newMail(singleplayer, SENDER, before);
        expect(arrived, "sealed letter status_report", wrong);
        if (arrived.size() != 1) wrong.add("status: " + arrived.size() + " pieces arrived: " + arrived);

        // Withdrawal request
        before = inboxSize(singleplayer, SENDER);
        send(context, singleplayer, SENDER, server -> sealed(server,
              serviceLetter(server, CloudDepository.LETTER_MEANING_WITHDRAWAL_REQUEST)));
        arrived = newMail(singleplayer, SENDER, before);
        expect(arrived, "sealed package [3 diamond]", wrong);
        if (arrived.size() != 1) wrong.add("withdrawal: " + arrived.size() + " pieces arrived: " + arrived);
        stored = singleplayer.getServer().computeOnServer(MailServices::cloudAccount);
        if (!stored.equals("capacity 8, empty")) wrong.add("after withdrawal the account holds " + stored);

        return wrong.isEmpty() ? null : String.join("; ", wrong);
    }

    private static String paybackFulfilled(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        List<String> wrong = new ArrayList<>();

        // Seller sends a letter that asks for 3 emeralds
        int sellerBefore = inboxSize(singleplayer, SENDER);
        int buyerBefore = inboxSize(singleplayer, BUYER);
        long sentAt = singleplayer.getServer().computeOnServer(server -> server.overworld().getGameTime());
        send(context, singleplayer, SENDER, MailServices::paybackLetter);
        List<String> toBuyer = newMail(singleplayer, BUYER, buyerBefore);
        expect(toBuyer, "payback box", wrong);
        List<String> toSeller = newMail(singleplayer, SENDER, sellerBefore);
        if (!toSeller.isEmpty()) wrong.add("the seller got " + toSeller + " while the request is pending");
        List<PaybackSubject> pending = singleplayer.getServer().computeOnServer(MailServices::pending);
        if (pending.size() != 1) {
            wrong.add(pending.size() + " pending payback subjects");
            return String.join("; ", wrong);
        }
        long expected = PaybackDuration.SHORT.getTicks();
        long lasts = pending.getFirst().expiresAt() - sentAt;
        if (lasts < expected || lasts > expected + 200) wrong.add("the request lasts " + lasts + " ticks, the short duration is " + expected);

        // Buyer pays 2 of 3: returned to the buyer, still pending
        buyerBefore = inboxSize(singleplayer, BUYER);
        send(context, singleplayer, BUYER, server -> payment(server, 2));
        toBuyer = newMail(singleplayer, BUYER, buyerBefore);
        expect(toBuyer, "returned payback package [2 emerald]", wrong);
        if (singleplayer.getServer().computeOnServer(MailServices::pending).size() != 1) wrong.add("a short payment ended the request");

        // Buyer pays 3
        sellerBefore = inboxSize(singleplayer, SENDER);
        buyerBefore = inboxSize(singleplayer, BUYER);
        send(context, singleplayer, BUYER, server -> payment(server, 3));
        toBuyer = newMail(singleplayer, BUYER, buyerBefore);
        toSeller = newMail(singleplayer, SENDER, sellerBefore);
        expect(toBuyer, "letter", wrong);
        expect(toSeller, "package [3 emerald]", wrong);
        if (toBuyer.size() != 1) wrong.add("buyer: " + toBuyer.size() + " pieces arrived: " + toBuyer);
        if (toSeller.size() != 1) wrong.add("seller: " + toSeller.size() + " pieces arrived: " + toSeller);
        if (!singleplayer.getServer().computeOnServer(MailServices::pending).isEmpty()) wrong.add("the request is still pending after payment");

        return wrong.isEmpty() ? null : String.join("; ", wrong);
    }

    private static String paybackExpired(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        List<String> wrong = new ArrayList<>();
        int sellerBefore = inboxSize(singleplayer, SENDER);
        send(context, singleplayer, SENDER, MailServices::paybackLetter);
        if (singleplayer.getServer().computeOnServer(MailServices::pending).size() != 1) return "the request was not held";

        // Time is the one thing cut here: the stored request is made due now. The department checks every 200 ticks.
        singleplayer.getServer().runOnServer(server -> {
            Map<io.github.mortuusars.envelope.world.item.component.Id, PaybackSubject> subjects =
                  MailService.of(server.overworld()).getPaybackDepartment().getData().getPaybackPendingSubjects();
            subjects.replaceAll((id, subject) -> new PaybackSubject(subject.id(), subject.mail(), subject.returnAddress(),
                  server.overworld().getGameTime()));
        });
        for (int i = 0; i < 30 && !singleplayer.getServer().computeOnServer(MailServices::pending).isEmpty(); i++) {
            context.waitTicks(10);
        }
        if (!singleplayer.getServer().computeOnServer(MailServices::pending).isEmpty()) wrong.add("still pending 300 ticks after it was due");
        waitForCouriers(context, singleplayer);
        List<String> toSeller = newMail(singleplayer, SENDER, sellerBefore);
        expect(toSeller, "returned letter", wrong);
        // The buyer's payback box that was sent with the request is left in the buyer's inbox: that is upstream behaviour
        return wrong.isEmpty() ? null : String.join("; ", wrong);
    }

    // -- mail

    private static ItemStack packageTo(MinecraftServer server, Address recipient, List<ItemStack> items) {
        return Mail.createPackage(new PackageContents(items)).addressTag(recipient).itemStack();
    }

    private static ItemStack serviceLetter(MinecraftServer server, ServiceLetterMeaning meaning) {
        // Same as the result of the service_letter/cloud_depository/* crafting recipes
        return Mail.of(Envelope.Items.SERVICE_LETTER)
              .letterContent(Component.literal("envelope port test"))
              .addressTag(service(server, ServiceAddress.CLOUD_DEPOSITORY))
              .serviceLetterMeaning(meaning)
              .itemStack();
    }

    private static ItemStack paybackLetter(MinecraftServer server) {
        ItemStack letter = Mail.of(Envelope.Items.LETTER).addressTag(BUYER).itemStack();
        return Mail.setPaybackRequest(letter, new PaybackRequest(List.of(new StackIngredient(Items.EMERALD, 3)), PaybackDuration.SHORT));
    }

    /** What PaybackPackingMenu#createPackingResult makes of the payback box in the buyer's inbox. */
    private static ItemStack payment(MinecraftServer server, int emeralds) {
        MailboxBlockEntity buyer = mailbox(server, BUYER);
        ItemStack box = buyer.getAllMail().stream().filter(stack -> stack.getItem() instanceof PaybackBoxItem)
              .findFirst().orElseThrow(() -> new IllegalStateException("no payback box in the buyer's inbox"));
        PaybackSubject subject = box.get(Envelope.DataComponents.PAYBACK_SUBJECT);
        ItemStack result = box.transmuteCopy(Envelope.Items.PAYBACK_PACKAGE.get());
        result.set(Envelope.DataComponents.PACKAGE_CONTENTS, new PackageContents(List.of(new ItemStack(Items.EMERALD, emeralds))));
        Mail.removePreviousDeliveryData(result);
        Mail.setRecipient(result, subject.returnAddress());
        return result;
    }

    /** Sealed by the player with a plain seal stamp, as SealStampItem does when used on the item. */
    private static ItemStack sealed(MinecraftServer server, ItemStack mail) {
        ServerPlayer player = player(server);
        ItemStack stamp = new ItemStack(Envelope.Items.SEAL_STAMP.get());
        return ((SealableItem) mail.getItem()).seal(player.level(), mail,
              ((SealStampItem) stamp.getItem()).createSeal(stamp, player));
    }

    // -- sending and waiting

    private interface MailFactory {
        ItemStack create(MinecraftServer server);
    }

    /** Puts the mail into the mailbox's send slot and hands it to a pigeon the way the pigeon's goal does. */
    private static void send(ClientGameTestContext context, TestSingleplayerContext singleplayer, BlockAddress from, MailFactory mail) {
        String failure = singleplayer.getServer().computeOnServer(server -> {
            ServerLevel level = server.overworld();
            MailboxBlockEntity mailbox = mailbox(server, from);
            mailbox.setItem(MailboxBlockEntity.SLOT_MAIL, mail.create(server));
            Pigeon pigeon = Pigeon.createService(level);
            pigeon.setOrigin(CourierOrigin.regular(mailbox.getBlockPos()));
            pigeon.snapTo(mailbox.getBlockPos().above(), 0, 0);
            level.addFreshEntity(pigeon);
            if (!mailbox.tryStartDelivery(pigeon)) {
                pigeon.discard();
                return "the mailbox did not start the delivery";
            }
            pigeon.transitionToBackground(level);
            return null;
        });
        if (failure != null) throw new IllegalStateException(failure);
        waitForCouriers(context, singleplayer);
    }

    private static void waitForCouriers(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        int quiet = 0;
        for (int i = 0; i < 100 && quiet < 3; i++) {
            context.waitTicks(5);
            boolean idle = singleplayer.getServer().computeOnServer(server ->
                  MailService.of(server.overworld()).getBackgroundDelivery().getActiveCouriers().isEmpty());
            quiet = idle ? quiet + 1 : 0;
        }
        if (quiet < 3) throw new IllegalStateException("couriers were still travelling after 500 ticks");
    }

    // -- reading

    private static int inboxSize(TestSingleplayerContext singleplayer, BlockAddress address) {
        return singleplayer.getServer().computeOnServer(server -> mailbox(server, address).getAllMail().size());
    }

    private static List<String> newMail(TestSingleplayerContext singleplayer, BlockAddress address, int before) {
        return singleplayer.getServer().computeOnServer(server -> {
            List<ItemStack> all = mailbox(server, address).getAllMail();
            return all.subList(Math.min(before, all.size()), all.size()).stream().map(MailServices::describe).toList();
        });
    }

    private static String describe(ItemStack stack) {
        String prefix = Mail.isReturned(stack) ? "returned " : "";
        if (stack.has(Envelope.DataComponents.SEAL)) prefix += "sealed ";
        if (stack.getItem() instanceof PaybackBoxItem) return prefix + "payback box";
        if (stack.is(Envelope.Items.PAYBACK_PACKAGE.get())) return prefix + "payback package " + contents(stack);
        if (stack.has(Envelope.DataComponents.PACKAGE_CONTENTS)) return prefix + "package " + contents(stack);
        ServiceLetterMeaning meaning = stack.get(Envelope.DataComponents.SERVICE_LETTER_MEANING);
        if (meaning != null) return prefix + "letter " + meaning.toString().replaceAll(".*cloud_depository/", "").replaceAll("[^a-z_].*", "");
        return prefix + (stack.is(Envelope.Items.LETTER.get()) ? "letter" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private static String contents(ItemStack stack) {
        return "[" + PackageContents.of(stack).getItemsTrimmed().stream()
              .map(item -> item.getCount() + " " + shortId(item))
              .collect(Collectors.joining(", ")) + "]";
    }

    private static String shortId(ItemStack item) {
        String id = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static void expect(List<String> arrived, String what, List<String> wrong) {
        if (!arrived.contains(what)) wrong.add("expected [" + what + "], arrived " + arrived);
    }

    private static String finish(List<String> arrived, List<String> wrong, int count) {
        if (arrived.size() != count) wrong.add(arrived.size() + " pieces arrived, expected " + count + ": " + arrived);
        return wrong.isEmpty() ? null : String.join("; ", wrong);
    }

    private static List<PaybackSubject> pending(MinecraftServer server) {
        return List.copyOf(MailService.of(server.overworld()).getPaybackDepartment().getData().getPaybackPendingSubjects().values());
    }

    private static String cloudAccount(MinecraftServer server) {
        CloudDepositoryData.Account account = MailService.of(server.overworld()).getCloudService().getData().accounts()
              .get(player(server).getScoreboardName());
        if (account == null) return "no account";
        return "capacity " + account.getCapacity() + ", "
              + (account.getItems().isEmpty() ? "empty" : account.getItems().stream().map(MailServices::describe).collect(Collectors.joining(", ")));
    }

    private static MailboxBlockEntity mailbox(MinecraftServer server, BlockAddress address) {
        return (MailboxBlockEntity) MailService.of(server.overworld()).getMailboxes().getBlockEntityOf(address)
              .orElseThrow(() -> new IllegalStateException("mailbox " + address + " is not registered"));
    }

    private static ServiceAddress service(MinecraftServer server, ResourceKey<io.github.mortuusars.envelope.world.mail.service.ServiceAddressDefinition> key) {
        return ServiceAddress.getOrThrow(key, server.registryAccess());
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }
}
