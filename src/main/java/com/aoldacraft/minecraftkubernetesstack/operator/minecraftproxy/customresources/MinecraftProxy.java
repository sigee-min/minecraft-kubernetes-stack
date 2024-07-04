package com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.customresources;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("aoldacraft.com")
@Version("v1alpha1")
@ShortNames("mp")
public class MinecraftProxy extends CustomResource<MinecraftProxySpec, MinecraftProxyStatus> implements Namespaced {
}
