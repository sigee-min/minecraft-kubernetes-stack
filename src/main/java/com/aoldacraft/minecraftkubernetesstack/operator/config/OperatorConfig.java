package com.aoldacraft.minecraftkubernetesstack.operator.config;

import com.aoldacraft.minecraftkubernetesstack.operator.MinecraftServerGroupController;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OperatorConfig {
    @Bean
    public MinecraftServerGroupController minecraftServerGroupController() {
        return new MinecraftServerGroupController(new KubernetesClientBuilder().build());
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @SuppressWarnings("rawtypes")
    public Operator operator(List<Reconciler> controllers) {
        Operator operator = new Operator();
        controllers.forEach(operator::register);
        return operator;
    }
}
