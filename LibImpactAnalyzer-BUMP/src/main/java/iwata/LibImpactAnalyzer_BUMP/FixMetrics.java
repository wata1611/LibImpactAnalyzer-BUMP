package iwata.LibImpactAnalyzer_BUMP;

import java.util.*;

/**
 * コンパイルエラー修正処理のメトリクスデータを保持するクラス
 * CSV出力用のデータを管理
 */
public class FixMetrics {
    /** メインコードの反復回数 */
    private int mainCodeIteration;
    
    /** テストコードの反復回数 */
    private int testCodeIteration;
    
    /** 総反復回数（メイン + テスト） */
    private int totalIteration;
    
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
    
    /** 実行時間（秒） - トータル */
    private double executionTime;
    
    /** メインコード修正時間（秒） */
    private double mainCodeFixTime;
    
    /** テストコード修正時間（秒） */
    private double testCodeFixTime;
    
    /** テスト実行時間（秒） */
    private double testExecutionTime;
    
    /** 削除されたテストメソッド数 */
    private int removedTestMethods;
    
    /** 失敗したテストケース名のリスト（Failures） */
    private List<String> failedTestCases;
    
    /** エラーが発生したテストケース名のリスト（Errors） */
    private List<String> errorTestCases;
    
    /** スキップされたテストケース名のリスト（Skipped） */
    private List<String> skippedTestCases;
    
    /** 削除されたテストケース名のリスト */
    private List<String> removedTestCases;
    
    /** 修正を行ったメインコードファイル名のリスト */
    private List<String> modifiedMainFiles;
    
    /** 修正を行ったテストコードファイル名のリスト */
    private List<String> modifiedTestFiles;
    
    /** ビルド成果物の情報リスト (ファイル名 -> サイズ) */
    private List<ArtifactInfo> artifactInfoList;
    
    /**
     * ビルド成果物の情報を保持する内部クラス
     */
    public static class ArtifactInfo {
        private String fileName;
        private long sizeInBytes;
        
        public ArtifactInfo(String fileName, long sizeInBytes) {
            this.fileName = fileName;
            this.sizeInBytes = sizeInBytes;
        }
        
        public String getFileName() {
            return fileName;
        }
        
        public long getSizeInBytes() {
            return sizeInBytes;
        }
    }
    
    /**
     * コンストラクタ
     */
    public FixMetrics() {
        this.failedTestCases = new ArrayList<>();
        this.errorTestCases = new ArrayList<>();
        this.skippedTestCases = new ArrayList<>();
        this.removedTestCases = new ArrayList<>();
        this.modifiedMainFiles = new ArrayList<>();
        this.modifiedTestFiles = new ArrayList<>();
        this.artifactInfoList = new ArrayList<>();
    }
    
    // ===== Getters and Setters =====
    
    public int getMainCodeIteration() {
        return mainCodeIteration;
    }
    
    public void setMainCodeIteration(int mainCodeIteration) {
        this.mainCodeIteration = mainCodeIteration;
    }
    
    public int getTestCodeIteration() {
        return testCodeIteration;
    }
    
    public void setTestCodeIteration(int testCodeIteration) {
        this.testCodeIteration = testCodeIteration;
    }
    
    public int getTotalIteration() {
        return totalIteration;
    }
    
    public void setTotalIteration(int totalIteration) {
        this.totalIteration = totalIteration;
    }
    
    /** 後方互換性のため残しておく（totalIterationのエイリアス） */
    @Deprecated
    public int getIteration() {
        return totalIteration;
    }
    
    /** 後方互換性のため残しておく（totalIterationのエイリアス） */
    @Deprecated
    public void setIteration(int iteration) {
        this.totalIteration = iteration;
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
    
    public double getMainCodeFixTime() {
        return mainCodeFixTime;
    }
    
    public void setMainCodeFixTime(double mainCodeFixTime) {
        this.mainCodeFixTime = mainCodeFixTime;
    }
    
    public double getTestCodeFixTime() {
        return testCodeFixTime;
    }
    
    public void setTestCodeFixTime(double testCodeFixTime) {
        this.testCodeFixTime = testCodeFixTime;
    }
    
    public double getTestExecutionTime() {
        return testExecutionTime;
    }
    
    public void setTestExecutionTime(double testExecutionTime) {
        this.testExecutionTime = testExecutionTime;
    }
    
    public int getRemovedTestMethods() {
        return removedTestMethods;
    }
    
    public void setRemovedTestMethods(int removedTestMethods) {
        this.removedTestMethods = removedTestMethods;
    }
    
    public List<String> getFailedTestCases() {
        return failedTestCases;
    }
    
    public void addFailedTestCase(String testCase) {
        if (!this.failedTestCases.contains(testCase)) {
            this.failedTestCases.add(testCase);
        }
    }
    
    public List<String> getErrorTestCases() {
        return errorTestCases;
    }
    
    public void addErrorTestCase(String testCase) {
        if (!this.errorTestCases.contains(testCase)) {
            this.errorTestCases.add(testCase);
        }
    }
    
    public List<String> getSkippedTestCases() {
        return skippedTestCases;
    }
    
    public void addSkippedTestCase(String testCase) {
        if (!this.skippedTestCases.contains(testCase)) {
            this.skippedTestCases.add(testCase);
        }
    }
    
    public List<String> getRemovedTestCases() {
        return removedTestCases;
    }
    
    public void addRemovedTestCase(String testCase) {
        if (!this.removedTestCases.contains(testCase)) {
            this.removedTestCases.add(testCase);
        }
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
    
    public List<ArtifactInfo> getArtifactInfoList() {
        return artifactInfoList;
    }
    
    public void addArtifactInfo(String fileName, long sizeInBytes) {
        this.artifactInfoList.add(new ArtifactInfo(fileName, sizeInBytes));
    }
    
    /**
     * リストを改行区切りの文字列に変換
     */
    public String getFailedTestCasesAsString() {
        return String.join("\n", failedTestCases);
    }
    
    public String getErrorTestCasesAsString() {
        return String.join("\n", errorTestCases);
    }
    
    public String getSkippedTestCasesAsString() {
        return String.join("\n", skippedTestCases);
    }
    
    public String getRemovedTestCasesAsString() {
        return String.join("\n", removedTestCases);
    }
    
    public String getModifiedMainFilesAsString() {
        return String.join("\n", modifiedMainFiles);
    }
    
    public String getModifiedTestFilesAsString() {
        return String.join("\n", modifiedTestFiles);
    }
    
    /**
     * ビルド成果物情報を文字列に変換
     * 形式: "ファイル名1:サイズ1\nファイル名2:サイズ2\n..."
     */
    public String getArtifactInfoAsString() {
        if (artifactInfoList.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < artifactInfoList.size(); i++) {
            ArtifactInfo info = artifactInfoList.get(i);
            sb.append(info.getFileName()).append(":").append(info.getSizeInBytes());
            if (i < artifactInfoList.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
    
    /**
     * ビルド成果物の総サイズを取得
     */
    public long getTotalArtifactSize() {
        long total = 0;
        for (ArtifactInfo info : artifactInfoList) {
            total += info.getSizeInBytes();
        }
        return total;
    }
}