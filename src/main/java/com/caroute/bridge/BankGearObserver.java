package com.caroute.bridge;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/** Optional, session-only positive observations. An unseen/partial bank never proves absence. */
public final class BankGearObserver
{
    private Set<Integer> itemIds = Collections.emptySet();
    private long observedAt;
    private boolean observed;

    /** Client-thread only. The caller owns account/login isolation and clears on changes. */
    public void capture(Client client, boolean enabled, long now)
    {
        if (!enabled) { clear(); return; }
        Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);
        if (bank == null || bank.isHidden()) { return; }
        ItemContainer container = client.getItemContainer(InventoryID.BANK);
        if (container == null) { return; }
        Map<Integer, Integer> quantities = new TreeMap<>();
        for (Item item : container.getItems())
        {
            if (item != null && item.getId() >= 0 && item.getQuantity() > 0)
            { quantities.put(item.getId(), item.getQuantity()); }
        }
        observe(true, true, quantities, now);
    }

    /** Testable boundary: zero-quantity placeholders do not count as owned items. */
    public void observe(boolean enabled, boolean bankOpen, Map<Integer, Integer> quantities, long now)
    {
        if (!enabled) { clear(); return; }
        if (!bankOpen) { return; }
        Set<Integer> positive = new TreeSet<>();
        quantities.forEach((id, count) -> { if (id != null && id >= 0 && count != null && count > 0) { positive.add(id); } });
        if (!observed || !positive.equals(itemIds)) { observedAt = now; }
        itemIds = Collections.unmodifiableSet(positive);
        observed = true;
    }

    public Set<Integer> itemIds() { return itemIds; }
    public long observedAt() { return observedAt; }
    public boolean observed() { return observed; }
    public void clear() { itemIds = Collections.emptySet(); observedAt = 0; observed = false; }
}
