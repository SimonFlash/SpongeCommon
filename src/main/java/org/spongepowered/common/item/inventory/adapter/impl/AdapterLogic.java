package org.spongepowered.common.item.inventory.adapter.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Streams;
import net.minecraft.inventory.IInventory;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.Container;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.InventoryProperty;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.property.InventoryTitle;
import org.spongepowered.api.item.inventory.transaction.InventoryTransactionResult;
import org.spongepowered.api.text.Text;
import org.spongepowered.common.item.inventory.adapter.InventoryAdapter;
import org.spongepowered.common.item.inventory.custom.CustomInventory;
import org.spongepowered.common.item.inventory.lens.Fabric;
import org.spongepowered.common.item.inventory.lens.Lens;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;
import org.spongepowered.common.text.SpongeTexts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class AdapterLogic {

    private AdapterLogic() {}

    public static Optional<ItemStack> pollSequential(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter) {
        return AdapterLogic.pollSequential(adapter.getInventory(), adapter.getRootLens());
    }

    public static Optional<ItemStack> pollSequential(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens) {
        return AdapterLogic.findStack(inv, lens, true);
    }

    public static Optional<ItemStack> pollSequential(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, int limit) {
        return AdapterLogic.pollSequential(adapter.getInventory(), adapter.getRootLens(), limit);
    }

    public static Optional<ItemStack> pollSequential(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, int limit) {
        return AdapterLogic.findStacks(inv, lens, limit, true);
    }

    public static Optional<ItemStack> peekSequential(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter) {
        return AdapterLogic.peekSequential(adapter.getInventory(), adapter.getRootLens());
    }

    public static Optional<ItemStack> peekSequential(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens) {
        return AdapterLogic.findStack(inv, lens, false);
    }

    public static Optional<ItemStack> peekSequential(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, int limit) {
        return AdapterLogic.peekSequential(adapter.getInventory(), adapter.getRootLens(), limit);
    }

    public static Optional<ItemStack> peekSequential(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, int limit) {
        return AdapterLogic.findStacks(inv, lens, limit, false);
    }

    private static Optional<ItemStack> findStack(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, boolean remove) {
        if (lens == null) {
            return Optional.empty();
        }
        for (int ord = 0; ord < lens.slotCount(); ord++) {
            net.minecraft.item.ItemStack stack = lens.getStack(inv, ord);
            if (stack.isEmpty() || (remove && !lens.setStack(inv, ord, net.minecraft.item.ItemStack.EMPTY))) {
                continue;
            }
            return ItemStackUtil.cloneDefensiveOptional(stack);
        }

        return Optional.empty();
    }

    private static Optional<ItemStack> findStacks(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, int limit, boolean remove) {

        if (lens == null) {
            return Optional.empty();
        }

        ItemStack result = null;

        for (int ord = 0; ord < lens.slotCount(); ord++) {
            net.minecraft.item.ItemStack stack = lens.getStack(inv, ord);
            if (stack.isEmpty() || stack.getCount() < 1 || (result != null && !result.getType().equals(stack.getItem()))) {
                continue;
            }

            if (result == null) {
                result = ItemStackUtil.cloneDefensive(stack, 0);
            }

            int pull = Math.min(stack.getCount(), limit);
            result.setQuantity(result.getQuantity() + pull);
            limit -= pull;

            if (!remove) {
                continue;
            }

            if (pull >= stack.getCount()) {
                lens.setStack(inv, ord, net.minecraft.item.ItemStack.EMPTY);
            } else {
                stack.setCount(stack.getCount() - pull);
            }
        }

        return Optional.ofNullable(result);
    }

    public static InventoryTransactionResult insertSequential(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, ItemStack stack) {
        return AdapterLogic.insertSequential(adapter.getInventory(), adapter.getRootLens(), stack);
    }

    public static InventoryTransactionResult insertSequential(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, ItemStack stack) {
        if (lens == null) {
            return InventoryTransactionResult.builder().type(InventoryTransactionResult.Type.FAILURE).reject(ItemStackUtil.cloneDefensive(stack)).build();
        }
        try {
            return AdapterLogic.insertStack(inv, lens, stack);
        } catch (Exception ex) {
           return InventoryTransactionResult.builder().type(InventoryTransactionResult.Type.ERROR).reject(ItemStackUtil.cloneDefensive(stack)).build();
        }
    }

    private static InventoryTransactionResult insertStack(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, ItemStack stack) {
        InventoryTransactionResult.Builder result = InventoryTransactionResult.builder().type(InventoryTransactionResult.Type.SUCCESS);
        net.minecraft.item.ItemStack nativeStack = ItemStackUtil.toNative(stack);

        int maxStackSize = Math.min(lens.getMaxStackSize(inv), nativeStack.getMaxStackSize());
        int remaining = stack.getQuantity();

        for (int ord = 0; ord < lens.slotCount() && remaining > 0; ord++) {
            net.minecraft.item.ItemStack old = lens.getStack(inv, ord);
            int push = Math.min(remaining, maxStackSize);
            if (lens.setStack(inv, ord, ItemStackUtil.cloneDefensiveNative(nativeStack, push))) {
                result.replace(ItemStackUtil.fromNative(old));
                remaining -= push;
            }
        }

        if (remaining > 0) {
            result.reject(ItemStackUtil.cloneDefensive(nativeStack, remaining));
        }

        return result.build();
    }

    public static InventoryTransactionResult appendSequential(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, ItemStack stack) {
        return AdapterLogic.appendSequential(adapter.getInventory(), adapter.getRootLens(), stack);
    }

    public static InventoryTransactionResult appendSequential(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, ItemStack stack) {
        InventoryTransactionResult.Builder result = InventoryTransactionResult.builder().type(InventoryTransactionResult.Type.SUCCESS);
        net.minecraft.item.ItemStack nativeStack = ItemStackUtil.toNative(stack);

        int maxStackSize = Math.min(lens.getMaxStackSize(inv), nativeStack.getMaxStackSize());
        int remaining = stack.getQuantity();

        for (int ord = 0; ord < lens.slotCount() && remaining > 0; ord++) {
            net.minecraft.item.ItemStack old = lens.getStack(inv, ord);
            int push = Math.min(remaining, maxStackSize);
            if (old.isEmpty() && lens.setStack(inv, ord, ItemStackUtil.cloneDefensiveNative(nativeStack, push))) {
                remaining -= push;
            } else if (!old.isEmpty() && ItemStackUtil.compareIgnoreQuantity(old, stack)) {
                push = Math.max(Math.min(maxStackSize - old.getCount(), remaining), 0); // max() accounts for oversized stacks
                old.setCount(old.getCount() + push);
                remaining -= push;
            }
        }

        if (remaining == stack.getQuantity()) {
            // No items were consumed
            result.type(InventoryTransactionResult.Type.FAILURE).reject(ItemStackUtil.cloneDefensive(nativeStack));
        } else {
            stack.setQuantity(remaining);
        }

        return result.build();
    }

    public static int countStacks(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter) {
        return AdapterLogic.countStacks(adapter.getInventory(), adapter.getRootLens());
    }

    public static int countStacks(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens) {
        int stacks = 0;

        for (int ord = 0; ord < lens.slotCount(); ord++) {
            stacks += !lens.getStack(inv, ord).isEmpty() ? 1 : 0;
        }

        return stacks;
    }

    public static int countItems(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter) {
        return AdapterLogic.countItems(adapter.getInventory(), adapter.getRootLens());
    }

    public static int countItems(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens) {
        int items = 0;

        for (int ord = 0; ord < lens.slotCount(); ord++) {
            net.minecraft.item.ItemStack stack = lens.getStack(inv, ord);
            items += !stack.isEmpty() ? stack.getCount() : 0;
        }

        return items;
    }

    public static int getCapacity(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter) {
        return AdapterLogic.getCapacity(adapter.getInventory(), adapter.getRootLens());
    }

    public static int getCapacity(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens) {
        return lens.getSlots().size();
    }

    public static Collection<InventoryProperty<?, ?>> getProperties(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter,
            Inventory child, Class<? extends InventoryProperty<?, ?>> property) {
        return AdapterLogic.getProperties(adapter.getInventory(), adapter.getRootLens(), child, property);
    }

    public static Collection<InventoryProperty<?, ?>> getProperties(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens,
            Inventory child, Class<? extends InventoryProperty<?, ?>> property) {

        if (child instanceof InventoryAdapter) {
            checkNotNull(property, "property");
            int index = lens.getChildren().indexOf(((InventoryAdapter<?, ?>) child).getRootLens());
            if (index > -1) {
                return lens.getProperties(index).stream().filter(prop -> property.equals(prop.getClass()))
                        .collect(Collectors.toCollection(ArrayList::new));
            }
        }

        return Collections.emptyList();
    }

    static <T extends InventoryProperty<?, ?>> Collection<T> getRootProperties(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, Class<T> property) {
        adapter = inventoryRoot(adapter);
        if (adapter instanceof CustomInventory) {
            return ((CustomInventory) adapter).getProperties().values().stream().filter(p -> property.equals(p.getClass()))
                    .map(property::cast).collect(Collectors.toList());
        }
        return Streams.stream(findRootProperty(adapter, property)).collect(Collectors.toList());
    }

    static <T extends InventoryProperty<?, ?>> Optional<T> getRootProperty(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, Class<T> property, Object key) {
        adapter = inventoryRoot(adapter);
        if (adapter instanceof CustomInventory) {
            InventoryProperty forKey = ((CustomInventory) adapter).getProperties().get(key);
            if (forKey != null && property.equals(forKey.getClass())) {
                return Optional.of((T) forKey);
            }
        }
        return findRootProperty(adapter, property);
    }

    private static <T extends InventoryProperty<?, ?>> Optional<T> findRootProperty(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, Class<T> property) {
        if (property == InventoryTitle.class) {
            if (adapter instanceof Container) {
                IInventory inv = adapter.getInventory().allInventories().iterator().next();
                Text text = SpongeTexts.toText(inv.getDisplayName());
                return ((Optional<T>) Optional.of(InventoryTitle.of(text)));
            }
            if (adapter instanceof IInventory) {
                Text text = SpongeTexts.toText(((IInventory) adapter).getDisplayName());
                return ((Optional<T>) Optional.of(InventoryTitle.of(text)));
            }
        }
        // TODO more properties of top level inventory
        return Optional.empty();
    }

    private static InventoryAdapter<IInventory, net.minecraft.item.ItemStack> inventoryRoot(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter) {
        // Get Root Inventory
        adapter = ((InventoryAdapter) adapter.root());
        if (adapter instanceof Container) {
            // If Root is a Container get the viewed inventory
            IInventory first = adapter.getInventory().allInventories().iterator().next();
            if (first instanceof CustomInventory) {
                // if viewed inventory is a custom inventory get it instead
                adapter = ((InventoryAdapter) first);
            }
        }
        return adapter;
    }

    public static boolean contains(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, ItemStack stack) {
        return AdapterLogic.contains(adapter.getInventory(), adapter.getRootLens(), stack, stack.getQuantity());
    }

    public static boolean contains(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, ItemStack stack, int quantity) {
        return AdapterLogic.contains(adapter.getInventory(), adapter.getRootLens(), stack, quantity);
    }

    /**
     * Searches for at least <code>quantity</code> of given stack.
     *
     * @param inv The inventory to search in
     * @param lens The lens to search with
     * @param stack The stack to search with
     * @param quantity The quantity to find
     * @return true if at least <code>quantity</code> of given stack has been found in given inventory
     */
    public static boolean contains(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, ItemStack stack, int quantity) {
        net.minecraft.item.ItemStack nonNullStack = ItemStackUtil.toNative(stack); // Handle null as empty
        int found = 0;
        for (int ord = 0; ord < lens.slotCount(); ord++) {
            net.minecraft.item.ItemStack slotStack = lens.getStack(inv, ord);
            if (slotStack.isEmpty()) {
                if (nonNullStack.isEmpty()) {
                    found++; // Found an empty Slot
                    if (found >= quantity) {
                        return true;
                    }
                }
            } else {
                if (ItemStackUtil.compareIgnoreQuantity(slotStack, stack)) {
                    found += slotStack.getCount(); // Found a matching stack
                    if (found >= quantity) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean contains(InventoryAdapter<IInventory, net.minecraft.item.ItemStack> adapter, ItemType type) {
        return AdapterLogic.contains(adapter.getInventory(), adapter.getRootLens(), type);
    }

    public static boolean contains(Fabric<IInventory> inv, Lens<IInventory, net.minecraft.item.ItemStack> lens, ItemType type) {
        for (int ord = 0; ord < lens.slotCount(); ord++) {
            net.minecraft.item.ItemStack slotStack = lens.getStack(inv, ord);
            if (slotStack.isEmpty()) {
                if (type == null || type == ItemTypes.NONE) {
                    return true; // Found an empty Slot
                }
            } else {
                if (slotStack.getItem() == type) {
                    return true; // Found a matching stack
                }
            }
        }
        return false;
    }
}
