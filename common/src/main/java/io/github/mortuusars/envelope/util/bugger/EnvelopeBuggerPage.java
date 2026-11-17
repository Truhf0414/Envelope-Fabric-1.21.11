package io.github.mortuusars.envelope.util.bugger;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.lib.mortaar.bugger.screen.page.BuggerScreenPage;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EnvelopeBuggerPage implements BuggerScreenPage {
    @Override
    public String getTitle() {
        return "Envelope";
    }

    @Override
    public List<String> getLeftLines() {
        return Envelope.BuggerData.MAIL_SERVICE.get()
              .map(tag -> {

                  List<String> lines = new ArrayList<>(List.of(
                        "Mailboxes: " + tag.getIntOr("mailboxes", 0),
                        "",
                        "Mail:",
                        "  Dropped: " + tag.getIntOr("dropped_mail_count", 0),
                        "  Awaiting Payback: " + tag.getIntOr("payback_pending_mail_count", 0),
                        "  In Cloud Depository: " + tag.getIntOr("cloud_depository_count", 0),
                        "",
                        "Couriers:",
                        "  Real: " + tag.getIntOr("delivering_couriers", 0),
                        "  Background: " + tag.getIntOr("background_delivering_couriers", 0),
                        "  Finished: " + tag.getIntOr("background_finished_couriers", 0)
                  ));

                  ListTag deliveries = tag.getListOrEmpty("deliveries"); // 1.21.11: getList(key, type) removed
                  if (!deliveries.isEmpty()) {
                      lines.add("");
                      lines.add("Deliveries:");
                      for (Tag delivery : deliveries) {
                          lines.add("  " + delivery.asString().orElse(delivery.toString())); // 1.21.11: Tag#getAsString -> asString()
                      }
                  }

                  return lines;
              })
              .orElse(Collections.emptyList());
    }
}
