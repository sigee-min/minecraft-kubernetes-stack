package com.aoldacraft.minecraftkubernetesstack.operator.config;

import com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.services.MinecraftGroupInfoService;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.MinecraftProxyOperator;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.MinecraftServerGroupOperator;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class OperatorConfig {

    private final MinecraftGroupInfoService minecraftGroupInfoService;
    private final KubernetesClient kubernetesClient;

    @Bean
    public MinecraftServerGroupOperator minecraftServerGroupController() {
        return new MinecraftServerGroupOperator(
                kubernetesClient,
                minecraftGroupInfoService
        );
    }

    @Bean
    public MinecraftProxyOperator minecraftProxyOperator() {
        return new MinecraftProxyOperator(
                kubernetesClient
        );
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @SuppressWarnings("rawtypes")
    public Operator operator(List<Reconciler> controllers) {
        Operator operator = new Operator();
        controllers.forEach(operator::register);
        return operator;
    }
}
