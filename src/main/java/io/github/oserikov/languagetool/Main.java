package io.github.oserikov.languagetool;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.lang3.tuple.Pair;
import org.languagetool.JLanguageTool;
import org.languagetool.language.*;
import org.languagetool.rules.RuleMatch;

import java.io.*;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

import static io.github.oserikov.languagetool.DBUtils.*;

@Slf4j
public class Main {
    private static final String DEFAULT_OUTPUT_CSV_FILENAME = "features.csv";
    private static final String DEFAULT_INPUT_CSV_FILENAME = "corrections_dump.tsv";
    private static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/LT_TEST?serverTimezone=UTC";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASS = "password";
    private static final String DEFAULT_PATH_TO_NGRAMS = "C:\\Users\\olegs\\Documents\\ngram";
    //    private static final String DEFAULT_PATH_TO_WORD2VEC = "C:\\Users\\olegs\\Documents\\word2vec";
    private static final int DEFAULT_LOG_FREQUENCY = 100;
    private static final int DEFAULT_CONTEXT_LENGTH = 3;
    private static final int DEFAULT_STARTING_ROW_NUM = 1; // 1 based

    private static final String DEFAULT_QUERY =
            "SELECT sentence, correction, covered, replacement, suggestion_pos, rule_id, language " +
                    "FROM corrections " +
                    "WHERE " +
                    "rule_id LIKE \'MORFOLOGIK_RULE_%%\' OR " +
                    "rule_id LIKE \'%%GERMAN_SPELLER_RULE%%\' OR " +
                    "rule_id = \'HUNSPELL_NO_SUGGEST_RULE\' OR " +
                    "rule_id = \'HUNSPELL_RULE\' OR " +
                    "rule_id = \'AUSTRIAN_GERMAN_SPELLER_RULE\' OR " +
                    "rule_id = \'FR_SPELLING_RULE\' OR " +
                    "rule_id = \'GERMAN_SPELLER_RULE\' OR " +
                    "rule_id = \'SWISS_GERMAN_SPELLER_RULE\' AND " +
                    "sentence != correction AND " +
                    "covered != replacement";


    private static String outputCsvFileName;
    private static String inputCsvFileName;
    private static String dbUrl;
    private static String dbUser;
    private static String dbPass;
    private static String pathToNgrams;
    //    private static String pathToWord2Vec;
    private static Integer logFrequency;
    private static Integer contextLength;
    private static String query;
    private static Integer startingRowNum;


    private static final JLanguageTool defaultLT = new JLanguageTool(new AmericanEnglish());
    private static final String PROPERTIES_FILENAME = "features-extractor.properties";

    private static final Map<String, JLanguageTool> languages = new HashMap<String, JLanguageTool>(){
        {
            put("AUSTRIAN_GERMAN_SPELLER_RULE", new JLanguageTool(new AustrianGerman()));
            put("FR_SPELLING_RULE", new JLanguageTool(new French()));
            put("GERMAN_SPELLER_RULE", new JLanguageTool(new GermanyGerman()));
            put("MORFOLOGIK_RULE_AST", new JLanguageTool(new Asturian()));
            put("MORFOLOGIK_RULE_BE_BY", new JLanguageTool(new Belarusian()));
            put("MORFOLOGIK_RULE_BR_FR", new JLanguageTool(new Breton()));
            put("MORFOLOGIK_RULE_CA_ES", new JLanguageTool(new Catalan()));
            put("MORFOLOGIK_RULE_EL_GR", new JLanguageTool(new Greek()));
            put("MORFOLOGIK_RULE_EN_AU", new JLanguageTool(new AustralianEnglish()));
            put("MORFOLOGIK_RULE_EN_CA", new JLanguageTool(new CanadianEnglish()));
            put("MORFOLOGIK_RULE_EN_GB", new JLanguageTool(new BritishEnglish()));
            put("MORFOLOGIK_RULE_EN_NZ", new JLanguageTool(new NewZealandEnglish()));
            put("MORFOLOGIK_RULE_EN_US", new JLanguageTool(new AmericanEnglish()));
            put("MORFOLOGIK_RULE_EN_ZA", new JLanguageTool(new SouthAfricanEnglish()));
            put("MORFOLOGIK_RULE_ES", new JLanguageTool(new Spanish()));
            put("MORFOLOGIK_RULE_IT_IT", new JLanguageTool(new Italian()));
            put("MORFOLOGIK_RULE_NL_NL", new JLanguageTool(new Dutch()));
            put("MORFOLOGIK_RULE_PL_PL", new JLanguageTool(new Polish()));
            put("MORFOLOGIK_RULE_RO_RO", new JLanguageTool(new Romanian()));
            put("MORFOLOGIK_RULE_RU_RU", new JLanguageTool(new Russian()));
            put("MORFOLOGIK_RULE_SK_SK", new JLanguageTool(new Slovak()));
            put("MORFOLOGIK_RULE_SL_SI", new JLanguageTool(new Slovenian()));
            put("MORFOLOGIK_RULE_SR_EKAVIAN", new JLanguageTool(new JekavianSerbian()));
            put("MORFOLOGIK_RULE_TL", new JLanguageTool(new Tagalog()));
            put("MORFOLOGIK_RULE_UK_UA", new JLanguageTool(new Ukrainian()));
            put("SWISS_GERMAN_SPELLER_RULE", new JLanguageTool(new SwissGerman()));
        }
    };


    public static void main(String[] args) {
        log.info("Hello!");

        initConfig();
        initLanguagesMap();
        processDBData();

        log.info("Bye!");
    }

    private static void initLanguagesMap() {
        for (JLanguageTool lang : languages.values()){
            initSingleLT(lang);
        }
        initSingleLT(defaultLT);
    }

    private static void processDBData() {
        FileWriter csvOut;
        try {
            csvOut = new FileWriter(outputCsvFileName);
        } catch (IOException e) {
            log.error("Error! issue when creating csv file.", e);
            return;
        }

        Reader in;
        try {
            in = new FileReader(inputCsvFileName);
        } catch (IOException e) {
            log.error("Error! issue when opening csv file.", e);
            return;
        }

        log.debug(query);

        String[] HEADERS = {"sentence", "correction", "covered", "replacement", "suggestion_pos", "rule_id", "language"};

        try (
//             Connection conn = getConnection(dbUrl, dbUser, dbPass);
//             Statement stmt = getStatement(conn);
//             ResultSet rs = getRs(stmt, query);
                CSVPrinter printer = new CSVPrinter(csvOut, CSVFormat.DEFAULT.withQuoteMode(QuoteMode.NON_NUMERIC).withEscape('\\')))
        {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .withHeader(HEADERS)
                    .withDelimiter('\t')
                    .withEscape('\\')
                    .withRecordSeparator('\n')
                    .parse(in);

            int i = 0;
            int errorsCnt = 0;
            for (CSVRecord rs : records) {
                i++;
                if (i < startingRowNum) continue;
                if (i % logFrequency == 0) {
                    log.info("processed {} rows. ...", i);
                }

                String sentence = rs.get("sentence");
                String correction = rs.get("correction");
                String covered = rs.get("covered");
                String replacement = rs.get("replacement");
                Integer suggestion_pos = Integer.parseInt(rs.get("suggestion_pos"));
                String morfologik_rule_id = rs.get("rule_id");
                String language = rs.get("language");

//                System.out.print(sentence + "\t");
//                System.out.print(correction + "\t");
//                System.out.print(covered + "\t");
//                System.out.print(replacement + "\t");
//                System.out.print(suggestion_pos + "\t");
//                System.out.print(morfologik_rule_id + "\t");
//                System.out.print(language + "\n");

                try {
                    List<FeaturesRow> collectedDataFeaturesRows = processRow(sentence, correction, covered, replacement, suggestion_pos, morfologik_rule_id);

                    for (FeaturesRow featuresRow : collectedDataFeaturesRows) {
                        printer.printRecord(i,
                                featuresRow.getLeftContext(),
                                featuresRow.getRightContext(),
                                featuresRow.getCoveredString(),
                                featuresRow.getReplacementString(),
                                featuresRow.getReplacementPosition(),
                                featuresRow.getSelectedByUser(),
                                morfologik_rule_id,
                                language);
                    }
                }
                catch (Exception e) {
                    log.error("Error! {} {}. On row: {}, {}, {}, {}, {}, {}, {}", e.getClass(), e.getMessage(),
                            sentence, correction, covered, replacement, suggestion_pos, morfologik_rule_id, language);
                    errorsCnt += 1;
                }
            }

            printer.close();
            log.info("processed {} rows with {} errors. Done!", i, errorsCnt);
        }
        catch (Exception e) {
            log.error("Error!", e);
        }
    }

    private static void initSingleLT(JLanguageTool lt) {
        try {
            lt.activateLanguageModelRules(Paths.get(pathToNgrams).toFile());
            log.info("n-gram data loaded.");
        } catch (RuntimeException | IOException e) {
            log.error("Error! n-gram data is not loaded.", e);
        }
//        try {
//            lt.activateWord2VecModelRules(Paths.get(pathToWord2Vec).toFile());
//            log.info("word2vec data loaded.");
//        } catch (RuntimeException | IOException e) {
//            log.error("Error! word2vec data is not loaded.", e);
//        }
    }

    private static void initConfig() {
        String path = "./" + PROPERTIES_FILENAME;

        Properties mainProperties = new Properties();
        try (FileInputStream file = new FileInputStream(path)) {
            mainProperties.load(file);
        } catch (IOException e) {
            log.error("Error! can't load config '{}'.", path, e);
            return;
        }

        outputCsvFileName = mainProperties.getProperty("output_csv_filename", DEFAULT_OUTPUT_CSV_FILENAME);
        inputCsvFileName = mainProperties.getProperty("input_csv_filename", DEFAULT_INPUT_CSV_FILENAME);
        dbUrl = mainProperties.getProperty("mysql_connection_string", DEFAULT_DB_URL);
        dbUser = mainProperties.getProperty("mysql_user", DEFAULT_USER);
        dbPass = mainProperties.getProperty("mysql_password", DEFAULT_PASS);

        pathToNgrams = mainProperties.getProperty("ngrams_folder", DEFAULT_PATH_TO_NGRAMS);
//        pathToWord2Vec = mainProperties.getProperty("word2vec_folder", DEFAULT_PATH_TO_WORD2VEC);
        contextLength = DEFAULT_CONTEXT_LENGTH;

        if (mainProperties.stringPropertyNames().contains("log_frequency_in_number_of_rows")) {
            logFrequency = Integer.parseInt(mainProperties.getProperty("log_frequency_in_number_of_rows"));
        } else {
            logFrequency = DEFAULT_LOG_FREQUENCY;
        }

        if (mainProperties.stringPropertyNames().contains("sql_limit")) {
            Integer limit = Integer.parseInt(mainProperties.getProperty("sql_limit"));

            query = DEFAULT_QUERY + String.format(" LIMIT %d", limit);
        } else {
            query = DEFAULT_QUERY;
        }

        if (mainProperties.stringPropertyNames().contains("input_starting_line")){
            startingRowNum = Integer.parseInt(mainProperties.getProperty("input_starting_line"));
        } else {
            startingRowNum = DEFAULT_STARTING_ROW_NUM;
        }

        log.info("properties passed: {}", mainProperties.stringPropertyNames());
    }


    private static List<FeaturesRow> processRow(String sentence, String correction, String covered, String replacement,
                                                Integer suggestionPos, String morfologik_rule_id) throws SQLException, IOException {

        List<FeaturesRow> featuresRows = new ArrayList<>();

        Pair<String, String> context = Pair.of("", "");
        int errorStartIdx = -1;

        int sentencesDifferenceCharIdx = Utils.firstDifferencePosition(sentence, correction);
        if (sentencesDifferenceCharIdx != -1) {
            errorStartIdx = Utils.startOfErrorString(sentence, covered, sentencesDifferenceCharIdx);
            if (errorStartIdx != -1) {
                context = Utils.extractContext(sentence, covered, errorStartIdx, contextLength);
            }
        }

        FeaturesRow featuresRow = new FeaturesRow();
        featuresRow.setLeftContext(context.getKey());
        featuresRow.setRightContext(context.getValue());
        featuresRow.setCoveredString(covered);
        featuresRow.setReplacementString(replacement);
        featuresRow.setReplacementPosition(suggestionPos);
        featuresRow.setSelectedByUser(suggestionPos != 99);

        featuresRows.add(featuresRow);

        List<String> replacementsSuggestedByLT = new ArrayList<>();
        if (errorStartIdx != -1) {
            List<RuleMatch> matches = languages.getOrDefault(morfologik_rule_id, defaultLT).check(sentence);
            for (RuleMatch match : matches) {
                if (match.getFromPos() == errorStartIdx && match.getToPos() == errorStartIdx + covered.length()) {
                    replacementsSuggestedByLT.addAll(match.getSuggestedReplacements());
                }
            }
        }
        else {
            log.warn("Sentence not processed: {}", sentence);
        }
        for (int i = 0; i < replacementsSuggestedByLT.size(); i++) {
            String processingReplacement = replacementsSuggestedByLT.get(i);
            if (processingReplacement.equals(replacement)){
                if(featuresRow.getReplacementPosition() != 99){
                    featuresRow.setReplacementPosition(i);
                }
            }
            else {
                FeaturesRow processingFeaturesRow = new FeaturesRow();
                processingFeaturesRow.setLeftContext(featuresRow.getLeftContext());
                processingFeaturesRow.setRightContext(featuresRow.getRightContext());
                processingFeaturesRow.setCoveredString(featuresRow.getCoveredString());
                processingFeaturesRow.setReplacementString(processingReplacement);
                processingFeaturesRow.setReplacementPosition(i);
                processingFeaturesRow.setSelectedByUser(false);
                featuresRows.add(processingFeaturesRow);
            }
        }
        if (replacementsSuggestedByLT.size() == 0 && featuresRow.getReplacementPosition() != 99){
            featuresRow.setReplacementPosition(0);
        }

        return featuresRows;
    }

}

@Getter
@Setter
@ToString
class FeaturesRow {
    private String leftContext;
    private String rightContext;
    private String coveredString;
    private String replacementString;
    private Integer replacementPosition;
    private Boolean selectedByUser;
}
