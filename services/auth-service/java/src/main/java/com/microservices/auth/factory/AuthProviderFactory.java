package com.microservices.auth.factory;

import com.microservices.auth.enums.AuthProviderType;
import com.microservices.auth.interfaces.AuthProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AuthProviderFactory {

    private final Map<AuthProviderType, AuthProvider> providers = new HashMap<>();

    public AuthProviderFactory(List<AuthProvider> providerList) {
        for (AuthProvider provider : providerList) {
            providers.put(provider.getProviderType(), provider);
        }
    }

    public AuthProvider getProvider(AuthProviderType providerType) {
        AuthProvider provider = providers.get(providerType);

        if (provider == null) {
            throw new RuntimeException("Unsupported auth provider: " + providerType);
        }

        return provider;
    }
}