package Botcode.AI.Personality;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;

import java.io.InputStream;

/**
 * Shared OpenNLP utility wrapper used by personality and interest systems.
 *
 * <p>Models are loaded once at class initialization so downstream calls can reuse the same
 * detector/tagger instances without repeated disk reads.
 */
public class NLPProcessor {

  private static SentenceDetectorME sentenceDetector;
  private static TokenizerME tokenizer;
  private static POSTaggerME posTagger;
  private static NameFinderME nameFinder;

  static {
    try {
      // Load model binaries from /resources/models and keep processors cached.
      // Sentence model
      InputStream sentenceModelStream =
          NLPProcessor.class.getResourceAsStream("/models/en-sent.bin");
      SentenceModel sentenceModel = new SentenceModel(sentenceModelStream);
      sentenceDetector = new SentenceDetectorME(sentenceModel);

      // Tokenizer model
      InputStream tokenModelStream = NLPProcessor.class.getResourceAsStream("/models/en-token.bin");
      TokenizerModel tokenizerModel = new TokenizerModel(tokenModelStream);
      tokenizer = new TokenizerME(tokenizerModel);

      // POS Tagger model
      InputStream posModelStream =
          NLPProcessor.class.getResourceAsStream("/models/en-pos-maxent.bin");
      POSModel posModel = new POSModel(posModelStream);
      posTagger = new POSTaggerME(posModel);

      // NER model (generic "person" model for now)
      InputStream nerModelStream =
          NLPProcessor.class.getResourceAsStream("/models/en-ner-person.bin");
      TokenNameFinderModel nerModel = new TokenNameFinderModel(nerModelStream);
      nameFinder = new NameFinderME(nerModel);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // Lightweight helpers used by detectors so callers stay implementation-agnostic.

  /**
   * Splits input text into sentences using the cached sentence model.
   */
  public static String[] detectSentences(String text) {
    return sentenceDetector.sentDetect(text);
  }

  /**
   * Tokenizes free text into word-level units.
   */
  public static String[] tokenize(String text) {
    return tokenizer.tokenize(text);
  }

  /**
   * Produces part-of-speech tags for each input token.
   */
  public static String[] posTags(String[] tokens) {
    return posTagger.tag(tokens);
  }

  /**
   * Extracts detected person-name spans and returns them as plain strings.
   */
  public static String[] findNames(String[] tokens) {
    var spans = nameFinder.find(tokens);
    String[] names = new String[spans.length];

    // Convert token index spans into user-readable names.
    for (int i = 0; i < spans.length; i++) {
      StringBuilder name = new StringBuilder();
      for (int j = spans[i].getStart(); j < spans[i].getEnd(); j++) {
        name.append(tokens[j]).append(" ");
      }
      names[i] = name.toString().trim();
    }

    return names;
  }
}


