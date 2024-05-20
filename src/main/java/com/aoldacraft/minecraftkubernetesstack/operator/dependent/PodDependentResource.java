package com.aoldacraft.minecraftkubernetesstack.operator.dependent;

import com.aoldacraft.minecraftkubernetesstack.operator.customresources.MinecraftServerGroup;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodIP;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesResourceMatcher;

import java.util.Set;
import java.util.stream.Collectors;

public class PodDependentResource extends CRUDKubernetesDependentResource<Pod, MinecraftServerGroup> {

    private final KubernetesClient kubernetesClient;

    public PodDependentResource(KubernetesClient kubernetesClient) {
        super(Pod.class);
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public Matcher.Result<Pod> match(Pod actualResource, MinecraftServerGroup primary,
                                     Context<MinecraftServerGroup> context) {
        String primaryName = primary.getMetadata().getName();
        String podLabel = actualResource.getMetadata().getLabels().get("minecraftservergroup");

        boolean labelsMatch = primaryName.equals(podLabel);

        Set<String> currentIPs = kubernetesClient.pods().inNamespace(primary.getMetadata().getNamespace())
                .withLabel("minecraftservergroup", primary.getMetadata().getName())
                .list().getItems().stream()
                .filter(pod -> "Running".equals(pod.getStatus().getPhase()))
                .map(pod -> pod.getStatus().getPodIP())
                .collect(Collectors.toSet());

        Set<String> actualIPs = actualResource.getStatus().getPodIPs().stream()
                .map(PodIP::getIp)
                .collect(Collectors.toSet());

        boolean ipsMatch = currentIPs.equals(actualIPs);
        Pod desiredResource = desired(primary, context);
        return GenericKubernetesResourceMatcher.match(desiredResource, actualResource, labelsMatch, labelsMatch, ipsMatch, context);
    }

    @Override
    public Pod desired(MinecraftServerGroup primary, Context<MinecraftServerGroup> context) {
        return new PodBuilder()
                .withNewMetadata()
                .withNamespace(primary.getMetadata().getNamespace())
                .addToLabels("minecraftservergroup", primary.getMetadata().getName())
                .addToLabels("app", primary.getMetadata().getName())
                .endMetadata()
                .build();
    }

    @Override
    public ReconcileResult<Pod> reconcile(MinecraftServerGroup primary, Context<MinecraftServerGroup> context) {
        Pod desiredPod = desired(primary, context);
        // Implement logic to create or update the Pod
        return null;
    }

    @Override
    public void delete(MinecraftServerGroup primary, Context<MinecraftServerGroup> context) {
        // Implement logic to delete the Pod
    }
}