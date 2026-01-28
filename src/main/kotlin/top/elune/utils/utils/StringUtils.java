package top.elune.utils.utils;

import java.util.Objects;

public class StringUtils {
    /**
     * 求取两个字符串间，最短编辑距离，算法：Levenshtein
     *
     * @param word1 字符串1
     * @param word2 字符串2
     * @return 最短编辑距离
     */
    public static int minDistance(String word1, String word2) {
        int m = word1.length();
        int n = word2.length();
        int[][] memo = new int[m + 1][n + 1];
        memo[0][0] = 0;
        //要删除的数量
        for (int i = 1; i <= m; i++) {
            memo[i][0] = i;
        }
        //要添加的数量
        for (int i = 1; i <= n; i++) {
            memo[0][i] = i;
        }
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                    memo[i][j] = memo[i - 1][j - 1];
                } else {
                    //取替换，删除，添加中的最小值
                    memo[i][j] = Math.min(memo[i - 1][j - 1], Math.min(memo[i - 1][j], memo[i][j - 1])) + 1;
                }
            }
        }
        return memo[m][n];
    }

    /**
     * 求取两个字符串间的相似度
     *
     * @param str1 字符串1
     * @param str2 字符串2
     * @return 相似度，0 - 100
     */
    public static int similarRates(String str1, String str2) {
        if (Objects.equals(str1, str2)) {
            return 100;
        }
        if (null == str1 || null == str2) {
            return 0;
        }
        str1 = str1.trim();
        str2 = str2.trim();
        return (int) ((1 - (double) minDistance(str1, str2) / Math.max(str1.length(), str2.length())) * 100);
    }

}
