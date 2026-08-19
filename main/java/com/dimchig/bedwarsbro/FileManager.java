package com.dimchig.bedwarsbro;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import net.minecraft.client.Minecraft;

public class FileManager {
	public FileManager() {
	}

	public static File getFile(String name) {
		File file = new File(name);
		if (!file.isAbsolute()) {
			Minecraft minecraft = Minecraft.getMinecraft();
			File dataDirectory = minecraft == null ? null : minecraft.mcDataDir;
			if (dataDirectory != null) file = new File(dataDirectory, name);
		}
		return file.getAbsoluteFile();
	}
	
	public static void initFile(String name) {
		try {
			File file = getFile(name);
			File parent = file.getParentFile();
			if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
				throw new IOException("Could not create directory: " + parent);
			}
			file.createNewFile();
	    } catch (IOException e) {
			e.printStackTrace();
	    }
	}
	
	public static void clearFile(String filename) {
		writeToFile("", filename, false);
	}
	
	public static void writeToFile(String str, String name, boolean append) {
		initFile(name);
		File file = getFile(name);
		try (Writer out = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(file, append), StandardCharsets.UTF_8))) {
			out.write((append && file.length() > 0 ? "\n" : "") + str);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static String readFile(String filename) {
		initFile(filename);
		try {
			List<String> list = Files.readAllLines(getFile(filename).toPath(), StandardCharsets.UTF_8);
			StringBuilder builder = new StringBuilder();
			for (String s: list) builder.append(s).append('\n');
			return builder.toString();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}
}
