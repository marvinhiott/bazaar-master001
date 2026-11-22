package me.math3w.bazaar.menu;

import me.math3w.bazaar.api.BazaarAPI;
import me.math3w.bazaar.api.config.Placeholder;
import me.math3w.bazaar.utils.MenuUtils;
import me.math3w.bazaar.utils.Utils;
import me.zort.containr.ContainerComponent;
import me.zort.containr.Element;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LazyLorePlaceholder implements Placeholder {
    private static final int MAX_UPDATE_ATTEMPTS = 20;

    private final BazaarAPI bazaarAPI;
    private final ContainerComponent container;
    private final ItemStack item;
    private final int itemSlot;
    private final Player player;
    private final String placeholder;
    private final CompletableFuture<String> valueFuture;
    private final String loadingValue;

    public LazyLorePlaceholder(BazaarAPI bazaarAPI, ContainerComponent container, ItemStack item, int itemSlot, Player player, String placeholder, CompletableFuture<String> valueFuture, String loadingValue) {
        this.bazaarAPI = bazaarAPI;
        this.container = container;
        this.item = item;
        this.itemSlot = itemSlot;
        this.player = player;
        this.placeholder = placeholder;
        this.valueFuture = valueFuture;
        this.loadingValue = loadingValue;
    }

    private static LoreLine getLoreLine(String line, List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String loreLine = lore.get(i);
            if (!ChatColor.stripColor(loreLine).contains(line)) continue;
            return new LoreLine(loreLine, i);
        }
        return null;
    }

    @Override
    public String replace(String text) {
        if (!text.contains("%" + placeholder + "%")) return text;

        String loadingLine = text.replaceAll("%" + placeholder + "%", loadingValue);

        //Need to wait at least 3 ticks because the ContainrGUI opens the menu with 3 ticks delay. So without this element will be null and value will stuck at loading if our valueFuture task would take less than 3 ticks
        Instant startInstant = Instant.now();
        valueFuture.thenAccept(value -> {
            Instant doneInstant = Instant.now();
            long waitTicks = Math.max(3 - (Duration.between(startInstant, doneInstant).toMillis() / 50), 0);

            Bukkit.getScheduler().runTaskLater(bazaarAPI.getPlugin(), () -> updateLore(value, loadingLine, 0), waitTicks);
        }).exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        });

        return loadingLine;
    }

    private void updateLore(String value, String loadingLine, int attempt) {
        if (!player.isOnline()) return;

        Element element = MenuUtils.getElementAtSlot(container, itemSlot);
        if (element == null) {
            retryUpdate(value, loadingLine, attempt, "menu element missing at slot " + itemSlot);
            return;
        }

        ItemStack newItem = element.item(player);
        if (newItem == null) {
            retryUpdate(value, loadingLine, attempt, "menu element has no item at slot " + itemSlot);
            return;
        }
        newItem = newItem.clone();

        ItemMeta itemMeta = newItem.getItemMeta();
        if (itemMeta == null || !itemMeta.hasLore()) {
            retryUpdate(value, loadingLine, attempt, "menu item has no lore at slot " + itemSlot);
            return;
        }
        List<String> lore = new ArrayList<>(itemMeta.getLore());

        LoreLine loreLine = getLoreLine(ChatColor.stripColor(Utils.colorize(loadingLine)), lore);
        if (loreLine == null) {
            retryUpdate(value, loadingLine, attempt, "loading lore line missing at slot " + itemSlot);
            return;
        }

        String loreContent = loreLine.getContent().replace(loadingValue, value);
        if (loreContent.equals(loreLine.getContent())) {
            loreContent = loreLine.getContent().replace(ChatColor.stripColor(loadingValue), value);
        }
        lore.set(loreLine.getIndex(), loreContent);

        itemMeta.setLore(lore);
        newItem.setItemMeta(itemMeta);

        MenuUtils.updateGuiItem(bazaarAPI.getMenuHistory(), container, itemSlot, player, newItem);
    }

    private void retryUpdate(String value, String loadingLine, int attempt, String failureReason) {
        if (attempt >= MAX_UPDATE_ATTEMPTS) {
            Bukkit.getLogger().warning("Could not update Bazaar price placeholder for " + player.getName()
                    + " after " + MAX_UPDATE_ATTEMPTS + " attempts: " + failureReason + ".");
            return;
        }

        Bukkit.getScheduler().runTaskLater(bazaarAPI.getPlugin(),
                () -> updateLore(value, loadingLine, attempt + 1), 1);
    }

    private static class LoreLine {
        private final String content;
        private final int index;

        public LoreLine(String content, int index) {
            this.content = content;
            this.index = index;
        }

        public String getContent() {
            return content;
        }

        public int getIndex() {
            return index;
        }
    }
}
