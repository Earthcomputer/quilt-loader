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

package net.fabricmc.api;

import org.quiltmc.loader.api.ModContainer;

/**
 * A mod initializer.
 *
 * <p>In {@code fabric.mod.json}, the entrypoint is defined with {@code main} key.</p>
 *
 * @see ClientModInitializer
 * @see DedicatedServerModInitializer
 * @see net.fabricmc.loader.api.FabricLoader#getEntrypointContainers(String, Class)
 * @deprecated Please migrate to the quilt version: {@link  org.quiltmc.loader.api.minecraft.ModInitializer}.
 */
@Deprecated
@FunctionalInterface
public interface ModInitializer extends org.quiltmc.loader.api.minecraft.ModInitializer {
	/**
	 * Runs the mod initializer.
	 */
	void onInitialize();

	@Override
	default void onInitialize(ModContainer mod) {
		onInitialize();
	}
}
