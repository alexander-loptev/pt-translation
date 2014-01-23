package opennlp.tools.apps.translation;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;

import javax.net.ssl.HttpsURLConnection;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.memetix.mst.language.Language;

/**
 * Makes calls to the Google machine translation web service API
 */
public final class GoogleTranslator extends Translator {

	private static final String ENCODING = "UTF-8";
	private static final String PARAM_LANG_TO = "&tl=",
			PARAM_LANG_FROM = "&sl=", PARAM_TEXT = "&text=";
	private static final String SERVICE_URL = "https://translate.google.ru/translate_a/t?client=x";
	private static final String TRANSLATION_LABEL = "sentences";
	private static final String SENTENCE_TRANSLATION_LABEL = "trans";
	private static GoogleTranslator instance;
	
	private GoogleTranslator() {
	}

	public static GoogleTranslator getInstance() {
		if (instance == null) {
			instance = new GoogleTranslator();
		}
		return instance;
	}

	/**
	 * Translates text from a given Language to another given Language using
	 * Google.
	 * 
	 * @param text
	 *            The String to translate.
	 * @param from
	 *            The language code to translate from.
	 * @param to
	 *            The language code to translate to.
	 * @return The translated String.
	 * @throws Exception
	 *             on error.
	 */
	public static String execute(final String text, final Language from,
			final Language to) throws Exception {
		validateServiceState(text);
		String params = PARAM_LANG_TO
				+ URLEncoder.encode(to.toString(), ENCODING) + PARAM_TEXT
				+ URLEncoder.encode(text, ENCODING);
		if (from != Language.AUTO_DETECT) {
			params += PARAM_LANG_FROM
					+ URLEncoder.encode(from.toString(), ENCODING);
		}
		final URL url = new URL(SERVICE_URL + params);
		return retrievePropArrString(url, TRANSLATION_LABEL).trim();
	}

	private static void validateServiceState(final String text)
			throws Exception {
		final int byteLength = text.getBytes(ENCODING).length;
		if (byteLength > 10240) {
			throw new RuntimeException("TEXT_TOO_LARGE");
		}
	}

	/**
	 * Forms an HTTPS request, sends it using GET method and returns the result
	 * of the request as a String.
	 * 
	 * @param url
	 *            The URL to query for a String response.
	 * @return The translated String.
	 * @throws Exception
	 *             on error.
	 */
	private static String retrieveResponse(final URL url) throws Exception {
		final HttpsURLConnection uc = (HttpsURLConnection) url.openConnection();
		uc.setRequestProperty("Content-Type", "text/plain; charset=" + ENCODING);
		uc.addRequestProperty("User-Agent",
				"Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)");
		uc.setRequestProperty("Accept-Charset", ENCODING);
		uc.setRequestMethod("GET");

		try {
			final int responseCode = uc.getResponseCode();
			final String result = inputStreamToString(uc.getInputStream());
			if (responseCode != 200) {
				throw new Exception("Error from Google API: " + result);
			}
			return result;
		} finally {
			if (uc != null) {
				uc.disconnect();
			}
		}
	}

	/**
	 * Forms a request, sends it using the GET method and returns the value with
	 * the given label from the resulting JSON response.
	 */
	protected static String retrievePropString(final URL url,
			final String jsonValProperty) throws Exception {
		final String response = retrieveResponse(url);
		JSONObject jsonObj = (JSONObject) JSONValue.parse(response);
		return jsonObj.get(jsonValProperty).toString();
	}

	/**
	 * Forms a request, sends it using the GET method and returns the contents
	 * of the array of strings with the given label, with multiple strings
	 * concatenated.
	 */
	protected static String retrievePropArrString(final URL url,
			final String jsonValProperty) throws Exception {
		final String response = retrieveResponse(url);
		String[] translationArr = jsonObjValToStringArr(response,
				jsonValProperty);
		String combinedTranslations = "";
		for (String s : translationArr) {
			combinedTranslations += s;
		}
		return combinedTranslations.trim();
	}

	// Helper method to parse a JSONObject containing an array of Strings with
	// the given label.
	private static String[] jsonObjValToStringArr(final String inputString,
			final String subObjPropertyName) throws Exception {
		JSONObject jsonObj = (JSONObject) JSONValue.parse(inputString);
		JSONArray jsonArr = (JSONArray) jsonObj.get(subObjPropertyName);
		return jsonArrToStringArr(jsonArr.toJSONString(),
				SENTENCE_TRANSLATION_LABEL);
	}

	// Helper method to parse a JSONArray. Reads an array of JSONObjects and
	// returns a String Array
	// containing the toString() of the desired property. If propertyName is
	// null, just return the String value.
	private static String[] jsonArrToStringArr(final String inputString,
			final String propertyName) throws Exception {
		final JSONArray jsonArr = (JSONArray) JSONValue.parse(inputString);
		String[] values = new String[jsonArr.size()];

		int i = 0;
		for (Object obj : jsonArr) {
			if (propertyName != null && propertyName.length() != 0) {
				final JSONObject json = (JSONObject) obj;
				if (json.containsKey(propertyName)) {
					values[i] = json.get(propertyName).toString();
				}
			} else {
				values[i] = obj.toString();
			}
			i++;
		}
		return values;
	}

	/**
	 * Reads an InputStream and returns its contents as a String. Also effects
	 * rate control.
	 * 
	 * @param inputStream
	 *            The InputStream to read from.
	 * @return The contents of the InputStream as a String.
	 * @throws Exception
	 *             on error.
	 */
	private static String inputStreamToString(final InputStream inputStream)
			throws Exception {
		final StringBuilder outputBuilder = new StringBuilder();

		try {
			String string;
			if (inputStream != null) {
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(inputStream, ENCODING));
				while (null != (string = reader.readLine())) {
					// TODO Can we remove this?
					// Need to strip the Unicode Zero-width Non-breaking Space.
					// For some reason, the Microsoft AJAX
					// services prepend this to every response
					outputBuilder.append(string.replaceAll("\uFEFF", ""));
				}
			}
		} catch (Exception ex) {
			throw new Exception(
					"[google-translator-api] Error reading translation stream.",
					ex);
		}
		return outputBuilder.toString();
	}

	@Override
	public String translate(String text) throws Exception {
		return GoogleTranslator.execute(text, Language.AUTO_DETECT,
				Language.ENGLISH);
	}

	@Override
	public String translate(String text, String fromLanguage, String toLanguage)
			throws Exception {
		return GoogleTranslator.execute(text,
				Language.fromString(fromLanguage),
				Language.fromString(toLanguage));
	}

	@Override
	public String getEngineName() {
		return "Google";
	}
}
