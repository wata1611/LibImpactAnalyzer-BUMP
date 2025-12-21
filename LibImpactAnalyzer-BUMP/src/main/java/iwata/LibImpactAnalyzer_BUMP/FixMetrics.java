package iwata.LibImpactAnalyzer_BUMP;

import java.util.*;

/**
 * コンパイルエラー修正処理のメトリクスデータを保持するクラス
 * CSV出力用のデータを管理
 */
public class FixMetrics {
    /** 反復回数 */
    private int iteration;
    
    /** メインコードの総行数 */
    private int mainCodeTotalLines;
    
    /** テストコードの総行数 */
    private int testCodeTotalLines;
    
    /** メインコード削除行数 */
    private int mainCodeDeletedLines;
    
    /** テストコード削除行数 */
    private int testCodeDeletedLines;
    
    /** 修正を行ったメインコードファイル数 */
    private int mainCodeModifiedFiles;
    
    /** 修正を行ったテストコードファイル数 */
    private int testCodeModifiedFiles;
    
    /** Tests run（テスト実行数） */
    private int testsRun;
    
    /** Failures（失敗数） */
    private int failures;
    
    /** Errors（エラー数） */
    private int errors;
    
    /** Skipped（スキップ数） */
    private int skipped;
    
    /** 実行時間（秒） */
    private double executionTime;
    
    /** 失敗したテストケース名のリスト */
    private List<String> failedTestCases;
    
    /** 修正を行ったメインコードファイル名のリスト */
    private List<String> modifiedMainFiles;
    
    /** 修正を行ったテストコードファイル名のリスト */
    private List<String> modifiedTestFiles;
    
    /**
     * コンストラクタ
     */
    public FixMetrics() {
        this.failedTestCases = new ArrayList<>();
        this.modifiedMainFiles = new ArrayList<>();
        this.modifiedTestFiles = new ArrayList<>();
    }
    
    // ===== Getters and Setters =====
    
    public int getIteration() {
        return iteration;
    }
    
    public void setIteration(int iteration) {
        this.iteration = iteration;
    }
    
    public int getMainCodeTotalLines() {
        return mainCodeTotalLines;
    }
    
    public void setMainCodeTotalLines(int mainCodeTotalLines) {
        this.mainCodeTotalLines = mainCodeTotalLines;
    }
    
    public int getTestCodeTotalLines() {
        return testCodeTotalLines;
    }
    
    public void setTestCodeTotalLines(int testCodeTotalLines) {
        this.testCodeTotalLines = testCodeTotalLines;
    }
    
    public int getMainCodeDeletedLines() {
        return mainCodeDeletedLines;
    }
    
    public void setMainCodeDeletedLines(int mainCodeDeletedLines) {
        this.mainCodeDeletedLines = mainCodeDeletedLines;
    }
    
    public int getTestCodeDeletedLines() {
        return testCodeDeletedLines;
    }
    
    public void setTestCodeDeletedLines(int testCodeDeletedLines) {
        this.testCodeDeletedLines = testCodeDeletedLines;
    }
    
    public int getMainCodeModifiedFiles() {
        return mainCodeModifiedFiles;
    }
    
    public void setMainCodeModifiedFiles(int mainCodeModifiedFiles) {
        this.mainCodeModifiedFiles = mainCodeModifiedFiles;
    }
    
    public int getTestCodeModifiedFiles() {
        return testCodeModifiedFiles;
    }
    
    public void setTestCodeModifiedFiles(int testCodeModifiedFiles) {
        this.testCodeModifiedFiles = testCodeModifiedFiles;
    }
    
    public int getTestsRun() {
        return testsRun;
    }
    
    public void setTestsRun(int testsRun) {
        this.testsRun = testsRun;
    }
    
    public int getFailures() {
        return failures;
    }
    
    public void setFailures(int failures) {
        this.failures = failures;
    }
    
    public int getErrors() {
        return errors;
    }
    
    public void setErrors(int errors) {
        this.errors = errors;
    }
    
    public int getSkipped() {
        return skipped;
    }
    
    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }
    
    public double getExecutionTime() {
        return executionTime;
    }
    
    public void setExecutionTime(double executionTime) {
        this.executionTime = executionTime;
    }
    
    public List<String> getFailedTestCases() {
        return failedTestCases;
    }
    
    public void addFailedTestCase(String testCase) {
        this.failedTestCases.add(testCase);
    }
    
    public List<String> getModifiedMainFiles() {
        return modifiedMainFiles;
    }
    
    public void addModifiedMainFile(String fileName) {
        if (!this.modifiedMainFiles.contains(fileName)) {
            this.modifiedMainFiles.add(fileName);
        }
    }
    
    public List<String> getModifiedTestFiles() {
        return modifiedTestFiles;
    }
    
    public void addModifiedTestFile(String fileName) {
        if (!this.modifiedTestFiles.contains(fileName)) {
            this.modifiedTestFiles.add(fileName);
        }
    }
    
    /**
     * リストを改行区切りの文字列に変換
     */
    public String getFailedTestCasesAsString() {
        return String.join("\n", failedTestCases);
    }
    
    public String getModifiedMainFilesAsString() {
        return String.join("\n", modifiedMainFiles);
    }
    
    public String getModifiedTestFilesAsString() {
        return String.join("\n", modifiedTestFiles);
    }
}