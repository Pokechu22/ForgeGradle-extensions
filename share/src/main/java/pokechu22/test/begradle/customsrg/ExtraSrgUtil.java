package pokechu22.test.begradle.customsrg;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.base.Joiner;
import com.google.common.io.Files;

import au.com.bytecode.opencsv.CSVParser;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import net.minecraftforge.srg2source.rangeapplier.MethodData;
import net.minecraftforge.srg2source.rangeapplier.SrgContainer;

public class ExtraSrgUtil {

	/**
	 * Remaps one srg with the extra SRG. Loosely based off of the FG 2
	 * GenSrgs.readExtraSrgs, specifically the commented out portion.
	 *
	 * @param inSrg
	 *            The original SRG.
	 * @param extraSrg
	 *            The extra SRG. Only its {@link SrgContainer#classMap classMap} is
	 *            used.
	 * @return The remapped SRG
	 */
	protected static SrgContainer remapSrg(SrgContainer inSrg, SrgContainer extraSrg) {
		SrgContainer outSrg = new SrgContainer();

		// Remap classes
		for (Entry<String, String> e : inSrg.classMap.entrySet()) {
			String newClass = remapClass(e.getValue(), extraSrg.classMap);
			outSrg.classMap.put(e.getKey(), newClass);
		}

		// Remap methods
		for (Entry<MethodData, MethodData> e : inSrg.methodMap.entrySet()) {
			String newSig = remapMethodDescriptor(e.getValue().sig, extraSrg.classMap);
			String newName = remapQualifiedName(e.getValue().name, extraSrg.classMap);
			outSrg.methodMap.put(e.getKey(), new MethodData(newName, newSig));
		}

		// Remap fields
		for (Entry<String, String> e : inSrg.fieldMap.entrySet()) {
			String newName = remapQualifiedName(e.getValue(), extraSrg.classMap);
			outSrg.fieldMap.put(e.getKey(), newName);
		}

		// Don't do anything with packages
		outSrg.packageMap.putAll(inSrg.packageMap);

		return outSrg;
	}

	/**
	 * Remaps the contents of the EXC file, based on the SRG.
	 *
	 * @param inLines  The lines in the input EXC file.
	 * @param extraSrg The extra SRG. Only its {@link SrgContainer#classMap
	 *                 classMap} is used.
	 * @return The lines of the remapped file.
	 */
	public static List<String> remapExc(List<String> inLines, SrgContainer extraSrg) {
		List<String> outLines = new ArrayList<>();
		Joiner comma = Joiner.on(',');

		for (String line : inLines) {
			if (line.startsWith("#")) {
				outLines.add(line);
			} else {
				String[] pts = line.split("=");

				// Target method signature - e.g. net/minecraft/Foo.doThing(Ljava/lang/Object;)V
				String method = pts[0];
				// Contains Exception1,Exception2|param1,param2
				String value = pts[1];

				// Remap method class and signature
				int dotIndex = method.indexOf('.');
				int sigIndex = method.indexOf('(');
				if (dotIndex == -1 || sigIndex == -1) {
					// Not actually method -- e.g. max_constructor_index
					outLines.add(line);
					continue;
				}
				if (dotIndex > sigIndex) {
					throw new RuntimeException("Weird EXC line: '" + line + "'");
				}

				String oldClass = method.substring(0, dotIndex);
				String name = method.substring(dotIndex + 1, sigIndex);
				String oldSignature = method.substring(sigIndex);

				String newClass = remapClass(oldClass, extraSrg.classMap);
				String newSignature = remapMethodDescriptor(oldSignature, extraSrg.classMap);
				String newMethod = newClass + "." + name + newSignature;

				String newValue;
				if (!method.endsWith("Access")) {
					int lineIndex = value.indexOf('|');
					String exceptionsLine, remainder;
					if (lineIndex >= 0) {
						exceptionsLine = value.substring(0, lineIndex);
						remainder = value.substring(lineIndex + 1);
					} else {
						exceptionsLine = value;
						remainder = "";
					}

					String[] exceptions;
					if (!exceptionsLine.isEmpty()) {
						exceptions = exceptionsLine.split(",");
					} else {
						// "".split(",") produces a 1-element array containing the empty string
						exceptions = new String[0];
					}

					String[] newExceptions = new String[exceptions.length];
					for (int i = 0; i < exceptions.length; i++) {
						newExceptions[i] = remapClass(exceptions[i], extraSrg.classMap);
					}

					newValue = comma.join(newExceptions) + '|' + remainder;
				} else {
					newValue = value;
				}
				outLines.add(newMethod + '=' + newValue);
			}
		}
		return outLines;
	}

	/**
	 * Remaps object types in a JVM <a href=
	 * "https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3">
	 * method descriptor</a>. As per the documentation on <a href=
	 * "https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2">field
	 * descriptors</a>, this means we need to remap things in the form of:
	 * <code>L<u>fully/qualified/class/Name</u>;</code>, changing the underlined
	 * portion.
	 *
	 * <p>
	 * Based on the method originally removed in f35ae3952a735efc907da9afb584a9029e852b79.
	 *
	 * @param sig
	 *            The signature, e.g. <code>(ILcom/example/Obj;I)V</code>
	 * @param classMap
	 *            The class map to use to rename, e.g. <code>{com/example/Obj &rarr;
	 *            com/example/Thing}</code>
	 * @return The new signature, e.g. <code>(ILcom/example/Thing;I)V</code>
	 */
	protected static String remapMethodDescriptor(String sig, Map<String, String> classMap) {
		StringBuilder newSig = new StringBuilder(sig.length());

		int last = 0;
		int start = sig.indexOf('L');
		while (start != -1) {
			newSig.append(sig.substring(last, start));
			int next = sig.indexOf(';', start);
			newSig.append('L').append(remapClass(sig.substring(start + 1, next), classMap)).append(';');
			last = next + 1;
			start = sig.indexOf('L', next);
		}
		newSig.append(sig.substring(last));

		return newSig.toString();
	}

	/**
	 * Remaps a fully qualified member (i.e. method or field) name.
	 *
	 * @param qualifiedName
	 *            The fully qualified name, e.g. <code>com/example/Obj/doIt</code>
	 * @param classMap
	 *            The class map to use to rename, e.g. <code>{com/example/Obj &rarr;
	 *            com/example/Thing}</code>
	 * @return The new name, e.g. <code>com/example/Thing/doIt</code>
	 */
	protected static String remapQualifiedName(String qualifiedName, Map<String, String> classMap) {
		String cls = qualifiedName.substring(0, qualifiedName.lastIndexOf('/'));
		String name = qualifiedName.substring(cls.length() + 1);

		return remapClass(cls, classMap) + '/' + name;
	}

	/**
	 * Remaps a class name.
	 *
	 * <p>
	 * Based on the method originally removed in f35ae3952a735efc907da9afb584a9029e852b79.
	 *
	 * @param className
	 *            The original name of the class
	 * @param classMap
	 *            The class map to use to rename
	 * @return The new name if contained in the map, else the old name
	 */
	protected static String remapClass(String className, Map<String, String> classMap) {
		if (classMap.containsKey(className)) {
			return classMap.get(className);
		} else {
			return className;
		}
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
	public static void readCSVInto(File file, Map<String, String[]> map) throws IOException {
		readCSVInto(Files.newReader(file, Charset.defaultCharset()), map);
	}

	/**
	 * Processes the given CSV file, adding data from the given map.
	 *
	 * @param reader
	 *            The object to read from.
	 * @param map
	 *            The map to update. Key is the first CSV field, values are ALL
	 *            of the CSV fields.
	 * @throws IOException when an IO error occurs
	 */
	public static void readCSVInto(Reader reader, Map<String, String[]> map) throws IOException {
		try (CSVReader csvReader = new CSVReader(reader, CSVParser.DEFAULT_SEPARATOR,
				CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.NULL_CHARACTER, 1, false)) {
			for (String[] data : csvReader.readAll()) {
				map.put(data[0], data);
			}
		}
	}

	/**
	 * Writes the processed CSV file.
	 *
	 * @param file
	 *            The file to write to.
	 * @param map
	 *            The map with the data. Only the values are used.
	 * @param isParams
	 *            True if this is a parameter CSV.
	 * @throws IOException when an IO error occurs
	 */
	public static void writeCSV(File file, LinkedHashMap<String, String[]> map, boolean isParams) throws IOException {
		writeCSV(Files.newWriter(file, Charset.defaultCharset()), map, isParams);
	}

	/** The header used for MCP method and field CSVs. */
	private static final String[] HEADER = { "searge", "name", "side", "desc" };

	/** The header used for MCP parameter CSVs. Note that parameter descriptions are in the method. */
	private static final String[] PARAMS_HEADER = { "param", "name", "side" };

	/**
	 * Writes the processed CSV file.
	 *
	 * @param writer
	 *            The object to write to.
	 * @param map
	 *            The map with the data. Only the values are used.
	 * @param isParams
	 *            True if this is a parameter CSV.
	 * @throws IOException when an IO error occurs
	 */
	public static void writeCSV(Writer writer, LinkedHashMap<String, String[]> map, boolean isParams) throws IOException {
		try (CSVWriter csvWriter = new CSVWriter(writer, CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.NO_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)) {
			csvWriter.writeNext(isParams ? PARAMS_HEADER : HEADER);

			for (String[] line : map.values()) {
				csvWriter.writeNext(line);
			}
		}
	}
}
