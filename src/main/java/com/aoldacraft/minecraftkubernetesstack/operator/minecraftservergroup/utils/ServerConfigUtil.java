package com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.utils;

import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroup;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroupStatus;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.statics.InitFile;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.statics.ServerData;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ServerConfigUtil {
  private static final Logger log = LoggerFactory.getLogger(ServerConfigUtil.class);
  private final KubernetesClient kubernetesClient;

  public Boolean sync(MinecraftServerGroup resource) {
    final ConfigMap cm = getConfigMap(resource);
    if(cm != null) {
      return false;
    }
    final ConfigMap configMap = new ConfigMapBuilder()
            .withNewMetadata()
            .withName(ResourceUtil.getConfigMapName(resource))
            .withLabels(ResourceUtil.createLabels(resource,-1))
            .withNamespace(resource.getMetadata().getNamespace())
            .endMetadata()
            .addToData(InitFile.SPIGOT_YAML.getFileName(), InitFile.SPIGOT_YAML.getRawData(resource))
            .addToData(InitFile.PAPER_GLOBAL_YML.getFileName(), InitFile.PAPER_GLOBAL_YML.getRawData(resource))
            .addToData(InitFile.PAPER_WORLD_DEFAULTS_YML.getFileName(), InitFile.PAPER_WORLD_DEFAULTS_YML.getRawData(resource))
            .addToData(InitFile.SERVER_PROPERTIES.getFileName(), InitFile.SERVER_PROPERTIES.getRawData(resource))
            .build();

    kubernetesClient.configMaps()
            .inNamespace(resource.getMetadata().getNamespace())
            .createOrReplace(configMap);
    log.info("Created/Updated ConfigMap for MinecraftServerGroup: {}", resource.getMetadata().getName());
    return true;
  }

  public void delete(MinecraftServerGroup resource) {
    kubernetesClient.configMaps()
            .inNamespace(resource.getMetadata().getNamespace())
            .withName("minecraft-config-" + resource.getMetadata().getName())
            .delete();
    log.info("Deleted ConfigMap for MinecraftServerGroup: {}", resource.getMetadata().getName());
  }

  public void updateStatus(MinecraftServerGroup resource, MinecraftServerGroupStatus status) {
    final ConfigMap configMap = getConfigMap(resource);
    if(configMap == null) {
      return;
    }
    status.setConfigMapObservedGeneration(configMap.getMetadata().getGeneration());
  }

  public ConfigMap getConfigMap(MinecraftServerGroup resource) {
    List<ConfigMap> configMaps = kubernetesClient.configMaps()
            .inNamespace(resource.getMetadata().getNamespace())
            .withLabel(ServerData.LABEL_GROUP, resource.getMetadata().getName())
            .list().getItems();

    if(configMaps == null || configMaps.isEmpty()) {
      return null;
    }
    if(configMaps.size() > 1) {
      log.warn("There are {} configMaps in {}.", configMaps.size(), resource.getMetadata().getName());
    }
    return configMaps.getFirst();
  }

}
