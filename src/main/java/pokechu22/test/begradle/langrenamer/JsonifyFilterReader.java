package pokechu22.test.begradle.langrenamer;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * A FilterReader that converts properties language files to JSON.
 */
public class JsonifyFilterReader extends FilterReader {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public JsonifyFilterReader(Reader in) throws IOException {
		super(jsonify(in));
	}

	private static StringReader jsonify(Reader in) throws IOException {
		Map<String, String> map = getTranslations(in);
		String json = GSON.toJson(map);
		return new StringReader(json);
	}

	private static Map<String, String> getTranslations(Reader in) throws IOException {
		Properties properties = new Properties();
		properties.load(in);
		return properties.entrySet().stream()
				.collect(Collectors.toMap(
						entry -> (String)entry.getKey(),
						entry -> (String)entry.getValue()));
	}
}
