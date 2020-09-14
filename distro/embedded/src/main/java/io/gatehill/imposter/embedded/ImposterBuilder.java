package io.gatehill.imposter.embedded;

import io.gatehill.imposter.ImposterConfig;
import io.gatehill.imposter.plugin.Plugin;
import io.gatehill.imposter.server.ImposterVerticle;
import io.gatehill.imposter.server.util.ConfigUtil;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.gatehill.imposter.util.HttpUtil.DEFAULT_SERVER_FACTORY;
import static java.util.Collections.emptyMap;
import static java.util.Objects.nonNull;

/**
 * @author pete
 */
public class ImposterBuilder<SELF extends ImposterBuilder<SELF>> {
    private static final Logger LOGGER = LogManager.getLogger(ImposterBuilder.class);
    private static final String HOST = "localhost";
    private static final int IMPOSTER_DEFAULT_PORT = 8080;
    private static final String COMBINED_SPECIFICATION_URL = "/_spec/combined.json";
    private static final String SPECIFICATION_UI_URL = "/_spec/";

    private final List<Path> specificationFiles = new ArrayList<>();
    private final List<Path> configurationDirs = new ArrayList<>();
    private final Vertx vertx = Vertx.vertx();
    private Class<? extends Plugin> pluginClass;

    @SuppressWarnings("unchecked")
    private SELF self() {
        return (SELF) this;
    }

    /**
     * The plugin to use.
     *
     * @param pluginClass the plugin
     */
    public SELF withPluginClass(Class<? extends Plugin> pluginClass) {
        this.pluginClass = pluginClass;
        return self();
    }

    /**
     * The directory containing a valid Imposter configuration file.
     *
     * @param configurationDir the directory
     */
    public SELF withConfigurationDir(String configurationDir) {
        return withConfigurationDir(Paths.get(configurationDir));
    }

    /**
     * The directory containing a valid Imposter configuration file.
     *
     * @param configurationDir the directory
     */
    public SELF withConfigurationDir(Path configurationDir) {
        this.configurationDirs.add(configurationDir);
        return self();
    }

    /**
     * The path to a valid OpenAPI/Swagger specification file.
     *
     * @param specificationFile the directory
     */
    public SELF withSpecificationFile(String specificationFile) {
        return withSpecificationFile(Paths.get(specificationFile));
    }

    /**
     * The path to a valid OpenAPI/Swagger specification file.
     *
     * @param specificationFile the directory
     */
    public SELF withSpecificationFile(Path specificationFile) {
        this.specificationFiles.add(specificationFile);
        return self();
    }

    public CompletableFuture<MockEngine> start() {
        final CompletableFuture<MockEngine> future = new CompletableFuture<>();

        try {
            if (!specificationFiles.isEmpty() && !configurationDirs.isEmpty()) {
                throw new IllegalStateException("Must specify only one of specification file or specification directory");
            }
            if (!specificationFiles.isEmpty()) {
                withConfigurationDir(ConfigGenerator.writeImposterConfig(specificationFiles));
            }
            if (configurationDirs.isEmpty()) {
                throw new IllegalStateException("Must specify one of specification file or specification directory");
            }
            bootMockEngine(future);

        } catch (Exception e) {
            throw new ImposterLaunchException("Error starting Imposter container", e);
        }

        final MockEngine mockEngine = new MockEngine();

        LOGGER.info("Started Imposter mock engine" +
                "\n  Specification UI: " + mockEngine.getSpecificationUiUri() +
                "\n  Config dir(s): " + configurationDirs);

        future.complete(mockEngine);

        return future;
    }

    private void bootMockEngine(CompletableFuture<MockEngine> future) {
        ConfigUtil.resetConfig();
        configure(ConfigUtil.getConfig());

        // wait for the engine to parse and combine the specifications
        vertx.deployVerticle(ImposterVerticle.class.getCanonicalName(), completion -> {
            if (completion.succeeded()) {
                future.complete(null);
            } else {
                future.completeExceptionally(completion.cause());
            }
        });
    }

    private void configure(ImposterConfig imposterConfig) {
        imposterConfig.setServerFactory(DEFAULT_SERVER_FACTORY);
        imposterConfig.setHost(HOST);
        imposterConfig.setListenPort(findFreePort());
        imposterConfig.setPlugins(new String[]{pluginClass.getCanonicalName()});
        imposterConfig.setPluginArgs(emptyMap());

        imposterConfig.setConfigDirs(configurationDirs.stream().map(dir -> {
            try {
                return Paths.get(getClass().getResource(dir.toString()).toURI()).toString();
            } catch (Exception e) {
                throw new RuntimeException("Error parsing directory: " + dir, e);
            }
        }).toArray(String[]::new));
    }

    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Unable to find a free port");
        }
    }

    static class MockEngine {
        public URI getCombinedSpecificationUri() {
            try {
                return URI.create(getBaseUrl("http", IMPOSTER_DEFAULT_PORT) + COMBINED_SPECIFICATION_URL);
            } catch (MalformedURLException e) {
                throw new RuntimeException("Error getting combined specification URI", e);
            }
        }

        public URI getSpecificationUiUri() {
            try {
                return URI.create(getBaseUrl("http", IMPOSTER_DEFAULT_PORT) + SPECIFICATION_UI_URL);
            } catch (MalformedURLException e) {
                throw new RuntimeException("Error getting specification UI URI", e);
            }
        }

        public URL getBaseUrl(String scheme, int port) throws MalformedURLException {
            return new URL(scheme + "://" + HOST + ":" + port);
        }

        public URL getBaseUrl(String scheme) throws MalformedURLException {
            return getBaseUrl(scheme, IMPOSTER_DEFAULT_PORT);
        }

        public URL getBaseUrl() throws MalformedURLException {
            return getBaseUrl("http");
        }
    }
}
