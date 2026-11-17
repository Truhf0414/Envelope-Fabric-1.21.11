package io.github.mortuusars.envelope.world.block.mailbox;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MailboxesSavedData extends SavedData {
    public static final Codec<MailboxesSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
          Codec.unboundedMap(BlockAddress.STRING_CODEC, RegisteredMailbox.CODEC)
                .optionalFieldOf("mailboxes", Collections.emptyMap()).forGetter(MailboxesSavedData::getMailboxes)
    ).apply(instance, MailboxesSavedData::new));

    private final HashMap<BlockAddress, RegisteredMailbox> mailboxes;

    protected MailboxesSavedData(Map<BlockAddress, RegisteredMailbox> mailboxes) {
        this.mailboxes = new HashMap<>(mailboxes); // Make sure it's mutable
    }

    protected MailboxesSavedData() {
        this(new HashMap<>());
    }

    public HashMap<BlockAddress, RegisteredMailbox> getMailboxes() {
        return mailboxes;
    }

    // -- Save / Load

    // 1.21.11: SavedData.Factory and SavedData#save were replaced by SavedDataType. DimensionDataStorage now encodes
    // and decodes CODEC itself (with registry-aware ops) and logs parse failures. DataFixTypes stays null like upstream.
    public static final SavedDataType<MailboxesSavedData> TYPE =
          new SavedDataType<>("envelope_mailboxes", MailboxesSavedData::new, CODEC, null);

    public static MailboxesSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}