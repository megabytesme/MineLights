package megabytesme.minelights.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class SimpleJsonConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final String modId;

    public SimpleJsonConfig(String modId) {
        this.modId = modId;
    }

    public <T> T load(Class<T> configClass, T defaultConfig) {
        File configFile = getConfigFile();
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                return GSON.fromJson(reader, configClass);
            } catch (IOException e) {
                throw new RuntimeException("Could not read config file!", e);
            }
        } else {
            save(defaultConfig);
            return defaultConfig;
        }
    }

    public <T> void save(T config) {
        File configFile = getConfigFile();
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            throw new RuntimeException("Could not save config file!", e);
        }
    }

    private File getConfigFile() {
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), modId + ".json");
    }
}