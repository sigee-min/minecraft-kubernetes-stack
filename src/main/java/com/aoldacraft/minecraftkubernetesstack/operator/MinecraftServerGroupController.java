package com.aoldacraft.minecraftkubernetesstack.operator;

import com.aoldacraft.minecraftkubernetesstack.operator.customresources.MinecraftServerGroup;
import com.aoldacraft.minecraftkubernetesstack.operator.customresources.MinecraftServerGroupStatus;
import com.aoldacraft.minecraftkubernetesstack.operator.dependent.PodDependentResource;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ControllerConfiguration
public class MinecraftServerGroupController implements Reconciler<MinecraftServerGroup>, EventSourceInitializer<MinecraftServerGroup>, Deleter<MinecraftServerGroup> {
    private static final String LABEL_APP = "app";
    private static final String LABEL_GROUP = "minecraftservergroup";

    private final Logger log = LoggerFactory.getLogger(MinecraftServerGroupController.class);
    private final KubernetesClient kubernetesClient;
    private PodDependentResource podDependentResource;

    public MinecraftServerGroupController(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        createDependentResources();
    }

    private void createDependentResources() {
        this.podDependentResource = new PodDependentResource(kubernetesClient);
    }

    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<MinecraftServerGroup> context) {
        Map<String, EventSource> eventSources = new HashMap<>();
        eventSources.put("podDependentResource", podDependentResource.initEventSource(context));
        return eventSources;
    }

    @Override
    public UpdateControl<MinecraftServerGroup> reconcile(MinecraftServerGroup resource, Context<MinecraftServerGroup> context) {
        log.info("Reconciling MinecraftServerGroup: {}", resource.getMetadata().getName());

        Deployment deployment = ensureDeploymentExists(resource);

        updateStatus(resource, deployment);

        // Reconcile the Pod
        podDependentResource.reconcile(resource, context);

        return UpdateControl.updateResourceAndPatchStatus(resource);
    }

    @Override
    public void delete(MinecraftServerGroup resource, Context<MinecraftServerGroup> context) {
        Deployment deployment = getDeploymentExist(resource);
        if(deployment != null) {
            kubernetesClient.resource(deployment).delete();
        }
    }

    private Deployment ensureDeploymentExists(MinecraftServerGroup resource) {
        Deployment existingDeployment = getDeploymentExist(resource);

        if (existingDeployment == null) {
            log.info("Create MinecraftServerGroup Deployment: %s to %s".formatted(resource.getMetadata().getName(), resource.getMetadata().getNamespace()));
            return createDeployment(resource);
        } else {
            return updateDeploymentIfNeeded(existingDeployment, resource);
        }
    }

    private Deployment createDeployment(MinecraftServerGroup resource) {
        Map<String, String> labels = createLabels(resource);

        Deployment deployment = new DeploymentBuilder()
                .editOrNewMetadata()
                .withName(resource.getMetadata().getName())
                .withNamespace(resource.getMetadata().getNamespace())
                .withLabels(labels)
                .endMetadata()
                .editOrNewSpec()
                .withSelector(new LabelSelectorBuilder().withMatchLabels(labels).build())
                .withReplicas(resource.getSpec().getReplicas())
                .editOrNewTemplate()
                .editOrNewMetadata()
                .withLabels(labels)
                .endMetadata()
                .editOrNewSpec()
                .addAllToContainers(createMinecraftContainers(resource))
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        kubernetesClient.apps().deployments().inNamespace(resource.getMetadata().getNamespace()).create(deployment);
        return deployment;
    }


    private Deployment updateDeploymentIfNeeded(Deployment existingDeployment, MinecraftServerGroup resource) {
        boolean modified = false;
        Map<String, String> labels = createLabels(resource);

        if (!existingDeployment.getSpec().getReplicas().equals(resource.getSpec().getReplicas())) {
            existingDeployment.getSpec().setReplicas(resource.getSpec().getReplicas());
            modified = true;
        }

        if (!existingDeployment.getMetadata().getLabels().equals(labels)) {
            existingDeployment.getMetadata().setLabels(labels);
            existingDeployment.getSpec().getTemplate().getMetadata().setLabels(labels);
            existingDeployment.getSpec().setSelector(new LabelSelectorBuilder().withMatchLabels(labels).build());
            modified = true;
        }

        // Update the containers if necessary.
        List<Container> desiredContainers = createMinecraftContainers(resource);
        if (!existingDeployment.getSpec().getTemplate().getSpec().getContainers().equals(desiredContainers)) {
            existingDeployment.getSpec().getTemplate().getSpec().setContainers(desiredContainers);
            modified = true;
        }

        if (modified) {
            kubernetesClient.apps().deployments().inNamespace(resource.getMetadata().getNamespace()).replace(existingDeployment);
        }

        return existingDeployment;
    }


    private Map<String, String> createLabels(MinecraftServerGroup resource) {
        return Map.of(
                LABEL_APP, resource.getMetadata().getName(),
                LABEL_GROUP, resource.getMetadata().getName()
        );
    }

    private void updateStatus(MinecraftServerGroup resource, Deployment deployment) {
        Set<String> podIPs = getPodIPsSet(resource);
        MinecraftServerGroupStatus status = resource.getStatus() == null ? new MinecraftServerGroupStatus() : resource.getStatus();

        status.setPodIPs(List.copyOf(podIPs));
        status.setState(podIPs.isEmpty() ? "Not Ready" : "Ready");
        resource.setStatus(status);
    }

    public Set<String> getPodIPsSet(MinecraftServerGroup primary) {
        return kubernetesClient.pods().inNamespace(primary.getMetadata().getNamespace())
                .withLabel(LABEL_GROUP, primary.getMetadata().getName())
                .list().getItems().stream()
                .filter(pod -> "Running".equals(pod.getStatus().getPhase()))
                .map(pod -> pod.getStatus().getPodIP())
                .collect(Collectors.toSet());
    }

    private Deployment getDeploymentExist(MinecraftServerGroup resource) {
        return kubernetesClient.apps().deployments()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName())
                .get();
    }

    private List<Container> createMinecraftContainers(MinecraftServerGroup resource) {
        Container container = new ContainerBuilder()
                .withName("minecraft")
                .withImage("itzg/minecraft-server:latest")
                .withEnv(new EnvVar("EULA", "TRUE", null))
                .withPorts(new ContainerPortBuilder().withContainerPort(25565).build())
                .withNewResourcesLike(resource.getSpec().getResourceRequirements())
                .endResources()
                .build();

        return List.of(container);
    }


}
