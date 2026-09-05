package cn.huohuas001.huhobot.inventory.host;

import cn.huohuas001.bot.QClient;
import cn.huohuas001.bot.events.commands.BaseCommand;
import cn.huohuas001.bot.events.commands.Commands;
import cn.huohuas001.huhobot.api.AttachmentSnapshot;
import cn.huohuas001.huhobot.api.BotMessage;
import cn.huohuas001.huhobot.api.MentionSnapshot;
import cn.huohuas001.huhobot.api.SenderSnapshot;
import io.github.kloping.qqbot.api.v2.GroupMessageEvent;
import io.github.kloping.qqbot.entities.qqpd.User;
import io.github.kloping.qqbot.entities.qqpd.message.MessageAttachment;
import io.github.kloping.qqbot.entities.qqpd.message.RawMessage;
import io.github.kloping.qqbot.entities.qqpd.v2.Contact;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Registers Inventory/API commands into the unmodified Mainline or AGENT QClient. */
final class OfficialQqCommandBridge implements AutoCloseable {
    static final String AGENT_ADDON_DESCRIPTION = "为 HuHoBot 提供 Minecraft 背包与末影箱图片查询";
    static final String AGENT_ADDON_AUTHOR = "RiegaLee";

    private final JavaPlugin plugin;
    private final EmbeddedHuHoBotService service;
    private final InventoryCommands commands;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile BukkitTask retryTask;
    private volatile boolean registered;
    private volatile boolean waitingLogged;

    private OfficialQqCommandBridge(JavaPlugin plugin, EmbeddedHuHoBotService service) {
        this.plugin = plugin;
        this.service = service;
        this.commands = new InventoryCommands(service, plugin);
    }

    static OfficialQqCommandBridge start(JavaPlugin plugin, EmbeddedHuHoBotService service) {
        OfficialQqCommandBridge bridge = new OfficialQqCommandBridge(plugin, service);
        bridge.retryTask = new BukkitRunnable() {
            @Override public void run() {
                if (bridge.closed.get()) {
                    cancel();
                    return;
                }
                if (bridge.tryRegister()) cancel();
            }
        }.runTaskTimer(plugin, 1L, 20L);
        return bridge;
    }

    private boolean tryRegister() {
        if (registered) return true;
        try {
            Field handlerField = QClient.class.getDeclaredField("groupMessageHandler");
            handlerField.setAccessible(true);
            if (handlerField.get(null) == null) {
                logWaitingOnce();
                return false;
            }
            if (!tryRegisterAsAgentAddon()) QClient.INSTANCE.registerCommand(commands);
            registered = true;
            plugin.getLogger().info(
                "Inventory 已接入官方 HuHoBot QQ 指令分发（内置兼容宿主，无第三方桥接 JAR）"
            );
            return true;
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            if (cause instanceof IllegalStateException &&
                cause.getMessage() != null && cause.getMessage().contains("not been launched")) {
                logWaitingOnce();
                return false;
            }
            plugin.getLogger().log(Level.WARNING, "Inventory 接入 HuHoBot QQ 指令分发失败，将继续重试", cause);
            return false;
        }
    }

    private boolean tryRegisterAsAgentAddon() throws Exception {
        ClassLoader loader = QClient.class.getClassLoader();
        Class<?> addonClass;
        try {
            addonClass = Class.forName("cn.huohuas001.bot.addon.Addon", false, loader);
        } catch (ClassNotFoundException ignored) {
            return false;
        }
        try {
            Constructor<?> constructor = addonClass.getConstructor(
                String.class, String.class, String.class, String.class
            );
            Object metadata = constructor.newInstance(
                "HuHoBotInventory",
                plugin.getDescription().getVersion(),
                AGENT_ADDON_DESCRIPTION,
                AGENT_ADDON_AUTHOR
            );
            Method register = QClient.class.getMethod("registerCommand", addonClass, BaseCommand.class);
            register.invoke(QClient.INSTANCE, metadata, commands);
            plugin.getLogger().info("Inventory 已登记到 AGENT AddonManager");
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private void logWaitingOnce() {
        if (waitingLogged) return;
        waitingLogged = true;
        plugin.getLogger().info("HuHoBot QQ 客户端尚未启动；Inventory 正在等待并会自动接入");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        BukkitTask task = retryTask;
        if (task != null) task.cancel();
        retryTask = null;
        if (!registered) return;
        try {
            Field handlerField = QClient.class.getDeclaredField("groupMessageHandler");
            handlerField.setAccessible(true);
            Object handler = handlerField.get(null);
            if (handler == null) return;
            Field commandsField = handler.getClass().getDeclaredField("commands");
            commandsField.setAccessible(true);
            Object value = commandsField.get(handler);
            if (value instanceof List<?>) ((List<?>) value).remove(commands);
        } catch (Throwable error) {
            plugin.getLogger().log(Level.FINE, "卸载 Inventory QQ 指令桥时未能移除处理器", unwrap(error));
        } finally {
            registered = false;
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable value = error;
        while ((value instanceof InvocationTargetException) && value.getCause() != null) {
            value = value.getCause();
        }
        return value;
    }

    /** Fixed public entry points supported by both official command scanners. */
    public static final class InventoryCommands extends BaseCommand {
        private final EmbeddedHuHoBotService service;
        private final JavaPlugin plugin;

        InventoryCommands(EmbeddedHuHoBotService service, JavaPlugin plugin) {
            this.service = service;
            this.plugin = plugin;
        }

        @Commands(command = "inventorytest", describe = "测试背包渲染")
        public void inventoryTest(GroupMessageEvent event, String ignored) { route(event); }

        @Commands(command = "invtest", describe = "测试背包渲染")
        public void inventoryTestAlias(GroupMessageEvent event, String ignored) { route(event); }

        @Commands(command = "inventory", describe = "查询我的背包")
        public void inventory(GroupMessageEvent event, String ignored) { route(event); }

        @Commands(command = "inv", describe = "查询我的背包")
        public void inventoryAlias(GroupMessageEvent event, String ignored) { route(event); }

        @Commands(command = "背包", describe = "查询我的背包")
        public void inventoryChinese(GroupMessageEvent event, String ignored) { route(event); }

        @Commands(command = "enderchest", describe = "查询我的末影箱")
        public void enderChest(GroupMessageEvent event, String ignored) { route(event); }

        @Commands(command = "ec", describe = "查询我的末影箱")
        public void enderChestAlias(GroupMessageEvent event, String ignored) { route(event); }

        @Commands(command = "末影箱", describe = "查询我的末影箱")
        public void enderChestChinese(GroupMessageEvent event, String ignored) { route(event); }

        @Commands(command = "绑定", describe = "绑定 Minecraft 账号")
        public void bind(GroupMessageEvent event, String ignored) { route(event); }

        @Commands(command = "解绑", describe = "解除 Minecraft 账号绑定")
        public void unbind(GroupMessageEvent event, String ignored) { route(event); }

        @Commands(command = "绑定列表", describe = "查看 Minecraft 账号绑定")
        public void bindingList(GroupMessageEvent event, String ignored) { route(event); }

        @Commands(command = "设置主账号", describe = "设置默认查询账号")
        public void primary(GroupMessageEvent event, String ignored) { route(event); }

        private void route(GroupMessageEvent event) {
            try {
                if (!service.route(snapshot(event))) {
                    QClient.INSTANCE.replyText(
                        groupOpenId(event), messageId(event), messageSequence(event),
                        "该扩展命令当前不可用，请检查 Inventory/GameAuthCode 配置"
                    );
                }
            } catch (Throwable error) {
                plugin.getLogger().log(Level.SEVERE, "Inventory QQ 指令转换或路由失败", error);
            }
        }

        private static BotMessage snapshot(GroupMessageEvent event) {
            RawMessage raw = event.getRawMessage();
            Contact sender = event.getSender();
            String groupOpenId = groupOpenId(event);
            List<MentionSnapshot> mentions = new ArrayList<MentionSnapshot>();
            User[] rawMentions = raw == null ? null : raw.getMentions();
            if (rawMentions != null) {
                for (User mention : rawMentions) {
                    if (mention != null) mentions.add(new MentionSnapshot(
                        mention.getId(), null, text(mention.getUsername(), "unknown"), null
                    ));
                }
            }
            List<AttachmentSnapshot> attachments = new ArrayList<AttachmentSnapshot>();
            MessageAttachment[] rawAttachments = raw == null ? null : raw.getAttachments();
            if (rawAttachments != null) {
                for (MessageAttachment attachment : rawAttachments) {
                    if (attachment != null) attachments.add(new AttachmentSnapshot(
                        attachment.getId(), attachment.getFilename(), attachment.getUrl(),
                        attachment.getContent_type(), attachment.getSize(), attachment.getWidth(),
                        attachment.getHeight(), null
                    ));
                }
            }
            return new BotMessage(
                raw == null ? "" : text(raw.getId(), ""),
                groupOpenId,
                event.getGroupId(),
                new SenderSnapshot(
                    sender == null ? null : sender.getId(),
                    sender == null ? null : sender.getOpenid(),
                    sender == null ? "unknown" : text(sender.getUsername(), "unknown"),
                    sender == null ? null : sender.getRole()
                ),
                raw == null ? "" : text(raw.getContent(), ""),
                raw == null ? "" : text(raw.toString0(), ""),
                raw == null ? null : raw.getTimestamp(),
                messageSequence(event),
                mentions,
                attachments
            );
        }

        private static String groupOpenId(GroupMessageEvent event) {
            String value = event.getGroupOpenId();
            return value == null || value.trim().isEmpty() ? text(event.getGroupId(), "") : value;
        }

        private static String messageId(GroupMessageEvent event) {
            RawMessage raw = event.getRawMessage();
            return raw == null ? "" : text(raw.getId(), "");
        }

        private static int messageSequence(GroupMessageEvent event) {
            Integer value = event.getMsgSeq();
            return value == null ? 0 : value.intValue();
        }

        private static String text(String value, String fallback) {
            return value == null ? fallback : value;
        }
    }
}
