package com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.entities;

import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroup;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroupSpec;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroupStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "minecraftservergroups")
@Data
public class MinecraftServerGroupInfo {

    @Id
    private String uuid;
    private String name;
    private String namespace;
    private MinecraftServerGroupSpec spec;
    private MinecraftServerGroupStatus status;
    private boolean active;

    public static MinecraftServerGroupInfo makeFromCRD(MinecraftServerGroup crd) {
        MinecraftServerGroupInfo res = new MinecraftServerGroupInfo();
        res.setName(crd.getMetadata().getName());
        res.setNamespace(crd.getMetadata().getNamespace());
        res.setSpec(crd.getSpec());
        res.setStatus(crd.getStatus());
        return res;
    }
}