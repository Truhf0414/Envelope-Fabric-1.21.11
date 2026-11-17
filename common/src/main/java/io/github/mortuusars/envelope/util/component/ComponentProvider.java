package io.github.mortuusars.envelope.util.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;

import java.util.List;

// 1.21.11: WeightedRandomList became WeightedList<E> of Weighted<E>. The codec is still a plain list of
// WeightedComponent ({component, weight}), which is what WeightedRandomList.codec(FULL_CODEC) wrote.
public record ComponentProvider(List<WeightedComponent> list) {
    public static final Codec<ComponentProvider> CODEC = WeightedComponent.FULL_CODEC.listOf()
          .xmap(ComponentProvider::new, ComponentProvider::list);

    public static final ComponentProvider EMPTY = new ComponentProvider(List.of());

    public static ComponentProvider of(Component component) {
        return new ComponentProvider(List.of(new WeightedComponent(component)));
    }

    public Component get(RandomSource randomSource) {
        return WeightedList.of(list.stream().map(WeightedComponent::asWeighted).toList())
              .getRandom(randomSource)
              .orElse(CommonComponents.EMPTY);
    }
}
