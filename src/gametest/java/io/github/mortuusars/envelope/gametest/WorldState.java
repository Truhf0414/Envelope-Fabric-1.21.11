package io.github.mortuusars.envelope.gametest;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.lib.mortaar.util.GameTimeId;
import io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity;
import io.github.mortuusars.envelope.world.item.component.Id;
import io.github.mortuusars.envelope.world.item.component.PaybackSubject;
import io.github.mortuusars.envelope.world.item.component.SealLock;
import io.github.mortuusars.envelope.world.level.saveddata.SealLocks;
import io.github.mortuusars.envelope.world.mail.MailService;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import io.github.mortuusars.envelope.world.mail.address.type.CustomAddress;
import io.github.mortuusars.envelope.world.mail.delivery.Delivery;
import io.github.mortuusars.envelope.world.mail.delivery.background.BackgroundDelivery;
import io.github.mortuusars.envelope.world.mail.payback.PaybackDepartmentData;
import io.github.mortuusars.envelope.world.mail.service.cloud_depository.CloudDepositoryData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Puts one of everything Envelope saves into the world, and describes it as text, so the same description can be
 * taken again after the world was closed and opened ("really restart the server" step of phase 3b).
 */
final class WorldState {
    private static final BlockAddress ADDRESS = new BlockAddress("envelope_port_restart");
    private static final String ACCOUNT = "envelope_port_account";
    private static final String LOCK_OWNER = "envelope_port_lock_owner";
    private static final long FIXED_TIME = 123456L;
    private static final BlockPos MAILBOX_OFFSET = new BlockPos(0, 0, -4);

    private WorldState() {
    }

    static void populate(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        MailService service = MailService.of(level);

        // Mailbox block with an address, an item in its slot and two letters in its inbox
        BlockPos pos = mailboxPos(level);
        level.setBlock(pos, Envelope.Blocks.MAILBOX.get().defaultBlockState(), 3);
        MailboxBlockEntity mailbox = (MailboxBlockEntity) level.getBlockEntity(pos);
        mailbox.setAddress(ADDRESS);
        mailbox.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 9));
        mailbox.addMail(new ItemStack(Envelope.Items.LETTER.get()));
        mailbox.addMail(new ItemStack(Items.PAPER));

        // Player's default address
        service.getKnownPlayers().add(player);
        service.getKnownPlayers().setDefaultAddress(player, mailbox.getAddress());

        // Seal lock
        SealLocks.get(level).lock(new SealLock(LOCK_OWNER, GameTimeId.createChecked(FIXED_TIME, false)));

        // Cloud depository account (typed data inside CustomMailSavedData)
        CloudDepositoryData cloud = service.getCloudService().getData();
        CloudDepositoryData.Account account = cloud.ofAccount(ACCOUNT);
        account.setCapacity(21);
        account.getItems().add(new ItemStack(Items.DIAMOND, 4));
        account.getItems().add(new ItemStack(Envelope.Items.PACKAGE.get()));
        cloud.setDirty();

        // Payback request waiting for an answer
        PaybackDepartmentData payback = service.getPaybackDepartment().getData();
        Id id = Id.createUnsafe(FIXED_TIME, 7);
        payback.getPaybackPendingSubjects().put(id, new PaybackSubject(id, new ItemStack(Envelope.Items.LETTER.get()),
              mailbox.getAddress(), level.getGameTime() + 20L * 60 * 60 * 24));
        payback.setDirty();

        // A background courier on its way (mail sent without a physical courier nearby)
        service.getDeliveryManager().startService(Delivery.draft()
              .deliver(new ItemStack(Envelope.Items.LETTER.get()))
              .from(new CustomAddress(Component.literal("envelope port test")))
              .to(mailbox.getAddress()));
    }

    static Map<String, String> describe(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        MailService service = MailService.of(level);
        Map<String, String> state = new LinkedHashMap<>();

        BlockPos pos = mailboxPos(level);
        state.put("mailbox registration", service.getMailboxes().getPositionOf(ADDRESS).map(BlockPos::toShortString).orElse("none"));
        if (level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox) {
            state.put("mailbox block entity address", String.valueOf(mailbox.getAddress()));
            state.put("mailbox slot 0", mailbox.getItem(0).toString());
            state.put("mailbox inbox", mailbox.getAllMail().stream().map(ItemStack::toString).collect(Collectors.joining(", ")));
        } else {
            state.put("mailbox block entity address", "none");
            state.put("mailbox slot 0", "none");
            state.put("mailbox inbox", "none");
        }

        state.put("player default address", service.getKnownPlayers().getDefaultAddressOf(player).map(String::valueOf).orElse("none"));

        state.put("seal lock", SealLocks.get(level).getLocks().stream()
              .filter(lock -> lock.owner().equals(LOCK_OWNER)).map(String::valueOf).collect(Collectors.joining(", ")));

        CloudDepositoryData.Account account = service.getCloudService().getData().accounts().get(ACCOUNT);
        state.put("cloud depository account", account == null ? "none"
              : "capacity " + account.getCapacity() + ", items " + account.getItems().stream().map(ItemStack::toString).collect(Collectors.joining(", ")));

        state.put("payback pending", service.getPaybackDepartment().getData().getPaybackPendingSubjects().values().stream()
              .map(subject -> subject.id() + " " + subject.mail() + " -> " + subject.returnAddress() + " expires " + subject.expiresAt())
              .sorted().collect(Collectors.joining("; ")));

        BackgroundDelivery background = BackgroundDelivery.get(level, "envelope_background_delivery");
        state.put("background couriers (active)", String.valueOf(background.getActiveCouriers().size()));
        return state;
    }

    /** Fixed spot near the world spawn, so it is the same position (and a loaded chunk) before and after reopening. */
    private static BlockPos mailboxPos(ServerLevel level) {
        return level.getRespawnData().pos().offset(MAILBOX_OFFSET);
    }
}
