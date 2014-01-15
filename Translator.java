package opennlp.tools.apps.translation;

/**
 * Abstract adapter class for some machine translation service.
 * @author Alex Loptev
 */
public abstract class Translator {
		
	/**
	 * Execute simple sentence translation to English
	 * with sentence native language auto detection.
	 * @param text
	 * @return translatedText
	 * @throws Exception 
	 */
	public abstract String translate(String text) throws Exception;
	
	/**
	 * Execute simple sentence translation to English
	 * with sentence native language auto detection.
	 * @param text
	 * @param	fromLanguage	sentence native language
	 * @param	fromLanguage	sentence destination language
	 * @return translatedText
	 */
	public abstract String translate(String text, String fromLanguage, String toLanguage) throws Exception;
	
	/**
	 * Returns translation engine name.
	 * @return engine name
	 */
	public abstract String getEngineName();
	
	
}
