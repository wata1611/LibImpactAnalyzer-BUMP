package iwata.LibImpactAnalyzer_BUMP;

import java.io.File;
import java.util.*;

/**
 * コンパイルエラー自動修正ツール(メインクラス)
 * 
 * 処理の流れ:
 * 1. フェーズ1: メインコード(srcディレクトリ)のコンパイルエラーを自動修正
 * 2. フェーズ2: テストコード(testsディレクトリ)のコンパイルエラーを自動修正
 * 3. テスト実行とメトリクス収集
 * 4. ビルド実行とビルド成果物のサイズ取得
 * 5. CSV出力
 * 
 * 各フェーズでは最大MAX_ITERATIONS回まで繰り返し修正を試みる
 * マルチモジュールプロジェクトにも対応
 */
public class CompilationErrorAutoFixer {
    
    /** Mavenコマンド実行(コンパイル実行とエラー抽出) */
    private final MavenCommandExecutor commandExecutor;
    
    /** ソースコード修正(ファイルの自動修正処理) */
    private final SourceCodeFixer sourceCodeFixer;
    
    /** メトリクスデータのリスト */
    private final List<FixMetrics> metricsList;
    
    /** メインコードの総反復回数 */
    private int mainCodeIterations;
    
    /** テストコードの総反復回数 */
    private int testCodeIterations;
    
    /** 総反復回数（メイン + テスト） */
    private int totalIterations;
    
    /** 修正されたメインコードファイルのセット */
    private final Set<String> allModifiedMainFiles;
    
    /** 修正されたテストコードファイルのセット */
    private final Set<String> allModifiedTestFiles;
    
    /** メインコード累積削除行数 */
    private int totalMainDeletedLines;
    
    /** テストコード累積削除行数 */
    private int totalTestDeletedLines;
    
    /** 削除されたテストメソッド数 */
    private int totalRemovedTestMethods;
    
    /** 現在のループ番号（メインコードとテストコードで連番） */
    private int currentLoopNumber;
    
    /** プログラム開始時刻 */
    private long programStartTime;
    
    /** メインコード修正開始時刻 */
    private long mainCodeFixStartTime;
    
    /** テストコード修正開始時刻 */
    private long testCodeFixStartTime;
    
    /** テスト実行開始時刻 */
    private long testExecutionStartTime;
    
    /** メインコードのビルド成功フラグ */
    private boolean mainCodeBuildSuccess = false;
    
    /** テストコードのビルド成功フラグ */
    private boolean testCodeBuildSuccess = false;
    
    /** テストコード修正時の削除テストケースを記録するメトリクス */
    private FixMetrics testCodeTempMetrics;
    
    /** メインコードが最大イテレーションに到達したかどうか */
    private boolean mainCodeReachedMaxIterations = false;
    
    /** テストコードが最大イテレーションに到達したかどうか */
    private boolean testCodeReachedMaxIterations = false;
    
    /**
     * コンストラクタ
     * 必要なコンポーネントを初期化
     */
    public CompilationErrorAutoFixer() {
        this.commandExecutor = new MavenCommandExecutor();
        this.sourceCodeFixer = new SourceCodeFixer();
        this.metricsList = new ArrayList<>();
        this.mainCodeIterations = 0;
        this.testCodeIterations = 0;
        this.totalIterations = 0;
        this.allModifiedMainFiles = new HashSet<>();
        this.allModifiedTestFiles = new HashSet<>();
        this.totalMainDeletedLines = 0;
        this.totalTestDeletedLines = 0;
        this.totalRemovedTestMethods = 0;
        this.currentLoopNumber = 0;
    }
    
    /**
     * アプリケーションのエントリーポイント
     * @param args コマンドライン引数(未使用)
     * @throws Exception 処理中のエラー
     */
    public static void main(String[] args) throws Exception {
    	// コマンドライン引数から設定を初期化（CSV出力先パスとプロジェクトディレクトリ）
        ApplicationConfig.initializeFromArgs(args);
        // プロジェクト構成の初期化(マルチモジュール検出)
        ApplicationConfig.initialize();
        
        // コンソール出力のキャプチャを開始
        ConsoleOutputCapture.start();
        
        try {
            CompilationErrorAutoFixer autoFixer = new CompilationErrorAutoFixer();
            autoFixer.run();
        } finally {
            // 例外が発生してもコンソール出力のキャプチャを終了
            ConsoleOutputCapture.stop();
        }
    }
    
    /**
     * メイン処理
     * フェーズ1(メインコード)とフェーズ2(テストコード)を順次実行
     * @throws Exception 処理中のエラー
     */
    public void run() throws Exception {
        // プログラム開始時刻を記録
        programStartTime = System.currentTimeMillis();
        
        // フェーズ1: メインコードの修正
        System.out.println("===== Phase 1: Main Code =====");
        mainCodeFixStartTime = System.currentTimeMillis();
        boolean mainCodeSuccess = fixMainCodeErrors();
        long mainCodeFixEndTime = System.currentTimeMillis();
        double mainCodeFixTime = (mainCodeFixEndTime - mainCodeFixStartTime) / 1000.0;
        System.out.println("Main code fix time: " + String.format("%.2f", mainCodeFixTime) + " seconds");
        
        // フェーズ2: テストコードの修正
        System.out.println("\n===== Phase 2: Test Code =====");
        testCodeFixStartTime = System.currentTimeMillis();
        boolean testCodeSuccess = fixTestCodeErrors();
        long testCodeFixEndTime = System.currentTimeMillis();
        double testCodeFixTime = (testCodeFixEndTime - testCodeFixStartTime) / 1000.0;
        System.out.println("Test code fix time: " + String.format("%.2f", testCodeFixTime) + " seconds");
        
        // テスト実行とメトリクス収集
        System.out.println("\n===== Running Tests =====");
        testExecutionStartTime = System.currentTimeMillis();
        
        // テストコード修正中に記録した削除テストケース情報を使用
        FixMetrics finalMetrics = collectFinalMetrics(testCodeTempMetrics);
        long testExecutionEndTime = System.currentTimeMillis();
        double testExecutionTime = (testExecutionEndTime - testExecutionStartTime) / 1000.0;
        System.out.println("Test execution time: " + String.format("%.2f", testExecutionTime) + " seconds");
        
        // 両方のコンパイルが成功した場合、ビルドを実行
        boolean buildSuccess = false;
        boolean overallSuccess = mainCodeBuildSuccess && testCodeBuildSuccess;
        
        if (overallSuccess) {
            buildSuccess = commandExecutor.buildPackage(finalMetrics);
        }
        
        // プログラム終了時刻を記録し、全体の実行時間を計算
        long programEndTime = System.currentTimeMillis();
        double totalExecutionTime = (programEndTime - programStartTime) / 1000.0;
        
        // 各時間をメトリクスに設定
        finalMetrics.setMainCodeFixTime(mainCodeFixTime);
        finalMetrics.setTestCodeFixTime(testCodeFixTime);
        finalMetrics.setTestExecutionTime(testExecutionTime);
        finalMetrics.setExecutionTime(totalExecutionTime);
        
        System.out.println("\n===== Execution Time Summary =====");
        System.out.println("Main code fix time: " + String.format("%.2f", mainCodeFixTime) + " seconds");
        System.out.println("Test code fix time: " + String.format("%.2f", testCodeFixTime) + " seconds");
        System.out.println("Test execution time: " + String.format("%.2f", testExecutionTime) + " seconds");
        System.out.println("Total execution time: " + String.format("%.2f", totalExecutionTime) + " seconds");
        
        // CSV出力(最終結果のみ)
        List<FixMetrics> finalMetricsList = new ArrayList<>();
        finalMetricsList.add(finalMetrics);
        CsvWriter.writeMetrics(finalMetricsList);
        
        // 最終結果の判定とディレクトリ移動
        // イテレーション上限到達の判定
        boolean iterationLimitReached = mainCodeReachedMaxIterations || testCodeReachedMaxIterations;
        
        if (iterationLimitReached) {
            System.out.println("\n===== ITERATION LIMIT REACHED =====");
            if (mainCodeReachedMaxIterations) {
                System.out.println("Main code reached maximum iterations (" + ApplicationConfig.MAX_ITERATIONS + ")");
            }
            if (testCodeReachedMaxIterations) {
                System.out.println("Test code reached maximum iterations (" + ApplicationConfig.MAX_ITERATIONS + ")");
            }
            moveToResultDirectory("iteration_limit_reached");
        } else if (overallSuccess) {
            System.out.println("\n===== BUILD SUCCESS: Both Main and Test Code Compiled Successfully =====");
            moveToResultDirectory("success");
            
            // ビルド成果物のサイズ取得に成功した場合、artifact_size_successにもコピー
            if (buildSuccess && !finalMetrics.getArtifactInfoList().isEmpty()) {
                System.out.println("Artifact size collection successful. Copying to artifact_size_success directory...");
                copyToResultDirectory("artifact_size_success");
            }
        } else {
            System.out.println("\n===== BUILD FAILURE: Compilation Failed =====");
            if (!mainCodeBuildSuccess) {
                System.out.println("Main code build failed");
            }
            if (!testCodeBuildSuccess) {
                System.out.println("Test code build failed");
            }
            moveToResultDirectory("failure");
        }
        
        // 従来の成功/失敗メッセージも表示
        if (mainCodeSuccess && testCodeSuccess) {
            System.out.println("Note: All errors were resolved within max iterations");
        } else {
            System.out.println("Note: Max iterations reached. Some errors may remain");
        }
    }
    
    /**
     * SHA名ディレクトリを成功/失敗のresultディレクトリに移動
     * @param resultType "success"、"failure"、または "iteration_limit_reached"
     */
    private void moveToResultDirectory(String resultType) {
        try {
            if (ApplicationConfig.SHA == null || ApplicationConfig.SHA.isEmpty()) {
                System.out.println("警告: SHAが設定されていないため、ディレクトリ移動をスキップします");
                return;
            }
            
            // 移動元: /output/SHA
            File sourceDir = new File(ApplicationConfig.OUTPUT_DIR, ApplicationConfig.SHA);
            
            // 移動先: /output/result/success/SHA、/output/result/failure/SHA、
            //        または /output/result/iteration_limit_reached/SHA
            File resultBaseDir = new File(ApplicationConfig.OUTPUT_DIR, "result");
            File resultTypeDir = new File(resultBaseDir, resultType);
            File destinationDir = new File(resultTypeDir, ApplicationConfig.SHA);
            
            // result/success、result/failure、または result/iteration_limit_reached ディレクトリを作成
            if (!resultTypeDir.exists()) {
                resultTypeDir.mkdirs();
                System.out.println("結果ディレクトリを作成: " + resultTypeDir.getAbsolutePath());
            }
            
            // SHAディレクトリが存在する場合のみ移動
            if (sourceDir.exists()) {
                // 移動先に既に存在する場合は削除
                if (destinationDir.exists()) {
                    deleteDirectory(destinationDir);
                }
                
                // ディレクトリを移動（リネーム）
                boolean moved = sourceDir.renameTo(destinationDir);
                
                if (moved) {
                    System.out.println("結果ディレクトリに移動: " + destinationDir.getAbsolutePath());
                } else {
                    System.err.println("警告: ディレクトリの移動に失敗しました");
                    System.err.println("  移動元: " + sourceDir.getAbsolutePath());
                    System.err.println("  移動先: " + destinationDir.getAbsolutePath());
                }
            } else {
                System.out.println("警告: SHAディレクトリが存在しないため、移動をスキップします: " + sourceDir.getAbsolutePath());
            }
            
        } catch (Exception e) {
            System.err.println("ディレクトリ移動中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * SHA名ディレクトリを追加の結果ディレクトリにコピー
     * （successディレクトリに移動済みのものをartifact_size_successにもコピー）
     * @param resultType "artifact_size_success"
     */
    private void copyToResultDirectory(String resultType) {
        try {
            if (ApplicationConfig.SHA == null || ApplicationConfig.SHA.isEmpty()) {
                System.out.println("警告: SHAが設定されていないため、ディレクトリコピーをスキップします");
                return;
            }
            
            // コピー元: /output/result/success/SHA
            File resultBaseDir = new File(ApplicationConfig.OUTPUT_DIR, "result");
            File successDir = new File(resultBaseDir, "success");
            File sourceDir = new File(successDir, ApplicationConfig.SHA);
            
            // コピー先: /output/result/artifact_size_success/SHA
            File resultTypeDir = new File(resultBaseDir, resultType);
            File destinationDir = new File(resultTypeDir, ApplicationConfig.SHA);
            
            // result/artifact_size_success ディレクトリを作成
            if (!resultTypeDir.exists()) {
                resultTypeDir.mkdirs();
                System.out.println("結果ディレクトリを作成: " + resultTypeDir.getAbsolutePath());
            }
            
            // ソースディレクトリが存在する場合のみコピー
            if (sourceDir.exists()) {
                // コピー先に既に存在する場合は削除
                if (destinationDir.exists()) {
                    deleteDirectory(destinationDir);
                }
                
                // ディレクトリをコピー
                copyDirectory(sourceDir, destinationDir);
                System.out.println("結果ディレクトリにコピー: " + destinationDir.getAbsolutePath());
            } else {
                System.out.println("警告: コピー元ディレクトリが存在しません: " + sourceDir.getAbsolutePath());
            }
            
        } catch (Exception e) {
            System.err.println("ディレクトリコピー中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * ディレクトリを再帰的に削除
     * @param directory 削除対象のディレクトリ
     */
    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }
    
    /**
     * ディレクトリを再帰的にコピー
     * @param source コピー元ディレクトリ
     * @param destination コピー先ディレクトリ
     * @throws Exception コピー中のエラー
     */
    private void copyDirectory(File source, File destination) throws Exception {
        if (source.isDirectory()) {
            // ディレクトリの場合
            if (!destination.exists()) {
                destination.mkdirs();
            }
            
            File[] files = source.listFiles();
            if (files != null) {
                for (File file : files) {
                    File destFile = new File(destination, file.getName());
                    copyDirectory(file, destFile);
                }
            }
        } else {
            // ファイルの場合
            java.nio.file.Files.copy(
                source.toPath(), 
                destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
        }
    }
    
    /**
     * フェーズ1: メインコード(srcディレクトリ)のエラー修正処理
     * 
     * 処理の流れ:
     * 1. mvn clean compile を実行してエラーを抽出
     * 2. エラーが発見されたファイルを1つずつ修正
     * 3. エラーがなくなるまで、または最大反復回数に達するまで繰り返す
     * 
     * @return コンパイルが成功した場合true、最大回数に達した場合false
     * @throws Exception コンパイル実行時のエラー
     */
    private boolean fixMainCodeErrors() throws Exception {
        int iteration = 1;
        boolean success = false;
        
        // 最大反復回数まで繰り返す
        while (iteration <= ApplicationConfig.MAX_ITERATIONS) {
            currentLoopNumber++;
            System.out.println("\n--- Main Code Loop: " + iteration + " (Global Loop: " + currentLoopNumber + ") ---");

            // 反復開始時のメインコード総行数を記録
            int mainCodeTotalLines = LineCounter.countTotalLines(ApplicationConfig.getAllSrcDirs());

            // メインコードをコンパイルしてエラーを抽出
            CompilationResult result = commandExecutor.compileMainCode();
            Map<String, CompilationError> mainErrorFiles = result.getErrorFiles();
            
            // ビルド成功フラグを更新
            if (result.isBuildSuccess()) {
                mainCodeBuildSuccess = true;
            }
            
            // エラーがなければ成功
            if (mainErrorFiles.isEmpty()) {
                System.out.println("Main code compilation successful (Loop: " + iteration + ", Global Loop: " + currentLoopNumber + ")");
                success = true;
                break;
            }

            // エラーファイル数を表示
            System.out.println("Error files: " + mainErrorFiles.size());

            // メトリクス収集用の変数
            int mainCodeDeletedLines = 0;
            Set<String> modifiedMainFiles = new HashSet<>();
            
            // 一時的なメトリクス（メインコード修正時は削除されたテストケースを記録しない）
            FixMetrics tempMetrics = new FixMetrics();

            // 各エラーファイルに対して修正処理を実行
            for (CompilationError compilationError : mainErrorFiles.values()) {
                File file = new File(compilationError.getFilePath());
                
                // ファイルが存在しない場合はスキップ
                if (!file.exists()) {
                    continue;
                }

                // ファイルを修正（ループ番号を渡す）
                int[] result_fix = sourceCodeFixer.fixErrorFile(file, compilationError, currentLoopNumber, tempMetrics);
                int deletedLines = result_fix[0];
                if (deletedLines > 0) {
                    mainCodeDeletedLines += deletedLines;
                    modifiedMainFiles.add(compilationError.getFileName());
                }
            }

            // 累積データを更新
            totalMainDeletedLines += mainCodeDeletedLines;
            allModifiedMainFiles.addAll(modifiedMainFiles);
            mainCodeIterations = iteration;

            // 修正されたファイルがない場合は処理を終了
            if (modifiedMainFiles.isEmpty()) {
                break;
            }

            iteration++;
        }
        
        // 最大イテレーションに到達したかをチェック
        if (iteration > ApplicationConfig.MAX_ITERATIONS && !success) {
            mainCodeReachedMaxIterations = true;
        }
        
        return success;
    }
    
    /**
     * フェーズ2: テストコード(testsディレクトリ)のエラー修正処理
     * 
     * 処理の流れ:
     * 1. mvn test-compile を実行してエラーを抽出
     * 2. エラーが発見されたファイルを1つずつ修正
     * 3. エラーがなくなるまで、または最大反復回数に達するまで繰り返す
     * 
     * @return コンパイルが成功した場合true、最大回数に達した場合false
     * @throws Exception コンパイル実行時のエラー
     */
    private boolean fixTestCodeErrors() throws Exception {
        int iteration = 1;
        boolean success = false;
        
        // テストコード修正時の削除されたテストケース名を記録するための一時メトリクス
        testCodeTempMetrics = new FixMetrics();
        
        // 最大反復回数まで繰り返す
        while (iteration <= ApplicationConfig.MAX_ITERATIONS) {
            currentLoopNumber++;
            System.out.println("\n--- Test Code Loop: " + iteration + " (Global Loop: " + currentLoopNumber + ") ---");

            // 反復開始時のテストコード総行数を記録
            int testCodeTotalLines = LineCounter.countTotalLines(ApplicationConfig.getAllTestDirs());

            // テストコードをコンパイルしてエラーを抽出
            CompilationResult result = commandExecutor.compileTestCode();
            Map<String, CompilationError> testErrorFiles = result.getErrorFiles();
            
            // ビルド成功フラグを更新
            if (result.isBuildSuccess()) {
                testCodeBuildSuccess = true;
            }
            
            // エラーがなければ成功
            if (testErrorFiles.isEmpty()) {
                System.out.println("Test code compilation successful (Loop: " + iteration + ", Global Loop: " + currentLoopNumber + ")");
                success = true;
                break;
            }

            // エラーファイル数を表示
            System.out.println("Error files: " + testErrorFiles.size());

            // メトリクス収集用の変数
            int testCodeDeletedLines = 0;
            int removedTestMethods = 0;
            Set<String> modifiedTestFiles = new HashSet<>();

            // 各エラーファイルに対して修正処理を実行
            for (CompilationError compilationError : testErrorFiles.values()) {
                File file = new File(compilationError.getFilePath());
                
                // ファイルが存在しない場合はスキップ
                if (!file.exists()) {
                    continue;
                }

                // ファイルを修正（ループ番号とメトリクスを渡す）
                int[] result_fix = sourceCodeFixer.fixErrorFile(file, compilationError, currentLoopNumber, testCodeTempMetrics);
                int deletedLines = result_fix[0];
                int removedMethods = result_fix[1];
                if (deletedLines > 0) {
                    testCodeDeletedLines += deletedLines;
                    modifiedTestFiles.add(compilationError.getFileName());
                }
                removedTestMethods += removedMethods;
            }

            // 累積データを更新
            totalTestDeletedLines += testCodeDeletedLines;
            totalRemovedTestMethods += removedTestMethods;
            allModifiedTestFiles.addAll(modifiedTestFiles);
            testCodeIterations = iteration;

            // 修正されたファイルがない場合は処理を終了
            if (modifiedTestFiles.isEmpty()) {
                break;
            }

            iteration++;
        }
        
        // 最大イテレーションに到達したかをチェック
        if (iteration > ApplicationConfig.MAX_ITERATIONS && !success) {
            testCodeReachedMaxIterations = true;
        }
        
        return success;
    }
    
    /**
     * 最終的なメトリクスを収集(テスト実行結果を含む)
     * 注: 実行時間はrun()メソッドで後から設定される
     * @param tempMetrics テストコード修正中に記録された削除テストケース情報
     * @return 最終メトリクス
     * @throws Exception テスト実行時のエラー
     */
    private FixMetrics collectFinalMetrics(FixMetrics tempMetrics) throws Exception {
        FixMetrics finalMetrics = new FixMetrics();
        
        // 総反復回数を計算して記録
        totalIterations = mainCodeIterations + testCodeIterations;
        finalMetrics.setMainCodeIteration(mainCodeIterations);
        finalMetrics.setTestCodeIteration(testCodeIterations);
        finalMetrics.setTotalIteration(totalIterations);
        
        // 最終的なコード行数を記録
        int mainCodeTotalLines = LineCounter.countTotalLines(ApplicationConfig.getAllSrcDirs());
        int testCodeTotalLines = LineCounter.countTotalLines(ApplicationConfig.getAllTestDirs());
        finalMetrics.setMainCodeTotalLines(mainCodeTotalLines);
        finalMetrics.setTestCodeTotalLines(testCodeTotalLines);
        
        // 累積削除行数と修正ファイルを記録
        finalMetrics.setMainCodeDeletedLines(totalMainDeletedLines);
        finalMetrics.setTestCodeDeletedLines(totalTestDeletedLines);
        finalMetrics.setMainCodeModifiedFiles(allModifiedMainFiles.size());
        finalMetrics.setTestCodeModifiedFiles(allModifiedTestFiles.size());
        
        // 削除されたテストメソッド数を記録
        finalMetrics.setRemovedTestMethods(totalRemovedTestMethods);
        
        for (String fileName : allModifiedMainFiles) {
            finalMetrics.addModifiedMainFile(fileName);
        }
        for (String fileName : allModifiedTestFiles) {
            finalMetrics.addModifiedTestFile(fileName);
        }
        
        // 削除されたテストケース名を記録
        for (String testCase : tempMetrics.getRemovedTestCases()) {
            finalMetrics.addRemovedTestCase(testCase);
        }
        
        // テストを実行してテスト結果を収集
        commandExecutor.runTests(finalMetrics);
        
        return finalMetrics;
    }
}