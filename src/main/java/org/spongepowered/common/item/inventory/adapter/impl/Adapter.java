/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.item.inventory.adapter.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Streams;
import net.minecraft.inventory.IInventory;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.Container;
import org.spongepowered.api.item.inventory.EmptyInventory;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.InventoryProperty;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.item.inventory.property.InventoryTitle;
import org.spongepowered.api.item.inventory.transaction.InventoryTransactionResult;
import org.spongepowered.api.item.inventory.transaction.InventoryTransactionResult.Type;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.translation.Translation;
import org.spongepowered.common.item.inventory.EmptyInventoryImpl;
import org.spongepowered.common.item.inventory.adapter.InventoryAdapter;
import org.spongepowered.common.item.inventory.custom.CustomInventory;
import org.spongepowered.common.item.inventory.lens.Fabric;
import org.spongepowered.common.item.inventory.lens.Lens;
import org.spongepowered.common.item.inventory.lens.LensProvider;
import org.spongepowered.common.item.inventory.lens.SlotProvider;
import org.spongepowered.common.item.inventory.lens.impl.DefaultEmptyLens;
import org.spongepowered.common.item.inventory.lens.impl.DefaultIndexedLens;
import org.spongepowered.common.item.inventory.lens.impl.collections.SlotCollection;
import org.spongepowered.common.item.inventory.lens.slots.SlotLens;
import org.spongepowered.common.item.inventory.observer.InventoryEventArgs;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;
import org.spongepowered.common.text.SpongeTexts;
import org.spongepowered.common.text.translation.SpongeTranslation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class Adapter implements MinecraftInventoryAdapter {

    public static final Translation DEFAULT_NAME = new SpongeTranslation("inventory.default.title");

    /**
     * All inventories have their own empty inventory with themselves as the
     * parent. This empty inventory is initialised on-demand but returned for
     * every query which fails. This saves us from creating a new empty
     * inventory with this inventory as the parent for every failed query.
     */
    private EmptyInventory empty;

    @Nullable
    protected Inventory parent;
    protected Inventory next;

    protected final Fabric<IInventory> inventory;
    protected final SlotCollection slots;
    protected final Lens<IInventory, net.minecraft.item.ItemStack> lens;
    protected final List<Inventory> children = new ArrayList<Inventory>();
    protected Iterable<Slot> slotIterator;

    /**
     * Used to calculate {@link #getPlugin()}.
     */
    private final Container rootContainer;

    public Adapter(Fabric<IInventory> inventory, net.minecraft.inventory.Container container) {
        this.inventory = inventory;
        this.parent = this;
        this.slots = this.initSlots(inventory, this);
        this.lens = checkNotNull(this.initRootLens(), "root lens");
        this.rootContainer = (Container) container;
    }

    public Adapter(Fabric<IInventory> inventory, @Nullable Lens<IInventory, net.minecraft.item.ItemStack> root, @Nullable Inventory parent) {
        this.inventory = inventory;
        this.parent = parent == null ? this : parent;
        this.lens = root != null ? root : checkNotNull(this.initRootLens(), "root lens");
        this.slots = this.initSlots(inventory, parent);
        this.rootContainer = null;
    }

    protected SlotCollection initSlots(Fabric<IInventory> inventory, @Nullable Inventory parent) {
        if (parent instanceof InventoryAdapter) {
            @SuppressWarnings("unchecked")
            SlotProvider<IInventory, net.minecraft.item.ItemStack> slotProvider = ((InventoryAdapter<IInventory, net.minecraft.item.ItemStack>)parent).getSlotProvider();
            if (slotProvider instanceof SlotCollection) {
                return (SlotCollection) slotProvider;
            }
        }
        return new SlotCollection(inventory.getSize());
    }

    @Override
    public Inventory parent() {
        return this.parent;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Inventory> T first() {
        return (T) this.iterator().next();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Inventory> T next() {
        return (T) this.emptyInventory(); // TODO implement me
    }

//    protected Inventory generateParent(Lens<IInventory, net.minecraft.item.ItemStack> root) {
//        Lens<IInventory, net.minecraft.item.ItemStack> parentLens = root.getParent();
//        if (parentLens == null) {
//            return this;
//        }
//        return parentLens.getAdapter(this.inventory);
//    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Lens<IInventory, net.minecraft.item.ItemStack> initRootLens() {
        if (this instanceof LensProvider) {
            return ((LensProvider) this).getRootLens(this.inventory, this);
        }
        int size = this.inventory.getSize();
        if (size == 0) {
            return new DefaultEmptyLens(this);
        }
        return new DefaultIndexedLens(0, size, this, this.slots);
    }

    @Override
    public SlotProvider<IInventory, net.minecraft.item.ItemStack> getSlotProvider() {
        return this.slots;
    }

    @Override
    public Lens<IInventory, net.minecraft.item.ItemStack> getRootLens() {
        return this.lens;
    }

    @Override
    public Fabric<IInventory> getInventory() {
        return this.inventory;
    }

    @Override
    public Inventory getChild(int index) {
        if (index < 0 || index >= this.lens.getChildren().size()) {
            throw new IndexOutOfBoundsException("No child at index: " + index);
        }
        while (index >= this.children.size()) {
            this.children.add(null);
        }
        Inventory child = this.children.get(index);
        if (child == null) {
            child = this.lens.getChildren().get(index).getAdapter(this.inventory, this);
            this.children.set(index, child);
        }
        return child;
    }

    @Override
    public Inventory getChild(Lens<IInventory, net.minecraft.item.ItemStack> lens) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void notify(Object source, InventoryEventArgs eventArgs) {
    }

    protected final EmptyInventory emptyInventory() {
        if (this.empty == null) {
            this.empty = new EmptyInventoryImpl(this);
        }
        return this.empty;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Inventory> Iterable<T> slots() {
        if (this.slotIterator == null) {
            this.slotIterator = this.slots.getIterator(this);
        }
        return (Iterable<T>) this.slotIterator;
    }

    @Override
    public void clear() {
        this.slots().forEach(Inventory::clear);
    }

    public static Optional<Slot> forSlot(Fabric<IInventory> inv, SlotLens<IInventory, net.minecraft.item.ItemStack> slotLens, Inventory parent) {
        return slotLens == null ? Optional.<Slot>empty() : Optional.<Slot>ofNullable((Slot) slotLens.getAdapter(inv, parent));
    }

    @Override
    public PluginContainer getPlugin() {
        if (this.parent != this) {
            return this.parent.getPlugin();
        }
        if (this.rootContainer == null) {
            return null;
        }
        return this.rootContainer.getPlugin();
    }
}
