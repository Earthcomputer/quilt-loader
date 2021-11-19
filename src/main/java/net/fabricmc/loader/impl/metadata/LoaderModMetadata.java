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

package net.fabricmc.loader.impl.metadata;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.metadata.ModMetadata;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import net.fabricmc.loader.impl.metadata.qmj.ConvertibleModMetadata;
import net.fabricmc.loader.impl.metadata.qmj.FabricModMetadataWrapper;
import net.fabricmc.loader.impl.metadata.qmj.InternalModMetadata;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;

/**
 * Internal variant of the ModMetadata interface.
 */
@SuppressWarnings("deprecation")
public interface LoaderModMetadata extends ModMetadata, ConvertibleModMetadata {
	int getSchemaVersion();

	default String getOldStyleLanguageAdapter() {
		return "net.fabricmc.loader.impl.language.JavaLanguageAdapter";
	}

	Map<String, String> getLanguageAdapterDefinitions();
	Collection<NestedJarEntry> getJars();
	Collection<String> getMixinConfigs(EnvType type);
	@Nullable
	String getAccessWidener();
	@Override
	boolean loadsInEnvironment(EnvType type);

	Collection<String> getOldInitializers();
	@Override
	List<EntrypointMetadata> getEntrypoints(String type);
	@Override
	Collection<String> getEntrypointKeys();

	void emitFormatWarnings();

	void setVersion(Version version);

	@Override
	default LoaderModMetadata asFabricModMetadata() {
		return this;
	}

	@Override
	default InternalModMetadata asFabricModMetadata() {
		return new FabricModMetadataWrapper(this);
	}
}
