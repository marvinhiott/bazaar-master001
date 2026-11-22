package me.math3w.bazaar.menu;

import me.math3w.bazaar.BazaarPlugin;
import me.math3w.bazaar.api.bazaar.Product;
import me.math3w.bazaar.api.bazaar.orders.BazaarOrder;
import me.math3w.bazaar.api.bazaar.orders.InstantBazaarOrder;
import me.math3w.bazaar.api.bazaar.orders.OrderType;
import me.math3w.bazaar.api.menu.ClickActionManager;
import me.math3w.bazaar.api.menu.ConfigurableMenuItem;
import me.math3w.bazaar.api.menu.MenuInfo;
import me.zort.containr.ContextClickInfo;
import me.zort.containr.GUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class DefaultClickActionManager implements ClickActionManager {
    private final BazaarPlugin bazaarPlugin;
    private final Map<String, Function<MenuInfo, Consumer<ContextClickInfo>>> clickActions = new HashMap<>();
    private final Map<String, BiFunction<ConfigurableMenuItem, MenuInfo, Consumer<ContextClickInfo>>> editActions = new HashMap<>();

    public DefaultClickActionManager(BazaarPlugin bazaarPlugin) {
        this.bazaarPlugin = bazaarPlugin;

        addClickActions();
        addEditClickActions();
    }

    private void addClickActions() {
        addClickAction("close", ContextClickInfo::close);
        addClickAction("back", (Consumer<ContextClickInfo>) clickInfo -> bazaarPlugin.getMenuHistory().openPrevious(clickInfo.getPlayer()));
        addClickAction("search", clickInfo -> {
            requireTextFromPlayer(clickInfo.getPlayer(), "search-sign", filter ->
                    bazaarPlugin.getBazaar().openSearch(clickInfo.getPlayer(), filter));
        });
        addClickAction("buy-order", menuInfo -> clickInfo -> {
            if (!(menuInfo instanceof Product)) return;
            Product product = (Product) menuInfo;

            requireNumberFromPlayer(clickInfo.getPlayer(), "buy-order-amount-sign", amount -> {
                requireNumberFromPlayer(clickInfo.getPlayer(), "buy-order-price-sign", unitPrice -> {
                    BazaarOrder order = bazaarPlugin.getOrderManager().prepareBazaarOrder(product, amount, unitPrice, OrderType.BUY, clickInfo.getPlayer().getUniqueId());
                    bazaarPlugin.getBazaarConfig().getConfirmationMenuConfiguration(OrderType.BUY).getMenu(order, false).open(clickInfo.getPlayer());
                }, Double::parseDouble);
            }, Integer::parseInt);
        });
        addClickAction("sell-offer", menuInfo -> clickInfo -> {
            if (!(menuInfo instanceof Product)) return;
            Product product = (Product) menuInfo;

            requireNumberFromPlayer(clickInfo.getPlayer(), "sell-offer-amount-sign", amount -> {
                requireNumberFromPlayer(clickInfo.getPlayer(), "sell-offer-price-sign", unitPrice -> {
                    BazaarOrder order = bazaarPlugin.getOrderManager().prepareBazaarOrder(product, amount, unitPrice, OrderType.SELL, clickInfo.getPlayer().getUniqueId());
                    bazaarPlugin.getBazaarConfig().getConfirmationMenuConfiguration(OrderType.SELL).getMenu(order, false).open(clickInfo.getPlayer());
                }, Double::parseDouble);
            }, Integer::parseInt);
        });

        addClickAction("buy-instantly", menuInfo -> clickInfo -> {
            if (!(menuInfo instanceof Product)) return;
            Product product = (Product) menuInfo;

            requireNumberFromPlayer(clickInfo.getPlayer(), "buy-instantly-amount-sign", amount -> {
                bazaarPlugin.getOrderManager().prepareInstantOrder(product, amount, OrderType.BUY, clickInfo.getPlayer().getUniqueId()).thenAccept(order -> {
                    bazaarPlugin.getBazaarConfig().getConfirmationMenuConfiguration(OrderType.BUY).getInstantMenu(order, false).open(clickInfo.getPlayer());
                });
            }, Integer::parseInt);
        });

        addClickAction("sell-instantly", menuInfo -> clickInfo -> {
            if (!(menuInfo instanceof Product)) return;
            Product product = (Product) menuInfo;

            if (clickInfo.getClickType().isRightClick()) {
                requireNumberFromPlayer(clickInfo.getPlayer(), "sell-instantly-amount-sign", amount -> {
                    bazaarPlugin.getOrderManager().prepareInstantOrder(product, amount, OrderType.SELL, clickInfo.getPlayer().getUniqueId()).thenAccept(order -> {
                        bazaarPlugin.getBazaarConfig().getConfirmationMenuConfiguration(OrderType.SELL).getInstantMenu(order, false).open(clickInfo.getPlayer());
                    });
                }, Integer::parseInt);
                return;
            }

            int amount = bazaarPlugin.getBazaar().getProductAmountInInventory(product, clickInfo.getPlayer());
            bazaarPlugin.getOrderManager().prepareInstantOrder(product, amount, OrderType.SELL, clickInfo.getPlayer().getUniqueId()).thenAccept(order -> {
                bazaarPlugin.getBazaarConfig().getConfirmationMenuConfiguration(OrderType.SELL).getInstantMenu(order, false).open(clickInfo.getPlayer());
            });
        });

        addClickAction("reject-order", menuInfo -> clickInfo -> {
            if (menuInfo instanceof BazaarOrder) {
                bazaarPlugin.getMessagesConfig().sendMessage(clickInfo.getPlayer(), "order.reject");
                clickInfo.close();
                return;
            }

            if (menuInfo instanceof InstantBazaarOrder) {
                bazaarPlugin.getMessagesConfig().sendMessage(clickInfo.getPlayer(), "instant.reject");
                clickInfo.close();
            }
        });

        addClickAction("confirm-order", menuInfo -> clickInfo -> {
            if (menuInfo instanceof BazaarOrder) {
                BazaarOrder order = (BazaarOrder) menuInfo;
                bazaarPlugin.getOrderManager().submitBazaarOrder(order);
                clickInfo.close();
            }

            if (menuInfo instanceof InstantBazaarOrder) {
                InstantBazaarOrder order = (InstantBazaarOrder) menuInfo;
                bazaarPlugin.getOrderManager().submitInstantOrder(order);
                clickInfo.close();
            }
        });

        addClickAction("manage-orders", clickInfo -> {
            bazaarPlugin.getBazaar().openOrders(clickInfo.getPlayer());
        });

        addClickAction("sell-inventory", menuInfo -> clickInfo -> {
            Map<Product, Integer> productsInInventory = bazaarPlugin.getBazaar().getProductsInInventory(clickInfo.getPlayer());

            for (Map.Entry<Product, Integer> productAmountEntry : productsInInventory.entrySet()) {
                Product product = productAmountEntry.getKey();
                int playerAmount = productAmountEntry.getValue();

                bazaarPlugin.getOrderManager().prepareInstantOrder(product, playerAmount, OrderType.SELL, clickInfo.getPlayer().getUniqueId())
                        .thenAccept(order -> bazaarPlugin.getOrderManager().submitInstantOrder(order));
            }
        });

        addClickAction("", clickInfo -> {
        });
    }

    private void addEditClickActions() {
        addEditClickAction("search", (configurableMenuItem, menuInfo) -> clickInfo -> {
            requireTextFromPlayer(clickInfo.getPlayer(), "search-sign", filter ->
                    bazaarPlugin.getBazaar().openEditSearch(clickInfo.getPlayer(), filter, configurableMenuItem));
        });

        addEditClickAction("manage-orders", (configurableMenuItem, menuInfo) -> clickInfo -> {
            bazaarPlugin.getBazaar().openEditOrders(clickInfo.getPlayer());
        });


        addEditClickAction("buy-order", (configurableMenuItem, menuInfo) -> clickInfo -> {
            if (!(menuInfo instanceof Product)) return;
            Product product = (Product) menuInfo;

            BazaarOrder order = bazaarPlugin.getOrderManager().prepareBazaarOrder(product, 0, 0, OrderType.BUY, clickInfo.getPlayer().getUniqueId());
            bazaarPlugin.getBazaarConfig().getConfirmationMenuConfiguration(OrderType.BUY).getMenu(order, true).open(clickInfo.getPlayer());
        });
        addEditClickAction("sell-offer", (configurableMenuItem, menuInfo) -> clickInfo -> {
            if (!(menuInfo instanceof Product)) return;
            Product product = (Product) menuInfo;

            BazaarOrder order = bazaarPlugin.getOrderManager().prepareBazaarOrder(product, 0, 0, OrderType.SELL, clickInfo.getPlayer().getUniqueId());
            bazaarPlugin.getBazaarConfig().getConfirmationMenuConfiguration(OrderType.SELL).getMenu(order, true).open(clickInfo.getPlayer());
        });


        addEditClickAction("buy-instantly", (configurableMenuItem, menuInfo) -> clickInfo -> {
            if (!(menuInfo instanceof Product)) return;
            Product product = (Product) menuInfo;

            bazaarPlugin.getOrderManager().prepareInstantOrder(product, 0, OrderType.BUY, clickInfo.getPlayer().getUniqueId()).thenAccept(order -> {
                bazaarPlugin.getBazaarConfig().getConfirmationMenuConfiguration(OrderType.BUY).getInstantMenu(order, true).open(clickInfo.getPlayer());
            });
        });

        addEditClickAction("sell-instantly", (configurableMenuItem, menuInfo) -> clickInfo -> {
            if (!(menuInfo instanceof Product)) return;
            Product product = (Product) menuInfo;

            bazaarPlugin.getOrderManager().prepareInstantOrder(product, 0, OrderType.SELL, clickInfo.getPlayer().getUniqueId()).thenAccept(order -> {
                bazaarPlugin.getBazaarConfig().getConfirmationMenuConfiguration(OrderType.SELL).getInstantMenu(order, true).open(clickInfo.getPlayer());
            });
        });
    }

    @Override
    public void addClickAction(String name, Consumer<ContextClickInfo> action) {
        addClickAction(name, menuInfo -> action);
    }

    @Override
    public void addClickAction(String name, Function<MenuInfo, Consumer<ContextClickInfo>> action) {
        clickActions.put(name, action);
    }

    @Override
    public void addEditClickAction(String name, BiFunction<ConfigurableMenuItem, MenuInfo, Consumer<ContextClickInfo>> action) {
        editActions.put(name, action);
    }

    @Override
    public Consumer<ContextClickInfo> getClickAction(ConfigurableMenuItem configurableMenuItem, MenuInfo menuInfo, boolean editing) {
        if (editing) {
            return clickInfo -> {
                if (clickInfo.getClickType().isRightClick()) {
                    bazaarPlugin.getEditManager().openItemEdit(clickInfo.getPlayer(), configurableMenuItem);
                    return;
                }

                editActions.getOrDefault(configurableMenuItem.getAction(),
                                (configurableMenuItem1, menuInfo1) -> getClickAction(configurableMenuItem, menuInfo, false))
                        .apply(configurableMenuItem, menuInfo).accept(clickInfo);
            };
        }
        return clickActions.getOrDefault(configurableMenuItem.getAction(), menuInfo1 -> clickInfo -> {
        }).apply(menuInfo);
    }

    @Override
    public Set<String> getActions() {
        return clickActions.keySet();
    }

    private <T extends Number> void requireNumberFromPlayer(Player player, String sign, Consumer<T> callback, Function<String, T> parser) {
        Stack<GUI> history = bazaarPlugin.getMenuHistory().getHistory(player);
        player.closeInventory();
        player.sendMessage(ChatColor.GRAY + getChatPrompt(sign) + " (type 'cancel' to cancel)");
        bazaarPlugin.getMessageInputManager().requirePlayerMessageInput(player, line -> {
            if (line.trim().equalsIgnoreCase("cancel")) {
                reopenMenu(player, history);
                return;
            }

            try {
                T amount = parser.apply(line);
                if (amount.doubleValue() <= 0) {
                    reopenMenu(player, history);
                    return;
                }

                Bukkit.getScheduler().runTaskLater(bazaarPlugin, () -> {
                    bazaarPlugin.getMenuHistory().setHistory(player, history);
                    callback.accept(amount);
                }, 1);
            } catch (NumberFormatException exception) {
                player.sendMessage(ChatColor.RED + "Invalid number. Returning to the menu.");
                reopenMenu(player, history);
            }
        });
    }

    private void requireTextFromPlayer(Player player, String promptPath, Consumer<String> callback) {
        player.closeInventory();
        player.sendMessage(ChatColor.GRAY + getChatPrompt(promptPath) + " in chat (type 'cancel' to cancel)");
        bazaarPlugin.getMessageInputManager().requirePlayerMessageInput(player, line -> {
            if (line.trim().equalsIgnoreCase("cancel")) {
                bazaarPlugin.getMenuHistory().openPrevious(player);
                return;
            }
            callback.accept(line);
        });
    }

    private String getChatPrompt(String path) {
        List<String> lines = bazaarPlugin.getMenuConfig().getStringList(path);
        String prompt = String.join(" ", lines.subList(Math.min(2, lines.size()), lines.size()));
        prompt = ChatColor.stripColor(prompt).trim();
        return prompt.isEmpty() ? "Enter input" : prompt;
    }

    private void reopenMenu(Player player, Stack<GUI> history) {
        if (history.isEmpty()) return;
        GUI lastGui = history.pop();
        bazaarPlugin.getMenuHistory().setHistory(player, history);
        lastGui.open(player);
    }
}
