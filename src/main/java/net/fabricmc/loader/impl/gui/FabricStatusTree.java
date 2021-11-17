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

package net.fabricmc.loader.impl.gui;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;

import net.fabricmc.json5.JsonReader;
import net.fabricmc.json5.JsonToken;
import net.fabricmc.json5.JsonWriter;

public final class FabricStatusTree {
	public enum FabricTreeWarningLevel {
		ERROR,
		WARN,
		INFO,
		NONE;

		static final Map<String, FabricTreeWarningLevel> nameToValue = new HashMap<>();

		public final String lowerCaseName = name().toLowerCase(Locale.ROOT);

		static {
			for (FabricTreeWarningLevel level : values()) {
				nameToValue.put(level.lowerCaseName, level);
			}
		}

		public boolean isHigherThan(FabricTreeWarningLevel other) {
			return ordinal() < other.ordinal();
		}

		public boolean isAtLeast(FabricTreeWarningLevel other) {
			return ordinal() <= other.ordinal();
		}

		public static FabricTreeWarningLevel getHighest(FabricTreeWarningLevel a, FabricTreeWarningLevel b) {
			return a.isHigherThan(b) ? a : b;
		}

		/** @return The level to use, or null if the given char doesn't map to any level. */
		public static FabricTreeWarningLevel fromChar(char c) {
			switch (c) {
				case '-': return NONE;
				case '+': return INFO;
				case '!': return WARN;
				case 'x': return ERROR;
				default: return null;
			}
		}

		static FabricTreeWarningLevel read(JsonReader reader) throws IOException {
			String string = reader.nextString();
			if (string.isEmpty()) {
				return NONE;
			}
			FabricTreeWarningLevel level = nameToValue.get(string);
			if (level != null) {
				return level;
			} else {
				throw new IOException("Expected a valid FabricTreeWarningLevel, but got '" + string + "'");
			}
		}
	}

	public enum FabricBasicButtonType {
		/** Sends the status message to the main application, then disables itself. */
		CLICK_ONCE;
	}

	/** No icon is displayed. */
	public static final String ICON_TYPE_DEFAULT = "";

	/** Generic folder. */
	public static final String ICON_TYPE_FOLDER = "folder";

	/** Generic (unknown contents) file. */
	public static final String ICON_TYPE_UNKNOWN_FILE = "file";

	/** Generic non-Fabric jar file. */
	public static final String ICON_TYPE_JAR_FILE = "jar";

	/** Generic Fabric-related jar file. */
	public static final String ICON_TYPE_FABRIC_JAR_FILE = "jar+fabric";

	/** Generic Fabric-related jar file. */
	public static final String ICON_TYPE_QUILT_JAR_FILE = "jar+quilt";

	/** Something related to Fabric (It's not defined what exactly this is for, but it uses the main Fabric logo). */
	public static final String ICON_TYPE_FABRIC = "fabric";

	/** Something related to Fabric (It's not defined what exactly this is for, but it uses the main Fabric logo). */
	public static final String ICON_TYPE_QUILT = "quilt";

	/** Generic JSON file. */
	public static final String ICON_TYPE_JSON = "json";

	/** A file called "fabric.mod.json". */
	public static final String ICON_TYPE_FABRIC_JSON = "json+fabric";

	/** A file called "quilt.mod.json". */
	public static final String ICON_TYPE_QUILT_JSON = "json+quilt";

	/** Java bytecode class file. */
	public static final String ICON_TYPE_JAVA_CLASS = "java_class";

	/** A folder inside of a Java JAR. */
	public static final String ICON_TYPE_PACKAGE = "package";

	/** A folder that contains Java class files. */
	public static final String ICON_TYPE_JAVA_PACKAGE = "java_package";

	/** A tick symbol, used to indicate that something matched. */
	public static final String ICON_TYPE_TICK = "tick";

	/** A cross symbol, used to indicate that something didn't match (although it's not an error). Used as the opposite
	 * of {@link #ICON_TYPE_TICK} */
	public static final String ICON_TYPE_LESSER_CROSS = "lesser_cross";

	public final List<FabricStatusTab> tabs = new ArrayList<>();
	public final List<FabricStatusButton> buttons = new ArrayList<>();

	public String mainText = null;

	public FabricStatusTree() {}


	public FabricStatusTab addTab(String name) {
		FabricStatusTab tab = new FabricStatusTab(name);
		tabs.add(tab);
		return tab;
	}

	public FabricStatusButton addButton(String text) {
		FabricStatusButton button = new FabricStatusButton(text);
		buttons.add(button);
		return button;
	}

	public FabricStatusTree(JsonReader reader) throws IOException {
		reader.beginObject();
		// As we write ourselves we mandate the order
		// (This also makes everything a lot simpler)
		expectName(reader, "mainText");
		mainText = reader.nextString();

		expectName(reader, "tabs");
		reader.beginArray();
		while (reader.peek() != JsonToken.END_ARRAY) {
			tabs.add(new FabricStatusTab(reader));
		}
		reader.endArray();

		expectName(reader, "buttons");
		reader.beginArray();
		while (reader.peek() != JsonToken.END_ARRAY) {
			buttons.add(new FabricStatusButton(reader));
		}
		reader.endArray();
	
		reader.endObject();
	}

	/** Writes this tree out as a single json object. */
	public void write(JsonWriter writer) throws IOException {
		writer.beginObject();
		writer.name("mainText").value(mainText);
		writer.name("tabs").beginArray();
		for (FabricStatusTab tab : tabs) {
			tab.write(writer);
		}
		writer.endArray();
		writer.name("buttons").beginArray();
		for (FabricStatusButton button : buttons) {
			button.write(writer);
		}
		writer.endArray();
		writer.endObject();
	}

	static void expectName(JsonReader reader, String expected) throws IOException {
		String name = reader.nextName();
		if (!expected.equals(name)) {
			throw new IOException("Expected '" + expected + "', but read '" + name + "'");
		}
	}

	static String readStringOrNull(JsonReader reader) throws IOException {
		if (reader.peek() == JsonToken.STRING) {
			return reader.nextString();
		} else {
			reader.nextNull();
			return null;
		}
	}

	public static final class FabricStatusButton {
		public final String text;
		public boolean shouldClose, shouldContinue;

		public FabricStatusButton(String text) {
			this.text = text;
		}

		public FabricStatusButton makeClose() {
			shouldClose = true;
			return this;
		}

		public FabricStatusButton makeContinue() {
			this.shouldContinue = true;
			return this;
		}

		FabricStatusButton(JsonReader reader) throws IOException {
			reader.beginObject();
			expectName(reader, "text");
			text = reader.nextString();
			expectName(reader, "shouldClose");
			shouldClose = reader.nextBoolean();
			expectName(reader, "shouldContinue");
			shouldContinue = reader.nextBoolean();
			reader.endObject();
		}

		void write(JsonWriter writer) throws IOException {
			writer.beginObject();
			writer.name("text").value(text);
			writer.name("shouldClose").value(shouldClose);
			writer.name("shouldContinue").value(shouldContinue);
			writer.endObject();
		}
	}

	public static final class FabricStatusTab {
		public final FabricStatusNode node;

		/** The minimum warning level to display for this tab. */
		public FabricTreeWarningLevel filterLevel = FabricTreeWarningLevel.NONE;

		public FabricStatusTab(String name) {
			this.node = new FabricStatusNode(null, name);
		}

		public FabricStatusNode addChild(String name) {
			return node.addChild(name);
		}

		FabricStatusTab(JsonReader reader) throws IOException {
			reader.beginObject();
			expectName(reader, "level");
			filterLevel = FabricTreeWarningLevel.read(reader);
			expectName(reader, "node");
			node = new FabricStatusNode(null, reader);
			reader.endObject();
		}

		void write(JsonWriter writer) throws IOException {
			writer.beginObject();
			writer.name("level").value(filterLevel.lowerCaseName);
			writer.name("node");
			node.write(writer);
			writer.endObject();
		}
	}

	public static final class FabricStatusNode {
		private FabricStatusNode parent;

		public String name;

		/** The icon type. There can be a maximum of 2 decorations (added with "+" symbols), or 3 if the
		 * {@link #setWarningLevel(FabricTreeWarningLevel) warning level} is set to
		 * {@link FabricTreeWarningLevel#NONE } */
		public String iconType = ICON_TYPE_DEFAULT;

		private FabricTreeWarningLevel warningLevel = FabricTreeWarningLevel.NONE;

		public boolean expandByDefault = false;

		public final List<FabricStatusNode> children = new ArrayList<>();

		/** Extra text for more information. Lines should be separated by "\n". */
		public String details;

		private FabricStatusNode(FabricStatusNode parent, String name) {
			this.parent = parent;
			this.name = name;
		}

		private FabricStatusNode(FabricStatusNode parent, JsonReader reader) throws IOException {
			this.parent = parent;
			reader.beginObject();
			expectName(reader, "name");
			name = reader.nextString();
			expectName(reader, "icon");
			iconType = reader.nextString();
			expectName(reader, "level");
			warningLevel = FabricTreeWarningLevel.read(reader);
			expectName(reader, "expandByDefault");
			expandByDefault = reader.nextBoolean();
			expectName(reader, "details");
			details = readStringOrNull(reader);
			expectName(reader, "children");
			reader.beginArray();

			while (reader.peek() != JsonToken.END_ARRAY) {
				children.add(new FabricStatusNode(this, reader));
			}

			reader.endArray();
			reader.endObject();
		}

		void write(JsonWriter writer) throws IOException {
			writer.beginObject();
			writer.name("name").value(name);
			writer.name("icon").value(iconType);
			writer.name("level").value(warningLevel.lowerCaseName);
			writer.name("expandByDefault").value(expandByDefault);
			writer.name("details").value(details);
			writer.name("children").beginArray();

			for (FabricStatusNode node : children) {
				node.write(writer);
			}

			writer.endArray();
			writer.endObject();
		}

		public void moveTo(FabricStatusNode newParent) {
			parent.children.remove(this);
			this.parent = newParent;
			newParent.children.add(this);
		}

		public FabricTreeWarningLevel getMaximumWarningLevel() {
			return warningLevel;
		}

		public void setWarningLevel(FabricTreeWarningLevel level) {
			if (this.warningLevel == level || level == null) {
				return;
			}

			if (warningLevel.isHigherThan(level)) {
				// Just because I haven't written the back-fill revalidation for this
				throw new Error("Why would you set the warning level multiple times?");
			} else {
				if (parent != null && level.isHigherThan(parent.warningLevel)) {
					parent.setWarningLevel(level);
				}

				this.warningLevel = level;
			}
		}

		public void setError() {
			setWarningLevel(FabricTreeWarningLevel.ERROR);
		}

		public void setWarning() {
			setWarningLevel(FabricTreeWarningLevel.WARN);
		}

		public void setInfo() {
			setWarningLevel(FabricTreeWarningLevel.INFO);
		}

		public FabricStatusNode addChild(String string) {
			int indent = 0;
			FabricTreeWarningLevel level = null;

			while (string.startsWith("\t")) {
				indent++;
				string = string.substring(1);
			}

			string = string.trim();

			if (string.length() > 1) {
				if (Character.isWhitespace(string.charAt(1))) {
					level = FabricTreeWarningLevel.fromChar(string.charAt(0));

					if (level != null) {
						string = string.substring(2);
					}
				}
			}

			string = string.trim();
			String icon = "";

			if (string.length() > 3) {
				if ('$' == string.charAt(0)) {
					Pattern p = Pattern.compile("\\$([a-z.+-]+)\\$");
					Matcher match = p.matcher(string);
					if (match.find()) {
						icon = match.group(1);
						string = string.substring(icon.length() + 2);
					}
				}
			}

			string = string.trim();

			FabricStatusNode to = this;

			for (; indent > 0; indent--) {
				if (to.children.isEmpty()) {
					FabricStatusNode node = new FabricStatusNode(to, "");
					to.children.add(node);
					to = node;
				} else {
					to = to.children.get(to.children.size() - 1);
				}

				to.expandByDefault = true;
			}

			FabricStatusNode child = new FabricStatusNode(to, string);
			child.setWarningLevel(level);
			child.iconType = icon;
			to.children.add(child);
			return child;
		}

		public FabricStatusNode addException(Throwable exception) {
			FabricStatusNode sub = new FabricStatusNode(this, "...");
			children.add(sub);

			sub.setError();
			String msg = exception.getMessage();
			String[] lines = (msg == null ? exception.toString() : msg).split("\n");

			if (lines.length == 0) {
				sub.name = exception.toString();
			} else {
				sub.name = lines[0];

				for (int i = 1; i < lines.length; i++) {
					sub.addChild(lines[i]);
				}
			}

			StringWriter sw = new StringWriter();
			exception.printStackTrace(new PrintWriter(sw));
			sub.details = sw.toString();

			return sub;
		}

		/** If this node has one child then it merges the child node into this one. */
		public void mergeWithSingleChild(String join) {
			if (children.size() != 1) {
				return;
			}

			FabricStatusNode child = children.remove(0);
			name += join + child.name;

			for (FabricStatusNode cc : child.children) {
				cc.parent = this;
				children.add(cc);
			}

			child.children.clear();
		}

		public void mergeSingleChildFilePath(String folderType) {
			if (!iconType.equals(folderType)) {
				return;
			}

			while (children.size() == 1 && children.get(0).iconType.equals(folderType)) {
				mergeWithSingleChild("/");
			}

			children.sort((a, b) -> a.name.compareTo(b.name));
			mergeChildFilePaths(folderType);
		}

		public void mergeChildFilePaths(String folderType) {
			for (FabricStatusNode node : children) {
				node.mergeSingleChildFilePath(folderType);
			}
		}

		public FabricStatusNode getFileNode(String file, String folderType, String fileType) {
			FabricStatusNode fileNode = this;

			pathIteration: for (String s : file.split("/")) {
				if (s.isEmpty()) {
					continue;
				}

				for (FabricStatusNode c : fileNode.children) {
					if (c.name.equals(s)) {
						fileNode = c;
						continue pathIteration;
					}
				}

				if (fileNode.iconType.equals(FabricStatusTree.ICON_TYPE_DEFAULT)) {
					fileNode.iconType = folderType;
				}

				fileNode = fileNode.addChild(s);
			}

			fileNode.iconType = fileType;
			return fileNode;
		}
	}
}
