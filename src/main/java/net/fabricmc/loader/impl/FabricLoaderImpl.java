/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.quiltmc.Fabric2FabricLoader;

import org.jetbrains.annotations.ApiStatus;
import net.fabricmc.loader.impl.discovery.RuntimeModRemapper;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.SemanticVersion;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.discovery.ClasspathModCandidateFinder;
import net.fabricmc.loader.impl.discovery.DirectoryModCandidateFinder;
import net.fabricmc.loader.impl.discovery.ModCandidate;
import net.fabricmc.loader.impl.discovery.ModResolutionException;
import net.fabricmc.loader.impl.discovery.ModResolver;
import net.fabricmc.loader.impl.discovery.ModSolvingError;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.gui.FabricGuiEntry;
import net.fabricmc.loader.impl.launch.common.FabricLauncher;
import net.fabricmc.loader.impl.launch.common.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.knot.Knot;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.qmj.AdapterLoadableClassEntry;
import net.fabricmc.loader.impl.metadata.qmj.InternalModMetadata;
import net.fabricmc.loader.impl.metadata.qmj.ModProvided;
import net.fabricmc.loader.impl.solver.ModSolveResult;
import net.fabricmc.loader.impl.util.DefaultLanguageAdapter;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;

import org.objectweb.asm.Opcodes;

/**
 * The main class for mod loading operations.
 */
@ApiStatus.Internal
public class FabricLoaderImpl {
	public static final FabricLoaderImpl INSTANCE = new FabricLoaderImpl();

	public static final int ASM_VERSION = Opcodes.ASM9;

	protected static Logger LOGGER = LogManager.getFormatterLogger("Fabric|Loader");

	public static final String DEFAULT_MODS_DIR = "mods";
	public static final String DEFAULT_CONFIG_DIR = "config";

	protected final Map<String, ModContainer> modMap = new HashMap<>();
	protected List<ModContainer> mods = new ArrayList<>();

	private final Map<String, LanguageAdapter> adapterMap = new HashMap<>();
	private final EntrypointStorage entrypointStorage = new EntrypointStorage();
	private final AccessWidener accessWidener = new AccessWidener();

	private boolean frozen = false;

	private Object gameInstance;

	private MappingResolver mappingResolver;
	private GameProvider provider;
	private Path gameDir;
	private Path configDir;
	private Path modsDir;

	protected FabricLoaderImpl() {
	}

	/**
	 * Freeze the FabricLoader, preventing additional mods from being loaded.
	 */
	public void freeze() {
		if (frozen) {
			throw new RuntimeException("Already frozen!");
		}

		frozen = true;
		finishModLoading();
	}

	public GameProvider getGameProvider() {
		if (provider == null) throw new IllegalStateException("game provider not set (yet)");

		return provider;
	}

	public void setGameProvider(GameProvider provider) {
		this.provider = provider;

		setGameDir(provider.getLaunchDirectory());
	}

	private void setGameDir(Path gameDir) {
		this.gameDir = gameDir;
		String configDir = System.getProperty(SystemProperties.CONFIG_DIRECTORY);
		this.configDir = gameDir.resolve((configDir == null || configDir.isEmpty()) ? DEFAULT_CONFIG_DIR : configDir);
		initializeModsDir(gameDir);
	}

	private void initializeModsDir(Path gameDir) {
		String modsDir = System.getProperty(SystemProperties.MODS_DIRECTORY);
		this.modsDir = gameDir.resolve((modsDir == null || modsDir.isEmpty()) ? DEFAULT_MODS_DIR : modsDir);
	}

	public Object getGameInstance() {
		return gameInstance;
	}

	public EnvType getEnvironmentType() {
		return FabricLauncherBase.getLauncher().getEnvironmentType();
	}

	/**
	 * @return The game instance's root directory.
	 */
	public Path getGameDir() {
		return gameDir;
	}

	/**
	 * @return The game instance's configuration directory.
	 */
	public Path getConfigDir() {
		if (configDir == null) {
			// May be null during tests
			// If this is in production then things are about to go very wrong.
			return null;
		}

		if (!Files.exists(configDir)) {
			try {
				Files.createDirectories(configDir);
			} catch (IOException e) {
				throw new RuntimeException(String.format("Failed to create config directory at '%s'", configDir), e);
			}
		}
		return configDir;
	}

	public Path getModsDir() {
		// modsDir should be initialized before this method is ever called, this acts as a very special failsafe
		if (modsDir == null) {
			initializeModsDir(gameDir);
		}

		if (!Files.exists(modsDir)) {
			try {
				Files.createDirectories(modsDir);
			} catch (IOException e) {
				throw new RuntimeException(String.format("Failed to create mods directory at '%s'", modsDir), e);
			}
		}
		return modsDir;
	}

	public void load() {
		if (provider == null) throw new IllegalStateException("game provider not set");
		if (frozen) throw new IllegalStateException("Frozen - cannot load additional mods!");

		try {
			setup();
		} catch (ModResolutionException exception) {
			FabricGuiEntry.displayCriticalError(exception, true);
		}
	}

	private void setup() throws ModResolutionException {
		ModResolver resolver = new ModResolver(this);
		resolver.addCandidateFinder(new ClasspathModCandidateFinder());
		resolver.addCandidateFinder(new DirectoryModCandidateFinder(getModsDir(), isDevelopmentEnvironment()));
		ModSolveResult result = resolver.resolve(this);
		Map<String, ModCandidate> candidateMap = result.modMap;

		String modListText = candidateMap.values().stream()
					.sorted(Comparator.comparing(candidate -> candidate.getMetadata().id()))
					.map(candidate -> String.format("\t- %s@%s", candidate.getMetadata().id(), candidate.getMetadata().version().raw()))
					.collect(Collectors.joining("\n"));

		String modText;
		switch (candidateMap.values().size()) {
			case 0:
				modText = "Loading %d mods";
				break;
			case 1:
				modText = "Loading %d mod:";
				break;
			default:
				modText = "Loading %d mods:";
				break;
		}

		LOGGER.info("[%s] " + modText + "%n%s", getClass().getSimpleName(), candidateMap.values().size(), modListText);

		if (DependencyOverrides.INSTANCE.getDependencyOverrides().size() > 0) {
			LOGGER.info(String.format("Dependencies overridden for \"%s\"", String.join(", ", DependencyOverrides.INSTANCE.getDependencyOverrides().keySet())));
		}

		boolean runtimeModRemapping = isDevelopmentEnvironment();

		if (runtimeModRemapping && System.getProperty(SystemProperties.REMAP_CLASSPATH_FILE) == null) {
			LOGGER.warn("Runtime mod remapping disabled due to no fabric.remapClasspathFile being specified. You may need to update loom.");
			runtimeModRemapping = false;
		}

		if (runtimeModRemapping) {
			for (ModCandidate candidate : RuntimeModRemapper.remap(candidateMap.values(), ModResolver.getInMemoryFs())) {
				addMod(candidate);
			}
		} else {
			for (ModCandidate candidate : candidateMap.values()) {
				addMod(candidate);
			}
		}
	}

	protected void finishModLoading() {
		// add mods to classpath
		// TODO: This can probably be made safer, but that's a long-term goal
		for (ModContainer mod : mods) {
			if (!mod.metadata().id().equals("quilt_loader") && !mod.getInfo().getType().equals("builtin")) {
				FabricLauncherBase.getLauncher().propose(mod.getOriginUrl());
			}
		}

		postprocessModMetadata();
		setupLanguageAdapters();
		setupMods();
	}

	public boolean hasEntrypoints(String key) {
		return entrypointStorage.hasEntrypoints(key);
	}

	public <T> List<T> getEntrypoints(String key, Class<T> type) {
		return entrypointStorage.getEntrypoints(key, type);
	}

	public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
		return entrypointStorage.getEntrypointContainers(key, type);
	}

	public MappingResolver getMappingResolver() {
		if (mappingResolver == null) {
			mappingResolver = new FabricMappingResolver(
				FabricLauncherBase.getLauncher().getMappingConfiguration()::getMappings,
				FabricLauncherBase.getLauncher().getTargetNamespace()
			);
		}

		return mappingResolver;
	}

	public Optional<net.fabricmc.loader.api.ModContainer> getModContainer(String id) {
		return Optional.ofNullable(modMap.get(id));
	}

	public Collection<net.fabricmc.loader.api.ModContainer> getAllMods() {
		return Collections.unmodifiableList(mods);
	}

	public boolean isModLoaded(String id) {
		return modMap.containsKey(id);
	}

	public boolean isDevelopmentEnvironment() {
		FabricLauncher launcher = FabricLauncherBase.getLauncher();
		if (launcher == null) {
			// Most likely a test
			return true;
		}
		return launcher.isDevelopment();
	}

	/**
	 * @return A list of all loaded mods, as ModContainers.
	 * @deprecated Use {@link net.fabricmc.loader.api.FabricLoader#getAllMods()}
	 */
	@Deprecated
	public Collection<ModContainer> getModContainers() {
		return Collections.unmodifiableList(mods);
	}

	@Deprecated
	public List<ModContainer> getMods() {
		return Collections.unmodifiableList(mods);
	}

	protected void addMod(ModCandidate candidate) throws ModResolutionException {
		InternalModMetadata meta = candidate.getMetadata();
		URL originUrl = candidate.getOriginUrl();

		if (modMap.containsKey(meta.id())) {
			throw new ModSolvingError("Duplicate mod ID: " + meta.id() + "! (" + modMap.get(meta.id()).getOriginUrl().getFile() + ", " + originUrl.getFile() + ")");
		}

		if (!meta.environment().matches(getEnvironmentType())) {
			return;
		}

		ModContainer container = new ModContainer(meta, originUrl);
		mods.add(container);
		modMap.put(meta.id(), container);
		for (ModProvided provided : meta.provides()) {
			if(modMap.containsKey(provided.id)) {
				throw new ModSolvingError("Duplicate provided alias: " + provided + "! (" + modMap.get(meta.id()).getOriginUrl().getFile() + ", " + originUrl.getFile() + ")");
			}
			modMap.put(provided.id, container);
		}
	}

	protected void postprocessModMetadata() {
		for (ModContainer mod : mods) {
			if (!(mod.getInfo().getVersion() instanceof SemanticVersion)) {
				LOGGER.warn("Mod `" + mod.getInfo().getId() + "` (" + mod.getInfo().getVersion().getFriendlyString() + ") does not respect SemVer - comparison support is limited.");
			} else if (((SemanticVersion) mod.getInfo().getVersion()).getVersionComponentCount() >= 4) {
				LOGGER.warn("Mod `" + mod.getInfo().getId() + "` (" + mod.getInfo().getVersion().getFriendlyString() + ") uses more dot-separated version components than SemVer allows; support for this is currently not guaranteed.");
			}
		}
	}

	/* private void sortMods() {
		LOGGER.debug("Sorting mods");

		LinkedList<ModContainer> sorted = new LinkedList<>();
		for (ModContainer mod : mods) {
			if (sorted.isEmpty() || mod.getInfo().getRequires().size() == 0) {
				sorted.addFirst(mod);
			} else {
				boolean b = false;
				l1:
				for (int i = 0; i < sorted.size(); i++) {
					for (Map.Entry<String, ModMetadataV0.Dependency> entry : sorted.get(i).getInfo().getRequires().entrySet()) {
						String depId = entry.getKey();
						ModMetadataV0.Dependency dep = entry.getValue();

						if (depId.equalsIgnoreCase(mod.getInfo().getId()) && dep.satisfiedBy(mod.getInfo())) {
							sorted.add(i, mod);
							b = true;
							break l1;
						}
					}
				}

				if (!b) {
					sorted.addLast(mod);
				}
			}
		}

		mods = sorted;
	} */

	private void setupLanguageAdapters() {
		adapterMap.put("default", DefaultLanguageAdapter.INSTANCE);

		for (ModContainer mod : mods) {
			// add language adapters
			for (Map.Entry<String, String> laEntry : mod.getInternalMeta().languageAdapters().entrySet()) {
				if (adapterMap.containsKey(laEntry.getKey())) {
					throw new RuntimeException("Duplicate language adapter key: " + laEntry.getKey() + "! (" + laEntry.getValue() + ", " + adapterMap.get(laEntry.getKey()).getClass().getName() + ")");
				}

				try {
					adapterMap.put(laEntry.getKey(), (LanguageAdapter) Class.forName(laEntry.getValue(), true, FabricLauncherBase.getLauncher().getTargetClassLoader()).getDeclaredConstructor().newInstance());
				} catch (Exception e) {
					throw new RuntimeException("Failed to instantiate language adapter: " + laEntry.getKey(), e);
				}
			}
		}
	}

	private void setupMods() {
		for (ModContainer mod : mods) {
			try {
				for (String in : mod.getInfo().getOldInitializers()) {
					String adapter = mod.getInfo().getOldStyleLanguageAdapter();
					entrypointStorage.addDeprecated(mod, adapter, in);
				}

				for (Map.Entry<String, Collection<AdapterLoadableClassEntry>> entry : mod.getInternalMeta().getEntrypoints().entrySet()) {
					for (AdapterLoadableClassEntry e : entry.getValue()) {
						entrypointStorage.add(mod, entry.getKey(), e, adapterMap);
					}
				}
			} catch (Exception e) {
				throw new RuntimeException(String.format("Failed to setup mod %s (%s)", mod.getInfo().getName(), mod.getOriginUrl().getFile()), e);
			}
		}
	}

	public void loadAccessWideners() {
		AccessWidenerReader accessWidenerReader = new AccessWidenerReader(accessWidener);
		for (ModContainer mod : mods) {
			for (String accessWidener : mod.getInternalMeta().accessWideners()) {

				Path path = mod.getPath(accessWidener);

				try (BufferedReader reader = Files.newBufferedReader(path)) {
					accessWidenerReader.read(reader, getMappingResolver().getCurrentRuntimeNamespace());
				} catch (Exception e) {
					throw new RuntimeException("Failed to read accessWidener file from mod " + mod.getInternalMeta().id(), e);
				}
			}
		}
	}

	public void prepareModInit(Path newRunDir, Object gameInstance) {
		if (!frozen) {
			throw new RuntimeException("Cannot instantiate mods when not frozen!");
		}

		if (gameInstance != null && FabricLauncherBase.getLauncher() instanceof Knot) {
			ClassLoader gameClassLoader = gameInstance.getClass().getClassLoader();
			ClassLoader targetClassLoader = FabricLauncherBase.getLauncher().getTargetClassLoader();
			boolean matchesKnot = (gameClassLoader == targetClassLoader);
			boolean containsKnot = false;

			if (matchesKnot) {
				containsKnot = true;
			} else {
				gameClassLoader = gameClassLoader.getParent();
				while (gameClassLoader != null && gameClassLoader.getParent() != gameClassLoader) {
					if (gameClassLoader == targetClassLoader) {
						containsKnot = true;
					}
					gameClassLoader = gameClassLoader.getParent();
				}
			}

			if (!matchesKnot) {
				if (containsKnot) {
					getLogger().info("Environment: Target class loader is parent of game class loader.");
				} else {
					getLogger().warn("\n\n* CLASS LOADER MISMATCH! THIS IS VERY BAD AND WILL PROBABLY CAUSE WEIRD ISSUES! *\n"
						+ " - Expected game class loader: " + FabricLauncherBase.getLauncher().getTargetClassLoader() + "\n"
						+ " - Actual game class loader: " + gameClassLoader + "\n"
						+ "Could not find the expected class loader in game class loader parents!\n");
				}
			}
		}

		this.gameInstance = gameInstance;

		if (gameDir != null) {
			try {
				if (!gameDir.toRealPath().equals(newRunDir.toRealPath())) {
					getLogger().warn("Inconsistent game execution directories: engine says " + newRunDir.toRealPath() + ", while initializer says " + gameDir.toRealPath() + "...");
					setGameDir(newRunDir);
				}
			} catch (IOException e) {
				getLogger().warn("Exception while checking game execution directory consistency!", e);
			}
		} else {
			setGameDir(newRunDir);
		}
	}

	public AccessWidener getAccessWidener() {
		return accessWidener;
	}

	public Logger getLogger() {
		return LOGGER;
	}

	/**
	 * Sets the game instance. This is only used in 20w22a+ by the dedicated server and should not be called by anything else.
	 */
	@Deprecated
	public void setGameInstance(Object gameInstance) {
		if (this.getEnvironmentType() != EnvType.SERVER) {
			throw new UnsupportedOperationException("Cannot set game instance on a client!");
		}

		if (this.gameInstance != null) {
			throw new UnsupportedOperationException("Cannot overwrite current game instance!");
		}

		this.gameInstance = gameInstance;
	}

	public String[] getLaunchArguments(boolean sanitize) {
		return getGameProvider().getLaunchArguments(sanitize);
	}
}
