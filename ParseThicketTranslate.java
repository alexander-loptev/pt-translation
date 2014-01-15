package opennlp.tools.apps.translation;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ProcessingInstruction;

import opennlp.tools.parse_thicket.ParseCorefsBuilder;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.apps.SnippetToParagraph;
import opennlp.tools.parse_thicket.matching.Matcher;
import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;

import com.memetix.mst.language.Language;
import com.memetix.mst.translate.Translate;

import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;

/**
 * Class for sentence translation with improvement based on parse thickets.
 * 
 * @author Alex Loptev
 * 
 */
public class ParseThicketTranslate {

	private static Logger LOG;
	private static ParseTreeChunkListScorer parseTreeChunkListScorer;
	private static Matcher matcher;
	private static BingQueryRunner searchRunner;
	private static SnippetToParagraph sentenceRetriever;
	private static int considerableSearchResultsCount = 5;

	// minimal and maximal bounds for phrase length for its meaningless testing
	private static int minimumWordsInPhraseForImprovement = 5;
	private static int maximumWordsInPhraseForImprovement = Integer.MAX_VALUE;
	private static double minimumRelativeWordsInPhraseForImprovement = 0.0;
	private static double maximumRelativeWordsInPhraseForImprovement = 1.0;

	// phrase is meaningless if its maximalSearchScore / selfScore <
	// meaninglessThresholdMultiplier
	private static double meaningfulnessRelativeSimilarityThreshold = 0.75;

	// all found results with relative similarity greater then this threshold
	// would be suggested like improvements
	private static double suggestionRelativeSimilarityThreshold = 0.1;

	private static List<String> getHitbaseSentencesForTesting(
			HitBase searchResult) throws IOException {
		if (searchResult.getUrl().endsWith(".pdf")) {
			throw new IOException(".pdf search results are not allowed");
		}
		searchResult = sentenceRetriever
				.formTextFromOriginalPageGivenSnippet(searchResult);
		List<String> sentences = searchResult.getOriginalSentences();

		String title = searchResult.getTitle().replace("<b>", " ")
				.replace("</b>", " ").replace("  ", " ").replace("  ", " ");
		String snippet = searchResult.getAbstractText().replace("<b>", " ")
				.replace("</b>", " ").replace("  ", " ").replace("  ", " ");
		sentences.add(title);
		sentences.add(snippet);
		return sentences;
	}

	/**
	 * If phrase is meaningless returns map of suggestions for improved
	 * translation and their relative similarity scores. If phrase is meaningful
	 * returns list with proof of phrase meaningfulness.
	 * 
	 * @param phrase
	 * @return suggestions for improved translation list
	 */
	public static Map<String, Double> suggestPhraseImprovedTranslations(
			String phrase) {
		Map<String, Double> suggestions = new HashMap<String, Double>();
		List<HitBase> quotedSearchResults = searchRunner.runSearch(
				String.format("\"%s\"", phrase), 1);

		if (quotedSearchResults.size() > 0) {
			suggestions.put(quotedSearchResults.get(0).getAbstractText(), 1.0);
			return suggestions;
		}

		double selfScore = assessSimilarityScore(phrase, phrase);
		double relativeScore = 0.0;
		List<HitBase> searchResults = searchRunner.runSearch(phrase,
				considerableSearchResultsCount);
		List<HitBase> considerableResults = searchResults.subList(0,
				Math.min(searchResults.size(), considerableSearchResultsCount));
		for (HitBase searchResult : considerableResults) {
			List<String> sentences;
			try {
				sentences = getHitbaseSentencesForTesting(searchResult);
				for (String sentence : sentences) {
					relativeScore = assessSimilarityScore(phrase, sentence)
							/ selfScore;
					if (relativeScore > meaningfulnessRelativeSimilarityThreshold) {
						suggestions.clear();
						suggestions.put(sentence, relativeScore);
						return suggestions;
					}
					if (relativeScore > suggestionRelativeSimilarityThreshold) {
						suggestions.put(sentence, relativeScore);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (NoClassDefFoundError e) {
				e.printStackTrace();
			}
		}
		return suggestions;
	}

	public static double assessSimilarityScore(String s1, String s2) {
		LOG.info(String.format("Assess similarity between: \"%s\" and \"%s\"",
				s1, s2));
		List<List<ParseTreeChunk>> match = matcher.assessRelevance(s1, s2);
		double sim = parseTreeChunkListScorer.getParseTreeChunkListScore(match);
		LOG.info(String.format("Score: %f", sim));
		return sim;
	}

	private static int wordCount(Tree t) {
		int wordCount = 0;
		for (TaggedWord leaf : t.taggedYield()) {
			// if is not punctuation
			if (Character.isLetter(leaf.tag().charAt(0))) {
				wordCount++;
			}
		}
		return wordCount;
	}

	/**
	 * Creates list of phrases (L_op) from translated sentence for
	 * meaningfulness testing. Such list includes all the phrases which contain
	 * at least two sub-phrases.
	 * 
	 * @param sentence
	 * @return list of phrases containing at least two sub-phrases
	 */
	private static List<Tree> formPhrasesForMeaningfulnessTesting(
			String sentence) {
		List<Tree> results = new LinkedList<Tree>();
		ParseCorefsBuilder ptBuilder = ParseCorefsBuilder.getInstance();
		ParseThicket pt = ptBuilder.buildParseThicket(sentence);
		Tree t = pt.getSentences().get(0);
		int sentenceWordCount = wordCount(t);
		int minimumWords = Math.max(
				(int) Math.ceil(sentenceWordCount
						* minimumRelativeWordsInPhraseForImprovement),
				minimumWordsInPhraseForImprovement);
		int maximumWords = Math.min(
				(int) Math.floor(sentenceWordCount
						* maximumRelativeWordsInPhraseForImprovement),
				maximumWordsInPhraseForImprovement);
		// tregex pattern for all nodes with at least two phrasal children
		// TregexPattern pattern = TregexPattern
		// .compile("__ < (__ [ !<: __ | < (__ < __) ] $ (__ !<: __ | < (__ < __)))");

		// tregex for any phrasal node
		TregexPattern pattern = TregexPattern
				.compile("__ !<: __");

		TregexMatcher matcher = pattern.matcher(t);
		while (matcher.findNextMatchingNode()) {
			Tree candidate = matcher.getMatch();
			int wordCount = wordCount(candidate);
			// test if phrase is too short or too long
			if (wordCount >= minimumWords && wordCount <= maximumWords) {
				results.add(candidate);
			}
		}
		// reversing phrases because the highest nodes in tree should
		// be tested for meaningfulness after the lowest nodes
		Collections.reverse(results);
		return results;
	}

	// getters/setters for adjustable parameters
	public static int getMinimumWordsInPhraseForImprovement() {
		return minimumWordsInPhraseForImprovement;
	}

	public static void setMinimumWordsInPhraseForImprovement(
			int minimumWordsInPhraseForImprovement) {
		ParseThicketTranslate.minimumWordsInPhraseForImprovement = minimumWordsInPhraseForImprovement;
	}

	public static int getMaximumWordsInPhraseForImprovement() {
		return maximumWordsInPhraseForImprovement;
	}

	public static void setMaximumWordsInPhraseForImprovement(
			int maximumWordsInPhraseForImprovement) {
		ParseThicketTranslate.maximumWordsInPhraseForImprovement = maximumWordsInPhraseForImprovement;
	}

	public static double getMinimumRelativeWordsInPhraseForImprovement() {
		return minimumRelativeWordsInPhraseForImprovement;
	}

	public static void setMinimumRelativeWordsInPhraseForImprovement(
			double minimumRelativeWordsInPhraseForImprovement) {
		ParseThicketTranslate.minimumRelativeWordsInPhraseForImprovement = minimumRelativeWordsInPhraseForImprovement;
	}

	public static double getMaximumRelativeWordsInPhraseForImprovement() {
		return maximumRelativeWordsInPhraseForImprovement;
	}

	public static void setMaximumRelativeWordsInPhraseForImprovement(
			double maximumRelativeWordsInPhraseForImprovement) {
		ParseThicketTranslate.maximumRelativeWordsInPhraseForImprovement = maximumRelativeWordsInPhraseForImprovement;
	}

	public static double getMeaninglessThresholdMultiplier() {
		return meaningfulnessRelativeSimilarityThreshold;
	}

	public static void setMeaninglessThresholdMultiplier(
			double meaninglessThresholdMultiplier) {
		ParseThicketTranslate.meaningfulnessRelativeSimilarityThreshold = meaninglessThresholdMultiplier;
	}

	public static double getSuggestionRelativeSimilarityThreshold() {
		return suggestionRelativeSimilarityThreshold;
	}

	public static void setSuggestionRelativeSimilarityThreshold(
			double suggestionRelativeSimilarityThreshold) {
		ParseThicketTranslate.suggestionRelativeSimilarityThreshold = suggestionRelativeSimilarityThreshold;
	}

	/**
	 * Static initialization block.
	 */
	static {
		searchRunner = new BingQueryRunner();
		sentenceRetriever = new SnippetToParagraph();
		matcher = new Matcher();
		parseTreeChunkListScorer = new ParseTreeChunkListScorer();
		LOG = Logger
				.getLogger("opennlp.tools.parse_thicket.translation.SentenceTranslate");
	}

	public static List<Translator> getAvailableTranslators() {
		return Arrays.asList(YandexTranslator.getInstance(),
				MicrosoftTranslator.getInstance());
	}

	/**
	 * Creates XML containing meaningfulness testing results for multi-sentence
	 * text.
	 * 
	 * @param text
	 *            Text for translation
	 * @param filename
	 *            filename for result XML
	 */
	public static void saveMeaningfulnessTestingResultsAsXML(String text,
			String filename) throws Exception {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory
				.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		Document doc = docBuilder.newDocument();
		Element textTranslationElement = doc.createElement("text-translation");
		Element textOriginalElement = doc.createElement("original-text");
		textOriginalElement.appendChild(doc.createTextNode(text));
		textTranslationElement.appendChild(textOriginalElement);
		doc.appendChild(textTranslationElement);
		ProcessingInstruction pi = doc.createProcessingInstruction(
				"xml-stylesheet", "type=\"text/xsl\" href=\"translation.xsl\"");
		doc.insertBefore(pi, textTranslationElement);

		for (Translator translator : getAvailableTranslators()) {

			String translatedText = translator.translate(text);
			ParseCorefsBuilder ptBuilder = ParseCorefsBuilder.getInstance();
			ParseThicket pt = ptBuilder.buildParseThicket(translatedText);

			Element translationElement = doc.createElement("translation");
			translationElement.setAttribute("engine",
					translator.getEngineName());
			textTranslationElement.appendChild(translationElement);
			Element translatedTextElement = doc
					.createElement("translated-text");
			translatedTextElement.appendChild(doc
					.createTextNode(translatedText));
			textTranslationElement.appendChild(translatedTextElement);

			for (Tree tree : pt.getSentences()) {
				String sentenceTranslation = Sentence
						.listToString(tree.yield());
				
				Element sentenceElement = doc.createElement("sentence");
				translationElement.appendChild(sentenceElement);

				Element translatedSentenceElement = doc
						.createElement("translated-sentence");
				translatedSentenceElement.appendChild(doc
						.createTextNode(sentenceTranslation));
				sentenceElement.appendChild(translatedSentenceElement);
				
				Element sentencePennElement = doc.createElement("sentence-penn-string");
				sentencePennElement.appendChild(doc.createTextNode(tree.pennString()));
				sentenceElement.appendChild(sentencePennElement);
				
				List<Tree> phrasesForTesting = formPhrasesForMeaningfulnessTesting(sentenceTranslation);
				for (Tree phraseTree : phrasesForTesting) {
					String phrase = Sentence.listToString(phraseTree.yield());
					Map<String, Double> suggestions = suggestPhraseImprovedTranslations(phrase);
					Element phraseElement = doc.createElement("phrase");
					sentenceElement.appendChild(phraseElement);
					double randomSuggestionScore = suggestions.values().iterator().next();
					phraseElement.setAttribute("meaningful", (randomSuggestionScore > meaningfulnessRelativeSimilarityThreshold) ? "1" : "0");
					Element translatedPhraseElement = doc
							.createElement("translated-phrase");
					translatedPhraseElement.appendChild(doc
							.createTextNode(phrase));
					phraseElement.appendChild(translatedPhraseElement);
					
					Element phrasePennElement = doc.createElement("phrase-penn-string");
					phrasePennElement.appendChild(doc.createTextNode(phraseTree.pennString()));
					sentenceElement.appendChild(phrasePennElement);
					
					for (Map.Entry<String, Double> improvement : suggestions
							.entrySet()) {
						Element suggestionElement = doc
								.createElement("suggestion");
						suggestionElement.appendChild(doc
								.createTextNode(improvement.getKey()));
						suggestionElement.setAttribute("relative-score",
								Double.toString(improvement.getValue()));
						phraseElement.appendChild(suggestionElement);
					}
				}
			}
		}
		TransformerFactory transformerFactory = TransformerFactory
				.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(new File(filename));
		transformer.transform(source, result);
	}

	/**
	 * Dummy method for testing purposes.
	 * 
	 * @param args
	 */
	public static void main(String[] args) throws Exception {

		String[] tests = { "Владельцы прав на музыку недовольны, что уже принятые и разрабатываемые в России антипиратские законы не предусматривают жесткой борьбы с музыкальным контрафактом.",
		"Немного познакомившись, мы с моим новым попутчиком опрокинули по стаканчику.",
		"Эти типы стали насмехаться над нашими глупыми попытками выйти из положения.",
		"Главный государственный санитарный врач России Геннадий Онищенко призвал россиян отказаться от суши.",
		"Последний по порядку, но никак не по важности курс — Алгоритмы и структуры данных для поиска.",
		};
		for (int i = 0; i < tests.length; i++) {
			ParseThicketTranslate.saveMeaningfulnessTestingResultsAsXML(
					tests[i], String.format("test%d.xml", i + 1));
		}

		// String[] tests = {
		// "He is French, lives near Calais, people essentially decent and honest, but special intelligence is no different.",
		// "All that he knew and asked questions only to talk conductor.",
		// "Typing a search engine offers tips - questions that begin with the characters the user.",
		// "We are upset with my friend for a drink.",
		// "I typically do not like the English, but I located the Colonel.",
		// };

	}
}
