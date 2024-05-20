package com.aoldacraft.minecraftkubernetesstack.operator.customresources;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.*;

@Group("aoldacraft.com")
@Version("v1alpha1")
@ShortNames("msg")
public class MinecraftServerGroup extends CustomResource<MinecraftServerGroupSpec, MinecraftServerGroupStatus> implements Namespaced {
}
