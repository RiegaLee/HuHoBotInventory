package cn.huohuas001.huhobot.inventory.host;

import cn.huohuas001.huhobot.api.BindingChallengeRequest;
import cn.huohuas001.huhobot.api.BindingChallengeResult;
import cn.huohuas001.huhobot.api.BindingConfirmation;
import cn.huohuas001.huhobot.api.BindingService;
import cn.huohuas001.huhobot.api.BindingVerificationResult;
import cn.huohuas001.huhobot.api.BindingVerificationService;
import cn.huohuas001.huhobot.api.BindingVerificationState;
import cn.huohuas001.huhobot.api.PlayerBinding;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves an independently installed binding authority on every call and falls back to the
 * official AGENT branch's legacy single-account repository when no external authority exists.
 */
final class DynamicBindingServices implements BindingService, BindingVerificationService {
    private final ServicesManager services;
    private final Logger logger;
    private volatile boolean agentLookupFailureLogged;

    DynamicBindingServices(ServicesManager services, Logger logger) {
        this.services = services;
        this.logger = logger;
    }

    @Override
    public Optional<PlayerBinding> findBinding(String groupId, String userId) {
        BindingService provider = bindingProvider();
        if (provider != null) return provider.findBinding(groupId, userId);
        return findAgentBinding(groupId, userId);
    }

    @Override
    public List<PlayerBinding> findBindings(String groupId, String userId) {
        BindingService provider = bindingProvider();
        if (provider != null) return provider.findBindings(groupId, userId);
        Optional<PlayerBinding> value = findAgentBinding(groupId, userId);
        return value.isPresent()
            ? Collections.singletonList(value.get())
            : Collections.<PlayerBinding>emptyList();
    }

    @Override
    public BindingChallengeResult createChallenge(BindingChallengeRequest request) {
        BindingVerificationService provider = verificationProvider();
        return provider == null
            ? BindingChallengeResult.of(BindingChallengeResult.Status.UNAVAILABLE)
            : provider.createChallenge(request);
    }

    @Override
    public BindingVerificationResult confirmChallenge(BindingConfirmation confirmation) {
        BindingVerificationService provider = verificationProvider();
        return provider == null
            ? BindingVerificationResult.rejected(BindingVerificationResult.Status.UNAVAILABLE, 0)
            : provider.confirmChallenge(confirmation);
    }

    private BindingService bindingProvider() {
        RegisteredServiceProvider<BindingService> registration =
            services.getRegistration(BindingService.class);
        BindingService provider = registration == null ? null : registration.getProvider();
        return provider == this ? null : provider;
    }

    private BindingVerificationService verificationProvider() {
        RegisteredServiceProvider<BindingVerificationService> registration =
            services.getRegistration(BindingVerificationService.class);
        BindingVerificationService provider = registration == null ? null : registration.getProvider();
        return provider == this ? null : provider;
    }

    private Optional<PlayerBinding> findAgentBinding(String groupId, String userId) {
        if (isBlank(groupId) || isBlank(userId)) return Optional.empty();
        try {
            ClassLoader loader = getClass().getClassLoader().getParent();
            Class<?> repositoriesClass;
            try {
                repositoriesClass = Class.forName(
                    "cn.huohuas001.bot.state.CommandRepositories", false, loader
                );
            } catch (ClassNotFoundException ignored) {
                repositoriesClass = Class.forName("cn.huohuas001.bot.state.CommandRepositories");
            }
            Field instanceField = repositoriesClass.getField("INSTANCE");
            Object repositories = instanceField.get(null);
            Object bindings = repositoriesClass.getMethod("getBindings").invoke(repositories);
            Method getBinding = bindings.getClass().getMethod("getBinding", String.class, String.class);
            Object binding = getBinding.invoke(bindings, groupId, userId);
            if (binding == null) return Optional.empty();
            Object playerName = binding.getClass().getMethod("getPlayerName").invoke(binding);
            if (!(playerName instanceof String) || isBlank((String) playerName)) return Optional.empty();
            // AGENT 1.6.1 stores only a player name. Do not invent UUID or verification proof.
            return Optional.of(new PlayerBinding(
                ((String) playerName).trim(), null, BindingVerificationState.LEGACY_UNVERIFIED
            ));
        } catch (ClassNotFoundException ignored) {
            return Optional.empty();
        } catch (Throwable error) {
            if (!agentLookupFailureLogged) {
                agentLookupFailureLogged = true;
                logger.log(Level.WARNING, "读取 AGENT 内置绑定仓库失败，将按未绑定处理", error);
            }
            return Optional.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
