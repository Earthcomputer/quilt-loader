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

package net.fabricmcold.loader.impl.quiltmc;

import net.fabricmcold.loader.api.ModContainer;
import net.fabricmcold.loader.api.entrypoint.EntrypointContainer;

public final class Quilt2FabricEntrypointContainer<T> implements EntrypointContainer<T> {

	private final org.quiltmc.loader.api.entrypoint.EntrypointContainer<T> quilt;

	public Quilt2FabricEntrypointContainer(org.quiltmc.loader.api.entrypoint.EntrypointContainer<T> quilt) {
		this.quilt = quilt;
	}

	@Override
	public T getEntrypoint() {
		return quilt.getEntrypoint();
	}

	@Override
	public ModContainer getProvider() {
		return new Quilt2FabricModContainer(quilt.getProvider());
	}
}
