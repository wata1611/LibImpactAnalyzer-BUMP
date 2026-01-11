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
     * メインコードのコンパイルを実行し、エラー情報とビルド結果を抽出
     * "mvn clean compile" コマンドを実行
     * 
     * @return コンパイル結果（エラー情報とビルド成功/失敗）
     * @throws Exception コンパイル実行時のエラー
     */
    public CompilationResult compileMainCode() throws Exception {
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
        
        // ビルド成功フラグ
        boolean buildSuccess = false;
        
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
            // エラー行、INFO行、BUILD行を出力
            if (line.contains("[ERROR]") || line.contains("[INFO]") || 
                line.contains("BUILD SUCCESS") || line.contains("BUILD FAILURE")) {
                System.out.println(line);
            }
            
            // BUILD SUCCESSのチェック
            if (line.contains("BUILD SUCCESS")) {
                buildSuccess = true;
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

        return new CompilationResult(errorFiles, buildSuccess);
    }
    
    /**
     * テストコードのコンパイルを実行し、エラー情報とビルド結果を抽出
     * "mvn test-compile" コマンドを実行
     * 
     * @return コンパイル結果（エラー情報とビルド成功/失敗）
     * @throws Exception コンパイル実行時のエラー
     */
    public CompilationResult compileTestCode() throws Exception {
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
        
        // ビルド成功フラグ
        boolean buildSuccess = false;
        
        // エラー行を検出する正規表現パターン（メインコードと同じ緩いパターン）
        Pattern errorPattern = Pattern.compile("([^\\\\/:*?\"<>|]+\\.java).*?\\[(\\d+),");
        
        // return文欠如エラーを検出する正規表現パターン
        Pattern missingReturnPattern = Pattern.compile(
            "\\[ERROR\\]\\s+(.+\\.java):\\[(\\d+),\\d+\\]\\s+(return文が指定されていません|missing return statement)"
        );
        
        // Spotlessエラーを検出する正規表現パターン
        Pattern spotlessErrorPattern = Pattern.compile("\\[ERROR\\]\\s+(.+\\.java):L(\\d+)\\s+.*error:");

        // コンパイル出力を1行ずつ読み込んで解析
        String line;
        while ((line = reader.readLine()) != null) {
            // エラー行、INFO行、BUILD行を出力
            if (line.contains("[ERROR]") || line.contains("[INFO]") || 
                line.contains("BUILD SUCCESS") || line.contains("BUILD FAILURE")) {
                System.out.println(line);
            }
            
            // BUILD SUCCESSのチェック
            if (line.contains("BUILD SUCCESS")) {
                buildSuccess = true;
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

        return new CompilationResult(errorFiles, buildSuccess);
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

        // プロセスを開始
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), "MS932")
        );

        // テスト結果のサマリーを検出する正規表現パターン
        // 例: Tests run: 10, Failures: 2, Errors: 1, Skipped: 0
        Pattern summaryPattern = Pattern.compile("Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");
        
        // 失敗したテストケースを検出する正規表現パターン（[ERROR]行から抽出）
        // 例: [ERROR] com.github.knaufk.flink.faker.FlinkFakerIntegrationTest.testFlinkFakerWithComplexTypes  Time elapsed: 4.494 s  <<< ERROR!
        // 例: [ERROR] com.github.knaufk.flink.faker.FlinkFakerTableSourceFactoryTest.testInvalidExpressionIsInvalid:143
        Pattern failedTestPattern1 = Pattern.compile("^\\[ERROR\\]\\s+([^\\s:]+(?:\\.[^\\s:]+)+)(?:\\s+Time elapsed:|:).*?(?:<<< FAILURE!|<<< ERROR!|$)");
        
        // 別の形式: testMethod(com.example.TestClass) の形式（旧形式との互換性のため残す）
        Pattern failedTestPattern2 = Pattern.compile("^\\s*(\\S+)\\(([^)]+)\\).*?(?:<<< FAILURE!|<<< ERROR!)");
        
        // WARNINGメッセージからSkippedテストを検出するパターン
        // 例: [WARNING] Tests run: 1, Failures: 0, Errors: 0, Skipped: 1, Time elapsed: 0.035 s - in org.jenkinsci.plugins.api.GihubAPITest
        Pattern skippedTestPattern = Pattern.compile("^\\[WARNING\\]\\s+Tests run:.*Skipped: \\d+.*- in (.+)$");

        String line;
        while ((line = reader.readLine()) != null) {
            // テスト関連のログを出力
            if (line.contains("[INFO]") || line.contains("[ERROR]") || line.contains("[WARNING]") ||
                line.contains("Tests run:") || line.contains("BUILD SUCCESS") || 
                line.contains("BUILD FAILURE") || line.contains("Running ") ||
                line.contains("FAILURE!") || line.contains("ERROR!")) {
                System.out.println(line);
            }
            
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
            
            // 失敗したテストケースのチェック（パターン1: [ERROR]行から）
            Matcher failedTestMatcher1 = failedTestPattern1.matcher(line);
            if (failedTestMatcher1.find()) {
                String testCase = failedTestMatcher1.group(1);
                
                // FAILURE または ERROR を判定
                if (line.contains("<<< FAILURE!")) {
                    metrics.addFailedTestCase(testCase);
                    System.out.println("Failed test detected: " + testCase);
                } else if (line.contains("<<< ERROR!")) {
                    metrics.addErrorTestCase(testCase);
                    System.out.println("Error test detected: " + testCase);
                }
                continue;
            }
            
            // 失敗したテストケースのチェック（パターン2: 旧形式）
            Matcher failedTestMatcher2 = failedTestPattern2.matcher(line);
            if (failedTestMatcher2.find()) {
                String testMethod = failedTestMatcher2.group(1);
                String testClass = failedTestMatcher2.group(2);
                String testCase = testClass + "." + testMethod;
                
                if (line.contains("<<< FAILURE!")) {
                    metrics.addFailedTestCase(testCase);
                    System.out.println("Failed test detected: " + testCase);
                } else if (line.contains("<<< ERROR!")) {
                    metrics.addErrorTestCase(testCase);
                    System.out.println("Error test detected: " + testCase);
                }
                continue;
            }
            
            // スキップされたテストケースのチェック
            Matcher skippedTestMatcher = skippedTestPattern.matcher(line);
            if (skippedTestMatcher.find()) {
                String testClass = skippedTestMatcher.group(1);
                metrics.addSkippedTestCase(testClass);
                System.out.println("Skipped test detected: " + testClass);
            }
        }

        // プロセスの終了を待機
        process.waitFor();
    }
    
    /**
     * ビルドを実行してビルド成果物を生成
     * "mvn package -DskipTests" コマンドを実行
     * 
     * @param metrics ビルド成果物の情報を格納するメトリクスオブジェクト
     * @return ビルドが成功した場合true
     * @throws Exception ビルド実行時のエラー
     */
    public boolean buildPackage(FixMetrics metrics) throws Exception {
        System.out.println("\n===== Building Package =====");
        
        // Mavenパッケージコマンドを構築（テストはスキップ）
        ProcessBuilder pb = new ProcessBuilder(ApplicationConfig.getMavenCmd(), "package", "-DskipTests");
        pb.directory(new File(ApplicationConfig.PROJECT_DIR));
        pb.redirectErrorStream(true);

        // プロセスを開始
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), "MS932")
        );

        boolean buildSuccess = false;
        
        // ビルド出力を1行ずつ読み込んで解析
        String line;
        while ((line = reader.readLine()) != null) {
            // ビルド関連のログを出力
            if (line.contains("[INFO]") || line.contains("[ERROR]") || 
                line.contains("BUILD SUCCESS") || line.contains("BUILD FAILURE")) {
                System.out.println(line);
            }
            
            // BUILD SUCCESSのチェック
            if (line.contains("BUILD SUCCESS")) {
                buildSuccess = true;
            }
        }
        
        // プロセスの終了を待機
        int exitCode = process.waitFor();
        
        if (buildSuccess && exitCode == 0) {
            System.out.println("Build successful. Collecting artifact information...");
            collectArtifactInfo(metrics);
            return true;
        } else {
            System.out.println("Build failed.");
            return false;
        }
    }
    
    /**
     * ビルド成果物の情報を収集
     * targetディレクトリからjar, war, earファイルを検索してサイズを取得
     * 
     * @param metrics ビルド成果物の情報を格納するメトリクスオブジェクト
     */
    private void collectArtifactInfo(FixMetrics metrics) {
        List<File> targetDirs = new ArrayList<>();
        
        // シングルモジュールの場合
        if (!ApplicationConfig.isMultiModule()) {
            File targetDir = new File(ApplicationConfig.PROJECT_DIR, "target");
            if (targetDir.exists() && targetDir.isDirectory()) {
                targetDirs.add(targetDir);
            }
        } else {
            // マルチモジュールの場合、各モジュールのtargetディレクトリを追加
            for (ModuleInfo module : ApplicationConfig.getModules()) {
                File targetDir = new File(module.getModuleDir(), "target");
                if (targetDir.exists() && targetDir.isDirectory()) {
                    targetDirs.add(targetDir);
                }
            }
        }
        
        // 各targetディレクトリからjar, war, earファイルを検索
        for (File targetDir : targetDirs) {
            File[] files = targetDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    String lowerName = name.toLowerCase();
                    return (lowerName.endsWith(".jar") || 
                            lowerName.endsWith(".war") || 
                            lowerName.endsWith(".ear")) &&
                           !lowerName.contains("sources") &&
                           !lowerName.contains("javadoc");
                }
            });
            
            if (files != null) {
                for (File file : files) {
                    long size = file.length();
                    metrics.addArtifactInfo(file.getName(), size);
                    System.out.println("Artifact found: " + file.getName() + " (" + size + " bytes)");
                }
            }
        }
        
        if (metrics.getArtifactInfoList().isEmpty()) {
            System.out.println("No artifacts found in target directories.");
        } else {
            System.out.println("Total artifact size: " + metrics.getTotalArtifactSize() + " bytes");
        }
    }
}