package io.github.mortuusars.envelope.util.component;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.random.Weighted;

import java.util.function.Function;

// 1.21.11: Weight and WeightedEntry were removed (weights are plain ints wrapped in Weighted<T>).
// The class keeps its own int weight so the serialized shape stays {component, weight}.
public class WeightedComponent {
    public static final Codec<WeightedComponent> FULL_CODEC = RecordCodecBuilder.create(i -> i.group(
          ComponentSerialization.CODEC.optionalFieldOf("component", Component.empty()).forGetter(WeightedComponent::getComponent),
          ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("weight", 1).forGetter(WeightedComponent::getWeight)
    ).apply(i, WeightedComponent::new));

    public static final Codec<WeightedComponent> CODEC = Codec.either(ComponentSerialization.CODEC, FULL_CODEC)
          .xmap(regularOrWeighted -> regularOrWeighted.map(WeightedComponent::new, Function.identity()),
                WeightedComponent::asEither);

    private final Component component;
    private final int weight;

    public WeightedComponent(Component component, int weight) {
        this.component = component;
        this.weight = weight;
    }

    public WeightedComponent(Component component) {
        this(component, 1);
    }

    public Component getComponent() {
        return component;
    }

    public int getWeight() {
        return weight;
    }

    public Weighted<Component> asWeighted() {
        return new Weighted<>(component, weight);
    }

    public Either<Component, WeightedComponent> asEither() {
        if (getWeight() <= 1) {
            return Either.left(getComponent());
        } else {
            return Either.right(this);
        }
    }
}
