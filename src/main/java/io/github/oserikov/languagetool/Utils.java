package io.github.oserikov.languagetool;

import javafx.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    public static String leftContext(String originalSentence, int errorStartIdx, String errorString, int contextLength) {
        String regex = repeat(contextLength, "\\w+\\W+") + errorString + "$";
        String stringToSearch = originalSentence.substring(0, errorStartIdx + errorString.length());

        return findFirstRegexMatch(regex, stringToSearch);
    }

    public static String rightContext(String originalSentence, int errorStartIdx, String errorString, int contextLength) {
        String regex = "^" + errorString + repeat(contextLength, "\\W+\\w+");
        String stringToSearch = originalSentence.substring(errorStartIdx);

        return findFirstRegexMatch(regex, stringToSearch);
    }

    public static int firstDifferencePosition(String sentence1, String sentence2) {
        int result = -1;

        for (int i = 0; i < sentence1.length(); i++){
            if (i >= sentence2.length() || sentence1.charAt(i) != sentence2.charAt(i)){
                result = i;
                break;
            }
        }

        return result;
    }

    public static int startOfErrorString(String sentence, String errorString, int sentencesDifferenceCharIdx) {
        int result = -1;

        List<Integer> possibleIntersections = allIndexesOf(sentence.charAt(sentencesDifferenceCharIdx), errorString);
        for (int i : possibleIntersections){
            if (sentencesDifferenceCharIdx - i < 0 || sentencesDifferenceCharIdx - i + errorString.length() > sentence.length()) continue;
            String possibleErrorString = sentence.substring(sentencesDifferenceCharIdx - i,
                    sentencesDifferenceCharIdx - i + errorString.length());

            if (possibleErrorString.equals(errorString)){
                result = sentencesDifferenceCharIdx - i;
                break;
            }
        }

        return result;
    }

    public static String getMaximalPossibleRightContext(String sentence, int errorStartIdx, String errorString,
                                                        int startingContextLength) {
        String rightContext = "";
        for (int contextLength = startingContextLength; contextLength > 0; contextLength--) {
            rightContext = rightContext(sentence, errorStartIdx, errorString, contextLength);
            if (!rightContext.isEmpty()) {
                break;
            }
        }
        return rightContext;
    }

    public static String getMaximalPossibleLeftContext(String sentence, int errorStartIdx, String errorString,
                                                       int startingContextLength) {
        String leftContext = "";
        for (int contextLength = startingContextLength; contextLength > 0; contextLength--) {
            leftContext = leftContext(sentence, errorStartIdx, errorString, contextLength);
            if (!leftContext.isEmpty()) {
                break;
            }
        }
        return leftContext;
    }

    public static Pair<String, String> extractContext(String sentence, String covered, int errorStartIdx, int contextLength) {
        int errorEndIdx = errorStartIdx + covered.length();
        String errorString = sentence.substring(errorStartIdx, errorEndIdx);

        String leftContext = getMaximalPossibleLeftContext(sentence, errorStartIdx, errorString, contextLength);
        String rightContext = getMaximalPossibleRightContext(sentence, errorStartIdx, errorString, contextLength);

        return new Pair<>(leftContext, rightContext);
    }


    private static String findFirstRegexMatch(String regex, String stringToSearch){
        String result = "";

        Pattern pattern = Pattern.compile(regex);
        Matcher stringToSearchMatcher = pattern.matcher(stringToSearch);

        if (stringToSearchMatcher.find()){
            result = stringToSearch.substring(stringToSearchMatcher.start(), stringToSearchMatcher.end());
        }

        return result;
    }

    private static String repeat(int count, String with) {
        return new String(new char[count]).replace("\0", with);
    }

    private static List<Integer> allIndexesOf(char character, String string){
        List<Integer> indexes = new ArrayList<>();
        for (int index = string.indexOf(character); index >= 0; index = string.indexOf(character, index + 1)){
            indexes.add(index);
        }
        return indexes;
    }


}
