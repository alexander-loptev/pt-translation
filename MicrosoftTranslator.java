package opennlp.tools.apps.translation;

import com.memetix.mst.language.Language;
import com.memetix.mst.translate.Translate;

public class MicrosoftTranslator extends Translator {
	private static final String clientID = "ParseThicketsTranslation";
	private static final String clientSecret = "M4teDPWKv5xMTOZ/v6nJbwya4ilPE0cUCK4cCPGeRok=";
	private static MicrosoftTranslator instance;
	
	private MicrosoftTranslator() {}
	
	public static MicrosoftTranslator getInstance() {
		if (instance == null) {
			instance = new MicrosoftTranslator();
		}
		return instance;
	}

	static {
		Translate.setClientId(clientID);
	    Translate.setClientSecret(clientSecret);
	}
	
	@Override
	public String translate(String text) throws Exception {
		return translate(text, Language.AUTO_DETECT, Language.ENGLISH);
	}
	private String translate(String text, Language fromLanguage, Language toLanguage) throws Exception {
		return Translate.execute(text, fromLanguage, toLanguage);
	}
	
	@Override
	public String translate(String text, String fromLanguage, String toLanguage)  throws Exception {
		return Translate.execute(text, Language.fromString(fromLanguage), Language.fromString(toLanguage));
	}
	
	@Override
	public String getEngineName() {
		return "Microsoft";
	}

}
