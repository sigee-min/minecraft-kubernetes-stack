package com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.utils;

import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroup;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.statics.ServerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ResourceUtil {

  private static final Logger log = LoggerFactory.getLogger(ResourceUtil.class);

  public static String getPodName(MinecraftServerGroup resource, int index) {
    return resource.getMetadata().getName() + "-" + index;
  }

  public static Map<String, String> createLabels(MinecraftServerGroup resource, int index) {
    if(index < 0) {
      return Map.of(
              ServerData.LABEL_GROUP, resource.getMetadata().getName()
      );
    }
    return Map.of(
            ServerData.LABEL_GROUP, resource.getMetadata().getName(),
            "pod-index", String.valueOf(index)
    );
  }

  public static String getConfigMapName(MinecraftServerGroup resource) {
    return "minecraft-config-" + resource.getMetadata().getName();
  }
}
