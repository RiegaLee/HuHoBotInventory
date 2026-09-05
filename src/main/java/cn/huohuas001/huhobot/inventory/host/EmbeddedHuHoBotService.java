package cn.huohuas001.huhobot.inventory.host;

import cn.huohuas001.huhobot.api.ApiVersion;
import cn.huohuas001.huhobot.api.BindingService;
import cn.huohuas001.huhobot.api.BindingVerificationService;
import cn.huohuas001.huhobot.api.BotMessage;
import cn.huohuas001.huhobot.api.Capability;
import cn.huohuas001.huhobot.api.CommandContext;
import cn.huohuas001.huhobot.api.CommandHandler;
import cn.huohuas001.huhobot.api.CommandInvocation;
import cn.huohuas001.huhobot.api.CommandPermission;
import cn.huohuas001.huhobot.api.CommandRegistry;
import cn.huohuas001.huhobot.api.CommandResult;
import cn.huohuas001.huhobot.api.CommandSpec;
import cn.huohuas001.huhobot.api.HuHoBotService;
import cn.huohuas001.huhobot.api.MessageGateway;
import cn.huohuas001.huhobot.api.MessageReference;
import cn.huohuas001.huhobot.api.PlayerBinding;
import cn.huohuas001.huhobot.api.PluginContext;
import cn.huohuas001.huhobot.api.PluginDescriptor;
import cn.huohuas001.huhobot.api.PluginLogger;
import cn.huohuas001.huhobot.api.Principal;
import cn.huohuas001.huhobot.api.PrincipalRole;
import cn.huohuas001.huhobot.api.Registration;
import cn.huohuas001.huhobot.api.Registrations;
import cn.huohuas001.huhobot.api.SendResult;
import cn.huohuas001.huhobot.api.TaskHandle;
import cn.huohuas001.huhobot.api.TaskScheduler;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.regex.Pattern;

/** Owner-scoped API 1.3 host embedded in the Inventory addon. */
final class EmbeddedHuHoBotService implements HuHoBotService, AutoCloseable {
    private static final long HANDLER_TIMEOUT_MILLIS = 30_000L;
    private static final long DEDUPE_TTL_MILLIS = 5 * 60_000L;
    private static final int DEDUPE_CLEANUP_INTERVAL = 256;
    private static final int DEDUPE_HARD_LIMIT = 20_000;
    private static final String GENERIC_FAILURE_REPLY = "扩展命令执行失败，请联系管理员";
    private static final Pattern MENTION = Pattern.compile("<@!?[^>]+>");

    private final JavaPlugin plugin;
    private final MessageGateway gateway;
    private final BindingService bindings;
    private final BindingVerificationService verification;
    private final Set<Capability> capabilities;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Map<String, Context> contexts = new ConcurrentHashMap<String, Context>();
    private final Map<String, CommandEntry> commands = new ConcurrentHashMap<String, CommandEntry>();
    private final Object commandLock = new Object();
    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "huhobot-inventory-addon-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Long> deliveredMessages = new ConcurrentHashMap<String, Long>();
    private final AtomicInteger routeCount = new AtomicInteger();

    EmbeddedHuHoBotService(
        JavaPlugin plugin,
        MessageGateway gateway,
        BindingService bindings,
        BindingVerificationService verification
    ) {
        this.plugin = plugin;
        this.gateway = gateway;
        this.bindings = bindings;
        this.verification = verification;
        this.capabilities = Collections.unmodifiableSet(EnumSet.of(
            Capability.COMMANDS,
            Capability.TEXT_MESSAGES,
            Capability.BYTE_ARRAY_IMAGES,
            Capability.SCHEDULER,
            Capability.BINDING_LOOKUP,
            Capability.BINDING_VERIFICATION
        ));
    }

    @Override public ApiVersion getApiVersion() { return ApiVersion.CURRENT; }
    @Override public Set<Capability> getCapabilities() { return capabilities; }

    @Override
    public PluginContext openPlugin(PluginDescriptor descriptor) {
        if (closed.get()) throw new IllegalStateException("HuHoBot addon service is closed");
        if (!getApiVersion().supports(descriptor.getRequiredApiVersion())) {
            throw new IllegalArgumentException(
                "Addon " + descriptor.getId() + " requires API " + descriptor.getRequiredApiVersion() +
                    ", embedded host provides " + getApiVersion()
            );
        }
        String key = normalize(descriptor.getId());
        PluginLogger ownerLogger = new OwnerLogger(plugin, descriptor);
        Context context = new Context(descriptor, ownerLogger);
        Context existing = contexts.putIfAbsent(key, context);
        if (existing != null) throw new IllegalStateException("Addon id already open: " + descriptor.getId());
        if (closed.get()) {
            context.close();
            throw new IllegalStateException("HuHoBot addon service closed while opening " + descriptor.getId());
        }
        ownerLogger.info("Opened addon context with embedded HuHoBot API " + getApiVersion());
        return context;
    }

    boolean route(BotMessage message) {
        if (closed.get()) return false;
        CommandInvocation invocation = parseInvocation(message.getContent());
        if (invocation == null) return false;
        CommandEntry entry = commands.get(normalize(invocation.getCommand()));
        if (entry == null || entry.owner.isClosed()) return false;
        if (isDuplicate(message)) return true;

        Principal principal = principalFor(message);
        CommandContext command = new CommandContext(
            message, invocation, principal, entry.owner.messages, entry.owner.scheduler
        );
        if (entry.spec.getPermission() == CommandPermission.ADMIN &&
            !principal.getRole().isAdministrator()) {
            safeReply(entry, message, "权限不足，无法执行该扩展命令");
            return true;
        }

        CompletionStage<CommandResult> completion;
        try {
            completion = entry.handler.handle(command);
            if (completion == null) throw new IllegalStateException("Addon handler returned null");
        } catch (Throwable error) {
            entry.owner.logger.error(audit(entry, message) + " threw before returning", error);
            if (error instanceof VirtualMachineError) throw (VirtualMachineError) error;
            if (error instanceof ThreadDeath) throw (ThreadDeath) error;
            if (error instanceof LinkageError) throw (LinkageError) error;
            safeReply(entry, message, GENERIC_FAILURE_REPLY);
            return true;
        }

        AtomicBoolean finished = new AtomicBoolean(false);
        java.util.concurrent.ScheduledFuture<?> timeout = timeoutExecutor.schedule(() -> {
            if (finished.compareAndSet(false, true)) {
                entry.owner.logger.error(
                    audit(entry, message) + " timed out after " + HANDLER_TIMEOUT_MILLIS + "ms",
                    new TimeoutException("Addon command timed out")
                );
                safeReply(entry, message, GENERIC_FAILURE_REPLY);
            }
        }, HANDLER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        completion.whenComplete((result, error) -> {
            if (!finished.compareAndSet(false, true)) return;
            timeout.cancel(false);
            if (error != null) {
                entry.owner.logger.error(audit(entry, message) + " failed", unwrap(error));
                safeReply(entry, message, GENERIC_FAILURE_REPLY);
            } else if (result == null || result.getStatus() == CommandResult.Status.FAILED) {
                String diagnostic = result == null || result.getDiagnostic() == null
                    ? "handler completed without a successful result" : result.getDiagnostic();
                entry.owner.logger.error(
                    audit(entry, message) + " returned FAILED: " + diagnostic,
                    new IllegalStateException(diagnostic)
                );
                safeReply(entry, message, GENERIC_FAILURE_REPLY);
            }
        });
        return true;
    }

    private Registration register(Context owner, CommandSpec spec, CommandHandler handler) {
        owner.requireOpen();
        LinkedHashSet<String> tokens = new LinkedHashSet<String>();
        tokens.add(normalize(spec.getId()));
        for (String alias : spec.getAliases()) tokens.add(normalize(alias));
        if (tokens.size() != spec.getAliases().size() + 1) {
            throw new IllegalArgumentException("Command repeats its id or an alias: " + spec.getId());
        }
        CommandEntry entry = new CommandEntry(owner, spec, handler, tokens);
        synchronized (commandLock) {
            owner.requireOpen();
            for (String token : tokens) {
                CommandEntry conflict = commands.get(token);
                if (conflict != null) {
                    throw new IllegalArgumentException(
                        "Command token '" + token + "' is already registered by " +
                            conflict.owner.descriptor.getId()
                    );
                }
            }
            for (String token : tokens) commands.put(token, entry);
        }
        owner.logger.info("Registered addon command '" + spec.getId() + "'");
        return Registrations.create(() -> {
            synchronized (commandLock) {
                for (String token : tokens) commands.remove(token, entry);
            }
        });
    }

    private Principal principalFor(BotMessage message) {
        String roleText = optional(message.getSender().getRole()).toUpperCase(Locale.ROOT);
        PrincipalRole role;
        if (roleText.equals("OWNER") || roleText.equals("群主")) role = PrincipalRole.OWNER;
        else if (roleText.equals("ADMIN") || roleText.equals("ADMINISTRATOR") || roleText.equals("管理员")) {
            role = PrincipalRole.ADMIN;
        } else if (roleText.equals("MEMBER") || roleText.equals("成员")) role = PrincipalRole.MEMBER;
        else role = PrincipalRole.UNKNOWN;
        return new Principal(
            message.getSender().getId(), message.getSender().getOpenId(),
            message.getSender().getUsername(), role
        );
    }

    private boolean isDuplicate(BotMessage message) {
        if (message.getMessageId().trim().isEmpty()) return false;
        long now = System.currentTimeMillis();
        String key = message.getGroupOpenId() + '\0' + message.getMessageId();
        Long previous = deliveredMessages.putIfAbsent(key, Long.valueOf(now));
        if (routeCount.incrementAndGet() % DEDUPE_CLEANUP_INTERVAL == 0) {
            long cutoff = now - DEDUPE_TTL_MILLIS;
            deliveredMessages.entrySet().removeIf(entry -> entry.getValue().longValue() < cutoff);
            if (deliveredMessages.size() > DEDUPE_HARD_LIMIT) deliveredMessages.clear();
        }
        return previous != null;
    }

    private void safeReply(CommandEntry entry, BotMessage message, String text) {
        try {
            entry.owner.messages.replyText(message.toReference(), text).whenComplete((result, error) -> {
                if (error != null) entry.owner.logger.error(audit(entry, message) + " failure reply failed", unwrap(error));
                else if (result == null || !result.isSuccess()) {
                    entry.owner.logger.warning(audit(entry, message) + " failure reply was not sent");
                }
            });
        } catch (Throwable error) {
            entry.owner.logger.error(audit(entry, message) + " failure reply could not start", error);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (Context context : new ArrayList<Context>(contexts.values())) {
            try { context.close(); }
            catch (Throwable error) { context.logger.error("Failed to close addon context", error); }
        }
        contexts.clear();
        synchronized (commandLock) { commands.clear(); }
        deliveredMessages.clear();
        timeoutExecutor.shutdownNow();
    }

    private final class Context implements PluginContext {
        private final PluginDescriptor descriptor;
        private final PluginLogger logger;
        private final AtomicBoolean contextClosed = new AtomicBoolean(false);
        private final Set<Registration> resources = ConcurrentHashMap.newKeySet();
        private final MessageGateway messages;
        private final TaskScheduler scheduler;
        private final CommandRegistry commandRegistry;

        private Context(PluginDescriptor descriptor, PluginLogger logger) {
            this.descriptor = descriptor;
            this.logger = logger;
            this.messages = new ScopedMessages(this, gateway);
            this.scheduler = new ScopedScheduler(this, new BukkitScheduler(plugin, logger));
            this.commandRegistry = (spec, handler) -> track(register(this, spec, handler));
        }

        @Override public PluginDescriptor getDescriptor() { return descriptor; }
        @Override public Set<Capability> getCapabilities() { return capabilities; }
        @Override public CommandRegistry getCommands() { return commandRegistry; }
        @Override public MessageGateway getMessages() { return messages; }
        @Override public TaskScheduler getScheduler() { return scheduler; }
        @Override public BindingService getBindings() { return bindings; }
        @Override public BindingVerificationService getBindingVerification() { return verification; }
        @Override public PluginLogger getLogger() { return logger; }
        @Override public boolean isClosed() { return contextClosed.get(); }

        private void requireOpen() {
            if (isClosed()) throw new IllegalStateException("Addon context is closed: " + descriptor.getId());
        }

        private <T extends Registration> T track(T registration) {
            resources.add(registration);
            if (isClosed()) registration.close();
            return registration;
        }

        @Override
        public void close() {
            if (!contextClosed.compareAndSet(false, true)) return;
            List<Registration> owned = new ArrayList<Registration>(resources);
            Collections.reverse(owned);
            for (Registration resource : owned) {
                try { resource.close(); }
                catch (Throwable error) { logger.error("Failed to close addon-owned resource", error); }
            }
            resources.clear();
            contexts.remove(normalize(descriptor.getId()), this);
            logger.info("Closed addon context");
        }
    }

    private static final class CommandEntry {
        final Context owner;
        final CommandSpec spec;
        final CommandHandler handler;
        final Set<String> tokens;
        CommandEntry(Context owner, CommandSpec spec, CommandHandler handler, Set<String> tokens) {
            this.owner = owner;
            this.spec = spec;
            this.handler = handler;
            this.tokens = Collections.unmodifiableSet(new LinkedHashSet<String>(tokens));
        }
    }

    private static final class ScopedMessages implements MessageGateway {
        private final Context owner;
        private final MessageGateway delegate;
        ScopedMessages(Context owner, MessageGateway delegate) { this.owner = owner; this.delegate = delegate; }

        @Override public CompletionStage<SendResult> replyText(MessageReference ref, String text) {
            return owner.isClosed() ? closed(owner) : delegate.replyText(ref, text);
        }
        @Override public CompletionStage<SendResult> replyImage(
            MessageReference ref, byte[] bytes, String mime, String name, String text
        ) { return owner.isClosed() ? closed(owner) : delegate.replyImage(ref, bytes, mime, name, text); }
        @Override public CompletionStage<SendResult> sendText(String group, String text) {
            return owner.isClosed() ? closed(owner) : delegate.sendText(group, text);
        }
        @Override public CompletionStage<SendResult> sendImage(
            String group, byte[] bytes, String mime, String name, String text
        ) { return owner.isClosed() ? closed(owner) : delegate.sendImage(group, bytes, mime, name, text); }

        private static CompletionStage<SendResult> closed(Context owner) {
            return CompletableFuture.completedFuture(SendResult.of(
                SendResult.Status.FAILED, "Addon context is closed: " + owner.descriptor.getId()
            ));
        }
    }

    private static final class ScopedScheduler implements TaskScheduler {
        private final Context owner;
        private final TaskScheduler delegate;
        ScopedScheduler(Context owner, TaskScheduler delegate) { this.owner = owner; this.delegate = delegate; }
        @Override public TaskHandle runSync(Runnable task) { owner.requireOpen(); return owner.track(delegate.runSync(task)); }
        @Override public TaskHandle runAsync(Runnable task) { owner.requireOpen(); return owner.track(delegate.runAsync(task)); }
        @Override public TaskHandle runLater(Duration delay, Runnable task) {
            owner.requireOpen(); return owner.track(delegate.runLater(delay, task));
        }
        @Override public TaskHandle runTimer(Duration initial, Duration period, Runnable task) {
            owner.requireOpen(); return owner.track(delegate.runTimer(initial, period, task));
        }
    }

    private static final class BukkitScheduler implements TaskScheduler {
        private final JavaPlugin plugin;
        private final PluginLogger logger;
        BukkitScheduler(JavaPlugin plugin, PluginLogger logger) { this.plugin = plugin; this.logger = logger; }
        @Override public TaskHandle runSync(Runnable task) {
            return handle(plugin.getServer().getScheduler().runTask(plugin, guarded(task)));
        }
        @Override public TaskHandle runAsync(Runnable task) {
            return handle(plugin.getServer().getScheduler().runTaskAsynchronously(plugin, guarded(task)));
        }
        @Override public TaskHandle runLater(Duration delay, Runnable task) {
            return handle(plugin.getServer().getScheduler().runTaskLater(plugin, guarded(task), ticks(delay, true)));
        }
        @Override public TaskHandle runTimer(Duration initial, Duration period, Runnable task) {
            return handle(plugin.getServer().getScheduler().runTaskTimer(
                plugin, guarded(task), ticks(initial, true), ticks(period, false)
            ));
        }
        private Runnable guarded(Runnable task) {
            return () -> {
                try { task.run(); }
                catch (Throwable error) {
                    logger.error("Addon scheduled task failed", error);
                    if (error instanceof VirtualMachineError) throw (VirtualMachineError) error;
                    if (error instanceof ThreadDeath) throw (ThreadDeath) error;
                    if (error instanceof LinkageError) throw (LinkageError) error;
                }
            };
        }
        private static TaskHandle handle(BukkitTask task) { return new BukkitHandle(task); }
        private static long ticks(Duration duration, boolean allowZero) {
            if (duration.isNegative()) throw new IllegalArgumentException("Task delay must not be negative");
            long millis = duration.toMillis();
            if (millis == 0L) {
                if (!allowZero) throw new IllegalArgumentException("Repeating task period must be positive");
                return 0L;
            }
            return millis / 50L + (millis % 50L == 0L ? 0L : 1L);
        }
    }

    private static final class BukkitHandle implements TaskHandle {
        private final BukkitTask task;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        BukkitHandle(BukkitTask task) { this.task = task; }
        @Override public boolean isClosed() { return cancelled.get(); }
        @Override public boolean cancel() {
            if (!cancelled.compareAndSet(false, true)) return false;
            task.cancel();
            return true;
        }
        @Override public void close() { cancel(); }
    }

    private static final class OwnerLogger implements PluginLogger {
        private final JavaPlugin plugin;
        private final String prefix;
        OwnerLogger(JavaPlugin plugin, PluginDescriptor descriptor) {
            this.plugin = plugin;
            this.prefix = "[addon:" + descriptor.getId() + "/" + descriptor.getVersion() + "] ";
        }
        @Override public void info(String message) { plugin.getLogger().info(prefix + message); }
        @Override public void warning(String message) { plugin.getLogger().warning(prefix + message); }
        @Override public void error(String message, Throwable error) {
            plugin.getLogger().log(Level.SEVERE, prefix + message, error);
        }
    }

    private static CommandInvocation parseInvocation(String content) {
        String cleaned = MENTION.matcher(content == null ? "" : content).replaceAll("").trim();
        if (cleaned.startsWith("/")) cleaned = cleaned.substring(1).trim();
        if (cleaned.isEmpty()) return null;
        String[] parts = cleaned.split("\\s+", 2);
        return new CommandInvocation(parts[0], parts.length == 1 ? "" : parts[1]);
    }

    private static String audit(CommandEntry entry, BotMessage message) {
        return "addon=" + entry.owner.descriptor.getId() + "/" + entry.owner.descriptor.getVersion() +
            " api=" + ApiVersion.CURRENT + " command=" + entry.spec.getId() +
            " messageId=" + message.getMessageId() + " group=" + message.getGroupOpenId();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable value = error;
        while ((value instanceof CompletionException || value instanceof ExecutionException) &&
            value.getCause() != null) value = value.getCause();
        return value;
    }

    private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private static String optional(String value) { return value == null ? "" : value.trim(); }
}
