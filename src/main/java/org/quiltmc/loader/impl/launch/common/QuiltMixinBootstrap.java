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

package org.quiltmc.loader.impl.launch.common;

import net.fabricmc.api.EnvType;
import net.fabricmc.mapping.tree.TinyTree;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.quiltmc.loader.impl.util.mappings.MixinIntermediaryDevRemapper;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class QuiltMixinBootstrap {
	private QuiltMixinBootstrap() {

	}

	private static boolean initialized = false;

	static void addConfiguration(String configuration) {
		Mixins.addConfiguration(configuration);
	}

	static Set<String> getMixinConfigs(QuiltLoaderImpl loader, EnvType type) {
		Set<String> set = new HashSet<>();
		for (org.quiltmc.loader.impl.ModContainer mod : loader.getMods()) {
			set.addAll(mod.getInternalMeta().mixins(type));
		}
		return set;
	}

	public static void init(EnvType side, QuiltLoaderImpl loader) {
		if (initialized) {
			throw new IllegalStateException("QuiltMixinBootstrap has already been initialized!");
		}

		if (QuiltLauncherBase.getLauncher().isDevelopment()) {
			MappingConfiguration mappingConfiguration = QuiltLauncherBase.getLauncher().getMappingConfiguration();
			TinyTree mappings = mappingConfiguration.getMappings();

			if (mappings != null) {
				List<String> namespaces = mappings.getMetadata().getNamespaces();
				if (namespaces.contains("intermediary") && namespaces.contains(mappingConfiguration.getTargetNamespace())) {
					System.setProperty("mixin.env.remapRefMap", "true");

					try {
						MixinIntermediaryDevRemapper remapper = new MixinIntermediaryDevRemapper(mappings, "intermediary", mappingConfiguration.getTargetNamespace());
						MixinEnvironment.getDefaultEnvironment().getRemappers().add(remapper);
						Log.info(LogCategory.MIXIN, "Loaded Quilt development mappings for mixin remapper!");
					} catch (Exception e) {
						Log.error(LogCategory.MIXIN, "Quilt development environment setup error - the game will probably crash soon!");
						e.printStackTrace();
					}
				}
			}
		}

		MixinBootstrap.init();
		getMixinConfigs(loader, side).forEach(QuiltMixinBootstrap::addConfiguration);
		initialized = true;
	}
}
