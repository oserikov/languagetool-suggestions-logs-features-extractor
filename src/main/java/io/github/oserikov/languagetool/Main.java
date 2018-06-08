package io.github.oserikov.languagetool;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.lang3.tuple.Pair;
import org.languagetool.JLanguageTool;
import org.languagetool.language.*;
import org.languagetool.rules.RuleMatch;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

import static io.github.oserikov.languagetool.DBUtils.*;

@Slf4j
public class Main {
    private static final String DEFAULT_CSV_FILENAME = "features.csv";
    private static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/LT_TEST?serverTimezone=UTC";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASS = "password";
    private static final String DEFAULT_PATH_TO_NGRAMS = "C:\\Users\\olegs\\Documents\\ngram";
    private static final String DEFAULT_PATH_TO_WORD2VEC = "C:\\Users\\olegs\\Documents\\word2vec";
    private static final int DEFAULT_LOG_FREQUENCY = 100;
    private static final int DEFAULT_CONTEXT_LENGTH = 2;

    private static final String DEFAULT_QUERY =
            "SELECT sentence, correction, covered, replacement, suggestion_pos, rule_id, language " +
                    "FROM corrections " +
                    "WHERE " +
                    "rule_id LIKE \'MORFOLOGIK_RULE_%%\' AND " +
                    "sentence != correction AND " +
                    "covered != replacement";


    public static String csvFileName;
    private static String dbUrl;
    private static String dbUser;
    private static String dbPass;
    private static String pathToNgrams;
    private static String pathToWord2Vec;
    private static Integer logFrequency;
    private static Integer contextLength;
    private static String query;


    private static final JLanguageTool defaultLT = new JLanguageTool(new AmericanEnglish());
    private static final String PROPERTIES_FILENAME = "features-extractor.properties";

    private static final Map<String, JLanguageTool> languages = new HashMap<String, JLanguageTool>(){
        {
            put("MORFOLOGIK_RULE_AST", new JLanguageTool(new AustrianGerman()));
            put("MORFOLOGIK_RULE_BE_BY", new JLanguageTool(new Belarusian()));
            put("MORFOLOGIK_RULE_BR_FR", new JLanguageTool(new Breton()));
            put("MORFOLOGIK_RULE_CA_ES", new JLanguageTool(new Catalan()));
            put("MORFOLOGIK_RULE_EL_GR", new JLanguageTool(new Greek()));
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
            put("MORFOLOGIK_RULE_TL", new JLanguageTool(new Tagalog()));
            put("MORFOLOGIK_RULE_UK_UA", new JLanguageTool(new Ukrainian()));
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
            csvOut = new FileWriter(csvFileName);
        } catch (IOException e) {
            log.error("Error! issue when creating csv file.", e);
            return;
        }

        log.debug(query);

        try (Connection conn = getConnection(dbUrl, dbUser, dbPass);
             Statement stmt = getStatement(conn);
             ResultSet rs = getRs(stmt, query);
             CSVPrinter printer = new CSVPrinter(csvOut, CSVFormat.DEFAULT.withQuoteMode(QuoteMode.NON_NUMERIC).withEscape('\\')))
        {
            int i = 0;
            for (; rs.next(); i++) {
                if (i % logFrequency == 0) {
                    log.info("processed {} rows. ...", i);
                }

                String sentence = rs.getString("sentence");
                String correction = rs.getString("correction");
                String covered = rs.getString("covered");
                String replacement = rs.getString("replacement");
                Integer suggestion_pos = rs.getInt("suggestion_pos");
                String morfologik_rule_id = rs.getString("rule_id");
                String language = rs.getString("language");

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

            printer.close();
            log.info("processed {} rows. Done!", i);
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
        try {
            lt.activateWord2VecModelRules(Paths.get(pathToWord2Vec).toFile());
            log.info("word2vec data loaded.");
        } catch (RuntimeException | IOException e) {
            log.error("Error! word2vec data is not loaded.", e);
        }
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

        csvFileName = mainProperties.getProperty("output_csv_filename", DEFAULT_CSV_FILENAME);
        dbUrl = mainProperties.getProperty("mysql_connection_string", DEFAULT_DB_URL);
        dbUser = mainProperties.getProperty("mysql_user", DEFAULT_USER);
        dbPass = mainProperties.getProperty("mysql_password", DEFAULT_PASS);

        pathToNgrams = mainProperties.getProperty("ngrams_folder", DEFAULT_PATH_TO_NGRAMS);
        pathToWord2Vec = mainProperties.getProperty("word2vec_folder", DEFAULT_PATH_TO_WORD2VEC);
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
