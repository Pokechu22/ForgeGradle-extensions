package pokechu22.test.begradle.customsrg;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.delayed.DelayedFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import com.google.common.collect.Maps;
import com.google.common.io.Files;

public class GenCsvsTask extends DefaultTask {
	@InputFile
	private DelayedFile inMethodsCSV;
	@OutputFile
	private DelayedFile outMethodsCSV;
	@InputFile
	private DelayedFile inFieldsCSV;
	@OutputFile
	private DelayedFile outFieldsCSV;

	@InputFiles
	private List<File> extraMethods;
	@InputFiles
	private List<File> extraFields;

	/**
	 * Gets the original method CSV.
	 * @return The original method CSV
	 */
	public File getInMethodsCSV() {
		return inMethodsCSV.call();
	}
	/**
	 * Sets the original method CSV.
	 * @param inMethodsCSV The original method CSV
	 */
	public void setInMethodsCSV(DelayedFile inMethodsCSV) {
		this.inMethodsCSV = inMethodsCSV;
	}

	/**
	 * Gets the new method CSV.
	 * @return The new method CSV
	 */
	public File getOutMethodsCSV() {
		return outMethodsCSV.call();
	}
	/**
	 * Sets the new method CSV.
	 * @param outMethodsCSV The new method CSV
	 */
	public void setOutMethodsCSV(DelayedFile outMethodsCSV) {
		this.outMethodsCSV = outMethodsCSV;
	}

	/**
	 * Gets the original field CSV.
	 * @return The original field CSV
	 */
	public File getInFieldsCSV() {
		return inFieldsCSV.call();
	}
	/**
	 * Sets the original field CSV.
	 * @param inFieldsCSV The original field CSV
	 */
	public void setInFieldsCSV(DelayedFile inFieldsCSV) {
		this.inFieldsCSV = inFieldsCSV;
	}

	/**
	 * Gets the new field CSV.
	 * @return The new field CSV
	 */
	public File getOutFieldsCSV() {
		return outFieldsCSV.call();
	}
	/**
	 * Sets the new field CSV.
	 * @param outFieldsCSV The new field CSV
	 */
	public void setOutFieldsCSV(DelayedFile outFieldsCSV) {
		this.outFieldsCSV = outFieldsCSV;
	}

	/**
	 * Collects data from the given extra SRG container.
	 *
	 * @param container The container.
	 */
	public void setExtraSrgContainer(ExtraSrgContainer container) {
		this.extraMethods = container.getMethods();
		this.extraFields = container.getFields();
	}

	@TaskAction
	public void doTask() throws IOException {
		// Read the methods CSV, replacing things as needed:
		LinkedHashMap<String, String[]> methods = Maps.newLinkedHashMap();
		process(getInMethodsCSV(), methods);
		for (File file : extraMethods) {
			process(file, methods);
		}

		// ... and then write it
		write(getOutMethodsCSV(), methods);

		// Same process for the fields:

		LinkedHashMap<String, String[]> fields = Maps.newLinkedHashMap();
		process(getInFieldsCSV(), fields);
		for (File file : extraFields) {
			process(file, fields);
		}

		write(getOutFieldsCSV(), fields);
	}

	/**
	 * Processes the given CSV file, adding data from the given map.
	 *
	 * @param file
	 *            The file to read from.
	 * @param map
	 *            The map to update. Key is the first CSV field, values are ALL
	 *            of the CSV fields.
	 * @throws IOException when an IO error occurs
	 */
	private void process(File file, LinkedHashMap<String, String[]> map) throws IOException {
		try (CSVReader csvReader = Constants.getReader(file)) {
			for (String[] data : csvReader.readAll()) {
				map.put(data[0], data);
			}
		}
	}

	/** The header used for MCP method and field CSVs. */
	private static final String[] HEADER = { "searge", "name", "side", "desc" };

	/**
	 * Writes the processed CSV file.
	 *
	 * @param file
	 *            The file to write to.
	 * @param map
	 *            The map with the data. Only the values are used.
	 * @throws IOException when an IO error occurs
	 */
	private void write(File file, LinkedHashMap<String, String[]> map) throws IOException {
		try (CSVWriter writer = new CSVWriter(Files.newWriter(file,
				Charset.defaultCharset()), CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.NO_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)) {
			writer.writeNext(HEADER);

			for (String[] line : map.values()) {
				writer.writeNext(line);
			}
		}
	}
}
