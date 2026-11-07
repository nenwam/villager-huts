package io.github.nenwam.villagerhuts.entity.goal;

import net.minecraft.network.chat.Component;

/** A worker goal that can say what the worker is doing while it runs (shown in the worker management screen). */
public interface DescribesActivity {
    Component describeActivity();
}
