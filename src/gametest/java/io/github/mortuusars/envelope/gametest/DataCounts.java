package io.github.mortuusars.envelope.gametest;

import io.github.mortuusars.envelope.Envelope;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Port tests (1.21.11, phase 4): for every kind of data Envelope ships, the number of files in the data pack must
 * equal the number of entries the game actually loaded. "No error in the log" does not show that: a file in the
 * wrong folder, or one a codec quietly rejects, is simply not there.
 * Files are listed through the server's resource manager, loaded entries through the matching registry or manager,
 * and the ids that are in one list but not the other are printed.
 */
final class DataCounts {
    private DataCounts() {
    }

    static void check(TestSingleplayerContext singleplayer, Path results, List<String> problems, boolean required) {
        List<String> lines = singleplayer.getServer().computeOnServer(DataCounts::collect);
        EnvelopeClientGameTest.write(results.resolve("data_counts.txt"), lines);
        List<String> mismatches = lines.stream().filter(line -> line.startsWith("MISMATCH") || line.startsWith("EMPTY")).toList();
        if (required && !mismatches.isEmpty()) {
            problems.add("data counts:\n  " + String.join("\n  ", mismatches));
        }
    }

    private static List<String> collect(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        ResourceManager resources = server.getResourceManager();

        compare(lines, "recipe", unconditional(resources, "recipe", lines),
              server.getRecipeManager().getRecipes().stream().map(holder -> holder.id().identifier()));
        compare(lines, "advancement", files(resources, "advancement", ".json"),
              server.getAdvancements().getAllAdvancements().stream().map(holder -> holder.id()));
        compare(lines, "loot_table", files(resources, "loot_table", ".json"),
              server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds().map(ResourceKey::identifier));

        registry(lines, server, resources, Registries.STRUCTURE, "worldgen/structure");
        registry(lines, server, resources, Registries.STRUCTURE_SET, "worldgen/structure_set");
        registry(lines, server, resources, Registries.TEMPLATE_POOL, "worldgen/template_pool");
        registry(lines, server, resources, Envelope.Registries.PIGEON_VARIANT, "envelope/pigeon_variant");
        registry(lines, server, resources, Envelope.Registries.SEAL_MATERIAL, "envelope/seal_material");
        registry(lines, server, resources, Envelope.Registries.SEAL_SYMBOL, "envelope/seal_symbol");
        registry(lines, server, resources, Envelope.Registries.SERVICE_ADDRESS_DEFINITION, "envelope/service_address");

        // Structure pieces are loaded on demand: every .nbt file must load
        Set<Identifier> templateFiles = files(resources, "structure", ".nbt");
        compare(lines, "structure template (.nbt)", templateFiles,
              templateFiles.stream().filter(id -> server.getStructureManager().get(id).isPresent()));

        // Tags: every tag file in the envelope namespace must exist as a bound tag in its registry
        tags(lines, server, resources, Registries.ITEM, "tags/item");
        tags(lines, server, resources, Registries.BLOCK, "tags/block");
        tags(lines, server, resources, Registries.ENTITY_TYPE, "tags/entity_type");
        tags(lines, server, resources, Registries.DAMAGE_TYPE, "tags/damage_type");
        tags(lines, server, resources, Registries.BIOME, "tags/worldgen/biome");
        tags(lines, server, resources, Registries.STRUCTURE, "tags/worldgen/structure");
        tags(lines, server, resources, Envelope.Registries.SEAL_SYMBOL, "tags/envelope/seal_symbol");
        tags(lines, server, resources, Envelope.Registries.SERVICE_ADDRESS_DEFINITION, "tags/envelope/service_address");
        return lines;
    }

    private static <T> void registry(List<String> lines, MinecraftServer server, ResourceManager resources,
                                     ResourceKey<? extends Registry<T>> key, String folder) {
        compare(lines, folder, files(resources, folder, ".json"),
              server.registryAccess().lookupOrThrow(key).listElementIds().map(ResourceKey::identifier));
    }

    private static <T> void tags(List<String> lines, MinecraftServer server, ResourceManager resources,
                                 ResourceKey<? extends Registry<T>> key, String folder) {
        compare(lines, folder, files(resources, folder, ".json"),
              server.registryAccess().lookupOrThrow(key).listTagIds().map(tag -> tag.location()));
    }

    /** Ids of the files under {@code data/envelope/<folder>/}. */
    private static Set<Identifier> files(ResourceManager resources, String folder, String extension) {
        FileToIdConverter converter = new FileToIdConverter(folder, extension);
        return converter.listMatchingResources(resources).keySet().stream()
              .filter(file -> file.getNamespace().equals(Envelope.ID))
              .map(converter::fileToId)
              .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Recipe files without the ones Fabric's load conditions switch off in this environment (for example a recipe
     * that needs another mod). Those are meant not to load; they are listed so nothing disappears silently.
     */
    private static Set<Identifier> unconditional(ResourceManager resources, String folder, List<String> lines) {
        FileToIdConverter converter = new FileToIdConverter(folder, ".json");
        Set<Identifier> ids = new TreeSet<>();
        converter.listMatchingResources(resources).forEach((file, resource) -> {
            if (!file.getNamespace().equals(Envelope.ID)) return;
            Identifier id = converter.fileToId(file);
            try (java.io.Reader reader = resource.openAsReader()) {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("fabric:load_conditions")) {
                    lines.add("NOTE      " + folder + ": " + id.getPath() + " has fabric:load_conditions "
                          + json.get("fabric:load_conditions") + " and is not counted");
                    return;
                }
            } catch (Exception e) {
                lines.add("MISMATCH  " + folder + ": " + id.getPath() + " could not be read: " + e);
            }
            ids.add(id);
        });
        return ids;
    }

    private static void compare(List<String> lines, String kind, Set<Identifier> files, java.util.stream.Stream<Identifier> loadedIds) {
        Set<Identifier> loaded = loadedIds.filter(id -> id.getNamespace().equals(Envelope.ID)).collect(Collectors.toCollection(TreeSet::new));
        Set<Identifier> notLoaded = new TreeSet<>(files);
        notLoaded.removeAll(loaded);
        Set<Identifier> noFile = new TreeSet<>(loaded);
        noFile.removeAll(files);

        Function<Set<Identifier>, String> list = ids -> ids.stream().map(Identifier::getPath).collect(Collectors.joining(", "));
        if (files.isEmpty() && loaded.isEmpty()) {
            lines.add("EMPTY     " + kind + ": no files found and nothing loaded, this check looked at nothing");
        } else if (notLoaded.isEmpty() && noFile.isEmpty()) {
            lines.add("OK        " + kind + ": " + files.size() + " files, " + loaded.size() + " loaded");
        } else {
            lines.add("MISMATCH  " + kind + ": " + files.size() + " files, " + loaded.size() + " loaded"
                  + (notLoaded.isEmpty() ? "" : " | file exists but not loaded: " + list.apply(notLoaded))
                  + (noFile.isEmpty() ? "" : " | loaded without a file here: " + list.apply(noFile)));
        }
    }
}
