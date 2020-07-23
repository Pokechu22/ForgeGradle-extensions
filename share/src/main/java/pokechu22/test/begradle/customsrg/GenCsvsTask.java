package pokechu22.test.begradle.customsrg;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import au.com.bytecode.opencsv.CSVParser;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import com.google.common.collect.Maps;
import com.google.common.io.Files;

public class GenCsvsTask extends DefaultTask {
	private Object inMethodsCSV;
	private Object outMethodsCSV;
	private Object inFieldsCSV;
	private Object outFieldsCSV;
	private Object inParamsCSV;
	private Object outParamsCSV;

	private ExtraSrgContainer extraSrgContainer;

	/**
	 * Gets the original method CSV.
	 * @return The original method CSV
	 */
	@InputFile
	public File getInMethodsCSV() {
		return getProject().file(inMethodsCSV);
	}
	/**
	 * Sets the original method CSV.
	 * @param inMethodsCSV The original method CSV
	 */
	public void setInMethodsCSV(Object inMethodsCSV) {
		this.inMethodsCSV = inMethodsCSV;
	}

	/**
	 * Gets the new method CSV.
	 * @return The new method CSV
	 */
	@OutputFile
	public File getOutMethodsCSV() {
		return getProject().file(outMethodsCSV);
	}
	/**
	 * Sets the new method CSV.
	 * @param outMethodsCSV The new method CSV
	 */
	public void setOutMethodsCSV(Object outMethodsCSV) {
		this.outMethodsCSV = outMethodsCSV;
	}

	/**
	 * Gets the original field CSV.
	 * @return The original field CSV
	 */
	@InputFile
	public File getInFieldsCSV() {
		return getProject().file(inFieldsCSV);
	}
	/**
	 * Sets the original field CSV.
	 * @param inFieldsCSV The original field CSV
	 */
	public void setInFieldsCSV(Object inFieldsCSV) {
		this.inFieldsCSV = inFieldsCSV;
	}

	/**
	 * Gets the new field CSV.
	 * @return The new field CSV
	 */
	@OutputFile
	public File getOutFieldsCSV() {
		return getProject().file(outFieldsCSV);
	}
	/**
	 * Sets the new field CSV.
	 * @param outFieldsCSV The new field CSV
	 */
	public void setOutFieldsCSV(Object outFieldsCSV) {
		this.outFieldsCSV = outFieldsCSV;
	}

	/**
	 * Gets the original parameter CSV.
	 * @return The original parameter CSV
	 */
	@InputFile
	public File getInParamsCSV() {
		return getProject().file(inParamsCSV);
	}
	/**
	 * Sets the original parameter CSV.
	 * @param inParamsCSV The original parameter CSV
	 */
	public void setInParamsCSV(Object inParamsCSV) {
		this.inParamsCSV = inParamsCSV;
	}

	/**
	 * Gets the new parameter CSV.
	 * @return The new parameter CSV
	 */
	@OutputFile
	public File getOutParamsCSV() {
		return getProject().file(outParamsCSV);
	}
	/**
	 * Sets the new parameter CSV.
	 * @param outParamsCSV The new parameter CSV
	 */
	public void setOutParamsCSV(Object outParamsCSV) {
		this.outParamsCSV = outParamsCSV;
	}

	/**
	 * Gets the extra method CSVs.
	 * @return The extra method CSVs.
	 */
	@InputFiles
	public List<File> getExtraMethods() {
		return extraSrgContainer.getMethods();
	}
	/**
	 * Gets the extra field CSVs.
	 * @return The extra field CSVs.
	 */
	@InputFiles
	public List<File> getExtraFields() {
		return extraSrgContainer.getFields();
	}
	/**
	 * Gets the extra parameter CSVs.
	 * @return The extra parameter CSVs.
	 */
	@InputFiles
	public List<File> getExtraParams() {
		return extraSrgContainer.getParams();
	}
	/**
	 * Collects data from the given extra SRG container.
	 *
	 * @param container The container.
	 */
	public void setExtraSrgContainer(ExtraSrgContainer container) {
		this.extraSrgContainer = container;
	}

	@TaskAction
	public void doTask() throws IOException {
		// Read the methods CSV, replacing things as needed:
		LinkedHashMap<String, String[]> methods = Maps.newLinkedHashMap();
		process(getInMethodsCSV(), methods);
		for (File file : getExtraMethods()) {
			process(file, methods);
		}

		// ... and then write it
		write(getOutMethodsCSV(), methods, false);

		// Same process for the fields:

		LinkedHashMap<String, String[]> fields = Maps.newLinkedHashMap();
		process(getInFieldsCSV(), fields);
		for (File file : getExtraFields()) {
			process(file, fields);
		}

		write(getOutFieldsCSV(), fields, false);

		// And for parameters:

		LinkedHashMap<String, String[]> params = Maps.newLinkedHashMap();
		process(getInParamsCSV(), params);
		for (File file : getExtraParams()) {
			process(file, params);
		}

		write(getOutParamsCSV(), params, true);
	}

	private CSVReader getCSVReader(File file) throws IOException {
		return new CSVReader(Files.newReader(file, Charset.defaultCharset()), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.NULL_CHARACTER, 1, false);
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
		try (CSVReader csvReader = getCSVReader(file)) {
			for (String[] data : csvReader.readAll()) {
				map.put(data[0], data);
			}
		}
	}

	/** The header used for MCP method and field CSVs. */
	private static final String[] HEADER = { "searge", "name", "side", "desc" };

	/** The header used for MCP parameter CSVs. Note that parameter descriptions are in the method. */
	private static final String[] PARAMS_HEADER = { "param", "name", "side" };

	/**
	 * Writes the processed CSV file.
	 *
	 * @param file
	 *            The file to write to.
	 * @param map
	 *            The map with the data. Only the values are used.
	 * @param params
	 *            True if this is a parameter CSV.
	 * @throws IOException when an IO error occurs
	 */
	private void write(File file, LinkedHashMap<String, String[]> map, boolean params) throws IOException {
		try (CSVWriter writer = new CSVWriter(Files.newWriter(file,
				Charset.defaultCharset()), CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.NO_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)) {
			writer.writeNext(params ? PARAMS_HEADER : HEADER);

			for (String[] line : map.values()) {
				writer.writeNext(line);
			}
		}
	}
}
