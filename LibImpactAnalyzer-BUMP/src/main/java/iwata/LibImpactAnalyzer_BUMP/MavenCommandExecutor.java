package iwata.LibImpactAnalyzer_BUMP;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Mavenコマンドの実行とコンパイルエラー抽出を行うクラス
 * メインコードとテストコードのコンパイルを個別に実行し、
 * エラー情報を解析して返す
 */
public class MavenCommandExecutor {
    
    /**
     * メインコードのコンパイルを実行し、エラー情報を抽出
     * "mvn clean compile" コマンドを実行
     * 
     * @return ファイル名をキーとするエラー情報のMap
     * @throws Exception コンパイル実行時のエラー
     */
    public Map<String, CompilationError> compileMainCode() throws Exception {
        // Mavenコンパイルコマンドを構築
        ProcessBuilder pb = new ProcessBuilder(ApplicationConfig.getMavenCmd(), "clean", "compile");
        pb.directory(new File(ApplicationConfig.PROJECT_DIR));
        pb.redirectErrorStream(true);  // 標準エラー出力を標準出力にリダイレクト

        // プロセスを開始
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), "MS932")  // 日本語Windows対応
        );

        // エラー情報を格納するMap（ファイル名 → CompilationError）
        Map<String, CompilationError> errorFiles = new HashMap<>();
        
        // エラー行を検出する正規表現パターン
        // 例: [ERROR] Example.java:[10,5] シンボルを見つけられません
        Pattern errorPattern = Pattern.compile("([^\\\\/:*?\"<>|]+\\.java).*?\\[(\\d+),");
        
        // return文欠如エラーを検出する正規表現パターン
        // 例: [ERROR] Example.java:[15,1] return文が指定されていません
        Pattern missingReturnPattern = Pattern.compile(
        	    "\\[ERROR\\]\\s+(.+\\.java):\\[(\\d+),\\d+\\]\\s+(return文が指定されていません|missing return statement)"
        	);
        
        // Spotlessエラーを検出する正規表現パターン
        // 例: [ERROR]   src/main/java/org/example/Example.java:L10 palantir-java-format(palantir-java-format) error: '.'がありません
        Pattern spotlessErrorPattern = Pattern.compile("\\[ERROR\\]\\s+(.+\\.java):L(\\d+)\\s+.*error:");

        // コンパイル出力を1行ずつ読み込んで解析
        String line;
        while ((line = reader.readLine()) != null) {
            // エラー行のみコンソールに出力
            if (line.contains("[ERROR]")) {
                System.out.println(line);
            }
            
            // Spotlessエラーのチェック（最優先）
            Matcher spotlessMatcher = spotlessErrorPattern.matcher(line);
            if (spotlessMatcher.find()) {
                String filePath = spotlessMatcher.group(1);
                int lineNumber = Integer.parseInt(spotlessMatcher.group(2));
                
                // パスからファイル名を抽出
                String fileName = new File(filePath).getName();
                
                // ファイルの完全パスを取得
                String fullPath = JavaFileSearcher.findJavaFile(fileName);
                if (fullPath != null) {
                    // CompilationErrorオブジェクトを取得または作成
                    CompilationError errorInfo = errorFiles.computeIfAbsent(fileName, 
                        k -> new CompilationError(fileName, fullPath));
                    errorInfo.addErrorLine(lineNumber);
                    System.out.println("Spotlessエラー検出: " + fileName + " 行:" + lineNumber);
                }
                continue;
            }
            
            // return文欠如エラーのチェック
            Matcher returnMatcher = missingReturnPattern.matcher(line);
            if (returnMatcher.find()) {
                String filePath = returnMatcher.group(1);
                int lineNumber = Integer.parseInt(returnMatcher.group(2));
                String fileName = new File(filePath).getName();
                
                // ファイルの完全パスを取得
                String fullPath = JavaFileSearcher.findJavaFile(fileName);
                if (fullPath != null) {
                    // CompilationErrorオブジェクトを取得または作成
                    CompilationError errorInfo = errorFiles.computeIfAbsent(fileName, 
                        k -> new CompilationError(fileName, fullPath));
                    errorInfo.addErrorLine(lineNumber);
                    errorInfo.setMissingReturnStatement(true);  // return文欠如フラグをセット
                }
                continue;
            }
            
            // 通常のコンパイルエラーのチェック
            Matcher m = errorPattern.matcher(line);
            if (m.find()) {
                String fileName = m.group(1);
                int lineNumber = Integer.parseInt(m.group(2));
                
                // ファイルの完全パスを取得
                String filePath = JavaFileSearcher.findJavaFile(fileName);
                if (filePath != null) {
                    // CompilationErrorオブジェクトを取得または作成
                    CompilationError errorInfo = errorFiles.computeIfAbsent(fileName, 
                        k -> new CompilationError(fileName, filePath));
                    errorInfo.addErrorLine(lineNumber);
                }
            }
        }
        
        // プロセスの終了を待機
        process.waitFor();

        return errorFiles;
    }
    
    /**
     * テストコードのコンパイルを実行し、エラー情報を抽出
     * "mvn test-compile" コマンドを実行
     * 
     * @return ファイル名をキーとするエラー情報のMap
     * @throws Exception コンパイル実行時のエラー
     */
    public Map<String, CompilationError> compileTestCode() throws Exception {
        // Mavenテストコンパイルコマンドを構築
        ProcessBuilder pb = new ProcessBuilder(ApplicationConfig.getMavenCmd(), "test-compile");
        pb.directory(new File(ApplicationConfig.PROJECT_DIR));
        pb.redirectErrorStream(true);

        // プロセスを開始
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), "MS932")  // 日本語Windows対応
        );

        // エラー情報を格納するMap（ファイル名 → CompilationError）
        Map<String, CompilationError> errorFiles = new HashMap<>();
        
        // エラー行を検出する正規表現パターン
        Pattern errorPattern = Pattern.compile("\\[ERROR\\]\\s+(.+\\.java):\\[(\\d+),(\\d+)\\]");
        
        // Spotlessエラーを検出する正規表現パターン
        Pattern spotlessErrorPattern = Pattern.compile("\\[ERROR\\]\\s+(.+\\.java):L(\\d+)\\s+.*error:");

        // コンパイル出力を1行ずつ読み込んで解析
        String line;
        while ((line = reader.readLine()) != null) {
            // エラー行のみコンソールに出力
            if (line.contains("[ERROR]")) {
                System.out.println(line);
            }
            
            // Spotlessエラーのチェック（最優先）
            Matcher spotlessMatcher = spotlessErrorPattern.matcher(line);
            if (spotlessMatcher.find()) {
                String filePath = spotlessMatcher.group(1);
                int lineNumber = Integer.parseInt(spotlessMatcher.group(2));
                
                // パスからファイル名を抽出
                String fileName = new File(filePath).getName();
                
                // ファイルの完全パスを取得
                String fullPath = JavaFileSearcher.findJavaFile(fileName);
                if (fullPath != null) {
                    // CompilationErrorオブジェクトを取得または作成
                    CompilationError errorInfo = errorFiles.computeIfAbsent(fileName, 
                        k -> new CompilationError(fileName, fullPath));
                    errorInfo.addErrorLine(lineNumber);
                    System.out.println("Spotlessエラー検出: " + fileName + " 行:" + lineNumber);
                }
                continue;
            }
            
            // コンパイルエラーのチェック
            Matcher m = errorPattern.matcher(line);
            if (m.find()) {
                String fileName = m.group(1);
                int lineNumber = Integer.parseInt(m.group(2));
                
                // ファイルの完全パスを取得
                String filePath = JavaFileSearcher.findJavaFile(fileName);
                if (filePath != null) {
                    // CompilationErrorオブジェクトを取得または作成
                    CompilationError errorInfo = errorFiles.computeIfAbsent(fileName, 
                        k -> new CompilationError(fileName, filePath));
                    errorInfo.addErrorLine(lineNumber);
                }
            }
        }
        
        // プロセスの終了を待機
        process.waitFor();

        return errorFiles;
    }
    
    /**
     * テストを実行してテスト結果を解析
     * "mvn test" コマンドを実行
     * 
     * @param metrics テスト結果を格納するメトリクスオブジェクト
     * @throws Exception テスト実行時のエラー
     */
    public void runTests(FixMetrics metrics) throws Exception {
        // Mavenテストコマンドを構築
        ProcessBuilder pb = new ProcessBuilder(ApplicationConfig.getMavenCmd(), "test");
        pb.directory(new File(ApplicationConfig.PROJECT_DIR));
        pb.redirectErrorStream(true);

        // 開始時刻を記録
        long startTime = System.currentTimeMillis();

        // プロセスを開始
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), "MS932")
        );

        // テスト結果のサマリーを検出する正規表現パターン
        // 例: Tests run: 10, Failures: 2, Errors: 1, Skipped: 0
        Pattern summaryPattern = Pattern.compile("Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");
        
        // 失敗したテストケースを検出する正規表現パターン
        // 例: testMethod(com.example.TestClass)
        Pattern failedTestPattern = Pattern.compile("^\\s*(\\S+)\\(([^)]+)\\).*?<<< FAILURE!|<<< ERROR!");

        String line;
        while ((line = reader.readLine()) != null) {
            // テスト結果サマリーのチェック
            Matcher summaryMatcher = summaryPattern.matcher(line);
            if (summaryMatcher.find()) {
                int testsRun = Integer.parseInt(summaryMatcher.group(1));
                int failures = Integer.parseInt(summaryMatcher.group(2));
                int errors = Integer.parseInt(summaryMatcher.group(3));
                int skipped = Integer.parseInt(summaryMatcher.group(4));
                
                // 最後に見つかったサマリーを使用（モジュールごとに出力される場合があるため）
                metrics.setTestsRun(testsRun);
                metrics.setFailures(failures);
                metrics.setErrors(errors);
                metrics.setSkipped(skipped);
            }
            
            // 失敗したテストケースのチェック
            Matcher failedTestMatcher = failedTestPattern.matcher(line);
            if (failedTestMatcher.find()) {
                String testMethod = failedTestMatcher.group(1);
                String testClass = failedTestMatcher.group(2);
                String testCase = testClass + "." + testMethod;
                metrics.addFailedTestCase(testCase);
            }
        }

        // プロセスの終了を待機
        process.waitFor();

        // 終了時刻を記録
        long endTime = System.currentTimeMillis();
        double executionTime = (endTime - startTime) / 1000.0;
        metrics.setExecutionTime(executionTime);
    }
}