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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;

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
		CLICK_ONCE,
		/** Sends the status message to the main application, remains enabled. */
		CLICK_MANY;
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

	public final String title;
	public final String mainText;
	public final List<FabricStatusTab> tabs = new ArrayList<>();
	public final List<FabricStatusButton> buttons = new ArrayList<>();

	public FabricStatusTree(String title, String mainText) {
		Objects.requireNonNull(title, "null title");
		Objects.requireNonNull(mainText, "null mainText");

		this.title = title;
		this.mainText = mainText;
	}

	public FabricStatusTree(DataInputStream is) throws IOException {
		title = is.readUTF();
		mainText = is.readUTF();

		for (int i = is.readInt(); i > 0; i--) {
			tabs.add(new FabricStatusTab(is));
		}

		for (int i = is.readInt(); i > 0; i--) {
			buttons.add(new FabricStatusButton(is));
		}
	}

	public void writeTo(DataOutputStream os) throws IOException {
		os.writeUTF(title);
		os.writeUTF(mainText);
		os.writeInt(tabs.size());

		for (FabricStatusTab tab : tabs) {
			tab.writeTo(os);
		}

		os.writeInt(buttons.size());

		for (FabricStatusButton button : buttons) {
			button.writeTo(os);
		}
	}

	public FabricStatusTree() {}


	public FabricStatusTab addTab(String name) {
		FabricStatusTab tab = new FabricStatusTab(name);
		tabs.add(tab);
		return tab;
	}

	public FabricStatusButton addButton(String text, FabricBasicButtonType type) {
		FabricStatusButton button = new FabricStatusButton(text, type);
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
		public final FabricBasicButtonType type;
		public String clipboard;
		public boolean shouldClose, shouldContinue;

		public FabricStatusButton(String text, FabricBasicButtonType type) {
			Objects.requireNonNull(text, "null text");

			this.text = text;
			this.type = type;
		}

		public FabricStatusButton(DataInputStream is) throws IOException {
			text = is.readUTF();
			type = FabricBasicButtonType.valueOf(is.readUTF());
			shouldClose = is.readBoolean();
			shouldContinue = is.readBoolean();

			if (is.readBoolean()) clipboard = is.readUTF();
		}

		public void writeTo(DataOutputStream os) throws IOException {
			os.writeUTF(text);
			os.writeUTF(type.name());
			os.writeBoolean(shouldClose);
			os.writeBoolean(shouldContinue);

			if (clipboard != null) {
				os.writeBoolean(true);
				os.writeUTF(clipboard);
			} else {
				os.writeBoolean(false);
			}
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

		public FabricStatusButton withClipboard(String clipboard) {
			this.clipboard = clipboard;
			return this;
		}
	}

	public static final class FabricStatusTab {
		public final FabricStatusNode node;

		/** The minimum warning level to display for this tab. */
		public FabricTreeWarningLevel filterLevel = FabricTreeWarningLevel.NONE;

		public FabricStatusTab(String name) {
			this.node = new FabricStatusNode(null, name);
		}

		public FabricStatusTab(DataInputStream is) throws IOException {
			node = new FabricStatusNode(null, is);
			filterLevel = FabricTreeWarningLevel.valueOf(is.readUTF());
		}

		public void writeTo(DataOutputStream os) throws IOException {
			node.writeTo(os);
			os.writeUTF(filterLevel.name());
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
		/** Extra text for more information. Lines should be separated by "\n". */
		public String details;
		public final List<FabricStatusNode> children = new ArrayList<>();

		private FabricStatusNode(FabricStatusNode parent, String name) {
			Objects.requireNonNull(name, "null name");

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
				expandByDefault |= level.isAtLeast(FabricTreeWarningLevel.WARN);
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
			return addException(this, Collections.newSetFromMap(new IdentityHashMap<>()), exception, UnaryOperator.identity(), new StackTraceElement[0]);
		}

		public FabricStatusNode addCleanedException(Throwable exception) {
			return addException(this, Collections.newSetFromMap(new IdentityHashMap<>()), exception, e -> {
				// Remove some self-repeating exception traces from the tree
				// (for example the RuntimeException that is is created unnecessarily by ForkJoinTask)
				Throwable cause;

				while ((cause = e.getCause()) != null) {
					if (e.getSuppressed().length > 0) {
						break;
					}

					String msg = e.getMessage();

					if (msg == null) {
						msg = e.getClass().getName();
					}

					if (!msg.equals(cause.getMessage()) && !msg.equals(cause.toString())) {
						break;
					}

					e = cause;
				}

				return e;
			}, new StackTraceElement[0]);
		}

		private static FabricStatusNode addException(FabricStatusNode node, Set<Throwable> seen, Throwable exception, UnaryOperator<Throwable> filter, StackTraceElement[] parentTrace) {
			if (!seen.add(exception)) {
				return node;
			}

			exception = filter.apply(exception);
			FabricStatusNode sub = node.addException(exception, parentTrace);
			StackTraceElement[] trace = exception.getStackTrace();

			for (Throwable t : exception.getSuppressed()) {
				FabricStatusNode suppressed = addException(sub, seen, t, filter, trace);
				suppressed.name += " (suppressed)";
				suppressed.expandByDefault = false;
			}

			if (exception.getCause() != null) {
				addException(sub, seen, exception.getCause(), filter, trace);
			}

			return sub;
		}

		private FabricStatusNode addException(Throwable exception, StackTraceElement[] parentTrace) {
			String msg = exception.getMessage();
			String[] lines = (msg == null || msg.isEmpty() ? exception.toString() : msg).split("\n");

			FabricStatusNode sub = new FabricStatusNode(this, lines[0]);
			children.add(sub);
			sub.setError();

			for (int i = 1; i < lines.length; i++) {
				sub.addChild(lines[i]);
			}

			StackTraceElement[] trace = exception.getStackTrace();
			int uniqueFrames = trace.length - 1;

			for (int i = parentTrace.length - 1; uniqueFrames >= 0 && i >= 0 && trace[uniqueFrames].equals(parentTrace[i]); i--) {
				uniqueFrames--;
			}

			StringJoiner frames = new StringJoiner("<br/>", "<html>", "</html>");
			int inheritedFrames = trace.length - 1 - uniqueFrames;

			for (int i = 0; i <= uniqueFrames; i++) {
				frames.add("at " + trace[i]);
			}

			if (inheritedFrames > 0) {
				frames.add("... " + inheritedFrames + " more");
			}

			sub.addChild(frames.toString()).iconType = ICON_TYPE_JAVA_CLASS;

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
