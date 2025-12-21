package iwata.LibImpactAnalyzer_BUMP;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * メトリクスデータをCSVファイルに出力するクラス
 */
public class CsvWriter {
    
    /** CSV出力先ディレクトリ */
    private static final String OUTPUT_DIR = ApplicationConfig.PROJECT_DIR + File.separator + "metrics";
    
    /**
     * メトリクスデータをCSVファイルに出力
     * @param metricsList メトリクスデータのリスト
     * @throws IOException ファイル書き込みエラー
     */
    public static void writeMetrics(List<FixMetrics> metricsList) throws IOException {
        // 出力ディレクトリの作成
        File dir = new File(OUTPUT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // ファイル名にタイムスタンプを付加
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = dateFormat.format(new Date());
        String fileName = "fix_metrics_" + timestamp + ".csv";
        File csvFile = new File(dir, fileName);
        
        // CSV書き込み
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(csvFile), StandardCharsets.UTF_8))) {
            
            // ヘッダー行を出力
            writer.println(getHeader());
            
            // データ行を出力
            for (FixMetrics metrics : metricsList) {
                writer.println(toCSVLine(metrics));
            }
        }
        
        System.out.println("\nCSVファイルを出力しました: " + csvFile.getAbsolutePath());
    }
    
    /**
     * CSVヘッダー行を取得
     * @return ヘッダー行
     */
    private static String getHeader() {
        return "Iteration," +
               "Main Code Total Lines," +
               "Test Code Total Lines," +
               "Main Code Deleted Lines," +
               "Test Code Deleted Lines," +
               "Modified Main Files Count," +
               "Modified Test Files Count," +
               "Tests Run," +
               "Failures," +
               "Errors," +
               "Skipped," +
               "Execution Time (seconds)," +
               "Failed Test Cases," +
               "Modified Main Files," +
               "Modified Test Files";
    }
    
    /**
     * メトリクスデータをCSV行に変換
     * @param metrics メトリクスデータ
     * @return CSV行
     */
    private static String toCSVLine(FixMetrics metrics) {
        return metrics.getIteration() + "," +
               metrics.getMainCodeTotalLines() + "," +
               metrics.getTestCodeTotalLines() + "," +
               metrics.getMainCodeDeletedLines() + "," +
               metrics.getTestCodeDeletedLines() + "," +
               metrics.getMainCodeModifiedFiles() + "," +
               metrics.getTestCodeModifiedFiles() + "," +
               metrics.getTestsRun() + "," +
               metrics.getFailures() + "," +
               metrics.getErrors() + "," +
               metrics.getSkipped() + "," +
               String.format("%.2f", metrics.getExecutionTime()) + "," +
               escapeCSV(metrics.getFailedTestCasesAsString()) + "," +
               escapeCSV(metrics.getModifiedMainFilesAsString()) + "," +
               escapeCSV(metrics.getModifiedTestFilesAsString());
    }
    
    /**
     * CSV用のエスケープ処理
     * カンマや改行を含む場合はダブルクォートで囲む
     * @param value 値
     * @return エスケープ後の値
     */
    private static String escapeCSV(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        
        // ダブルクォートをエスケープ
        String escaped = value.replace("\"", "\"\"");
        
        // カンマ、改行、ダブルクォートを含む場合はダブルクォートで囲む
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        
        return escaped;
    }
}