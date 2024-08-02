package com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.statics;

import com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.utils.ProxyPodUtil;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public enum InitFile {
  SPIGOT_YAML("spigot.yml", "https://raw.githubusercontent.com/dayyeeet/minecraft-default-configs/main/%s/spigot.yml"),
  PAPER_GLOBAL_YML("paper-global.yml", "https://raw.githubusercontent.com/dayyeeet/minecraft-default-configs/main/%s/paper-global.yml"),
  PAPER_WORLD_DEFAULTS_YML("paper-world-defaults.yml", "https://raw.githubusercontent.com/dayyeeet/minecraft-default-configs/main/%s/paper-world-defaults.yml"),
  SERVER_PROPERTIES("server.properties", "https://server.properties"),
  ETC("", "");

  private static final Logger log = LoggerFactory.getLogger(InitFile.class);
  private final String fileName;
  private final String filePath;

  public String getFileName() {
    return this.fileName;
  }
  public String getFilePath(String version) {
    return this.filePath.formatted(version);
  }

  InitFile(String name, String path) {
    this.fileName = name;
    this.filePath = path;
  }

  public String getRawData(MinecraftServerGroup resource) {
    if (getFileName().equals("paper-global.yml")) {
      return getPaperGlobalYaml(resource);
    }
    if (getFileName().equals("server.properties")) {
      return getServerProperties(resource);
    }
    return downloadFile(getFilePath(resource.getSpec().getVersion()));
  }

  private String getPaperGlobalYaml(MinecraftServerGroup resource) {
    final String paperGlobalYml = downloadFile(getFilePath(resource.getSpec().getVersion()));
    final Yaml yaml = new Yaml();
    final Map<String, Object> paperGlobalConfig = yaml.load(paperGlobalYml);
    final Map<String, Object> proxies = (Map<String, Object>) paperGlobalConfig.get("proxies");
    if (proxies != null) {
      Map<String, Object> velocity = (Map<String, Object>) proxies.get("velocity");
      if (velocity != null) {
        velocity.put("enabled", true);
        velocity.put("online-mode", false);
        velocity.put("secret", ProxyPodUtil.SECRET);
      }
    }
    final DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    final Yaml modifiedYaml = new Yaml(options);
    return modifiedYaml.dump(paperGlobalConfig);
  }

  private String getServerProperties(MinecraftServerGroup resource) {
    try {
      final String serverProperties = downloadFile(getFilePath(resource.getSpec().getVersion()));
      Properties tmpServerProperties = new Properties();
      tmpServerProperties.load(new ByteArrayInputStream(serverProperties.getBytes(StandardCharsets.UTF_8)));
      tmpServerProperties.setProperty("online-mode", "false");
      StringWriter writer = new StringWriter();
      tmpServerProperties.list(new PrintWriter(writer));
      return writer.getBuffer().toString();
    }
    catch (Exception exception) {
      log.error("Default ConfigMap or Properties downloading is failed");
    }
    return null;
  }

  private static String downloadFile(String fileURL) {
    StringBuilder content = new StringBuilder();
    try {
      URL url = new URL(fileURL);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");

      try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
        content.append(in.lines().collect(Collectors.joining("\n")));
      }
    } catch (Exception e) {
      log.error("Error downloading file from URL: {}", fileURL, e);
    }
    return content.toString();
  }
}
