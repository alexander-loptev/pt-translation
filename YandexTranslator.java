package opennlp.tools.apps.translation;

import com.rmtheis.yandtran.detect.Detect;
import com.rmtheis.yandtran.language.Language;
import com.rmtheis.yandtran.translate.Translate;


public class YandexTranslator extends Translator {
	private static final String apiKey = "trnsl.1.1.20140112T165142Z.22f64ce172e144eb.fa27b570bcef47a222f1c5fdf6517672f1374176";
	private static YandexTranslator instance;
	
	private YandexTranslator() {}
	
	public static YandexTranslator getInstance() {
		if (instance == null) {
			instance = new YandexTranslator();
		}
		return instance;
	}

	static {
		Translate.setKey(apiKey);
	}
	
	@Override
	public String translate(String text) throws Exception {
		return translate(text, Detect.execute(text), Language.ENGLISH);
	}
	
	private String translate(String text, Language fromLanguage, Language toLanguage) throws Exception {
		return Translate.execute(text, fromLanguage, toLanguage);
	}
	
	@Override
	public String translate(String text, String fromLanguage, String toLanguage)  throws Exception {
		return Translate.execute(text, Language.fromString(fromLanguage), Language.fromString(toLanguage));
	}
	
	public static void main(String[] args) throws Exception {
		System.out.println(YandexTranslator.getInstance().translate("я ¬ас любил, любовь ещЄ быть может..."));
	}

	@Override
	public String getEngineName() {
		return "Yandex";
	}
	

}