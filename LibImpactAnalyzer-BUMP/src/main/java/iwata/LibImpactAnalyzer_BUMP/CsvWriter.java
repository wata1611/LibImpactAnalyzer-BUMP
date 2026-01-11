package iwata.LibImpactAnalyzer_BUMP;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * メトリクスデータをCSVファイルに出力するクラス
 */
public class CsvWriter {
    
    
    
    /**
     * メトリクスデータをCSVファイルに出力
     * @param metricsList メトリクスデータのリスト
     * @throws IOException ファイル書き込みエラー
     */
    public static void writeMetrics(List<FixMetrics> metricsList) throws IOException {
    	File csvFile;
        
        // CSV出力先パスが指定されている場合はそれを使用
        if (ApplicationConfig.CSV_OUTPUT_PATH != null && !ApplicationConfig.CSV_OUTPUT_PATH.isEmpty()) {
            // SHAディレクトリ配下に出力
            if (ApplicationConfig.SHA != null && !ApplicationConfig.SHA.isEmpty()) {
                // SHAディレクトリを作成
                String shaDir = ApplicationConfig.OUTPUT_DIR + "/" + ApplicationConfig.SHA;
                File shaDirFile = new File(shaDir);
                if (!shaDirFile.exists()) {
                    shaDirFile.mkdirs();
                }
                
                // CSVファイル名を取得（元のパスからファイル名のみ抽出）
                File originalCsvFile = new File(ApplicationConfig.CSV_OUTPUT_PATH);
                String csvFileName = originalCsvFile.getName();
                
                // SHAディレクトリ配下にCSVファイルを配置
                csvFile = new File(shaDirFile, csvFileName);
            } else {
                // SHAが取得できない場合は元のパスを使用
                csvFile = new File(ApplicationConfig.CSV_OUTPUT_PATH);
                
                // 親ディレクトリが存在しない場合は作成
                File parentDir = csvFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
            }
        } else {
            // 指定されていない場合はプロジェクトディレクトリ配下のmetricsフォルダに出力
            File dir = new File(ApplicationConfig.PROJECT_DIR + "/metrics");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            csvFile = new File(dir, "fix_metrics.csv");
        }
        
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
               "Removed Testcase," +
               "Tests Run," +
               "Failures," +
               "Errors," +
               "Skipped," +
               "Main Code Fix Time (seconds)," +
               "Test Code Fix Time (seconds)," +
               "Test Execution Time (seconds)," +
               "Total Execution Time (seconds)," +
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
               metrics.getRemovedTestMethods() + "," +
               metrics.getTestsRun() + "," +
               metrics.getFailures() + "," +
               metrics.getErrors() + "," +
               metrics.getSkipped() + "," +
               String.format("%.2f", metrics.getMainCodeFixTime()) + "," +
               String.format("%.2f", metrics.getTestCodeFixTime()) + "," +
               String.format("%.2f", metrics.getTestExecutionTime()) + "," +
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