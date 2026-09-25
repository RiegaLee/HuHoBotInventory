package cn.huohuas001.huhobot.inventory.host;

import cn.huohuas001.huhobot.api.BindingChallengeRequest;
import cn.huohuas001.huhobot.api.BindingChallengeResult;
import cn.huohuas001.huhobot.api.BindingConfirmation;
import cn.huohuas001.huhobot.api.BindingService;
import cn.huohuas001.huhobot.api.BindingVerificationResult;
import cn.huohuas001.huhobot.api.BindingVerificationService;
import cn.huohuas001.huhobot.api.PlayerBinding;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Resolves the Mainline binding authority dynamically on every call. */
final class DynamicBindingServices implements BindingService, BindingVerificationService {
    private final ServicesManager services;

    DynamicBindingServices(ServicesManager services) {
        this.services = services;
    }

    @Override
    public Optional<PlayerBinding> findBinding(String groupId, String userId) {
        BindingService provider = bindingProvider();
        if (provider != null) return provider.findBinding(groupId, userId);
        return Optional.empty();
    }

    @Override
    public List<PlayerBinding> findBindings(String groupId, String userId) {
        BindingService provider = bindingProvider();
        if (provider != null) return provider.findBindings(groupId, userId);
        return Collections.emptyList();
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

}
