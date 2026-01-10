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
 * 4. CSV出力
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
    
    /** 総反復回数 */
    private int totalIterations;
    
    /** 修正されたメインコードファイルのセット */
    private final Set<String> allModifiedMainFiles;
    
    /** 修正されたテストコードファイルのセット */
    private final Set<String> allModifiedTestFiles;
    
    /** メインコード累積削除行数 */
    private int totalMainDeletedLines;
    
    /** テストコード累積削除行数 */
    private int totalTestDeletedLines;
    
    /** 現在のループ番号（メインコードとテストコードで連番） */
    private int currentLoopNumber;
    
    /** プログラム開始時刻 */
    private long programStartTime;
    
    /** メインコードのビルド成功フラグ */
    private boolean mainCodeBuildSuccess = false;
    
    /** テストコードのビルド成功フラグ */
    private boolean testCodeBuildSuccess = false;
    
    /**
     * コンストラクタ
     * 必要なコンポーネントを初期化
     */
    public CompilationErrorAutoFixer() {
        this.commandExecutor = new MavenCommandExecutor();
        this.sourceCodeFixer = new SourceCodeFixer();
        this.metricsList = new ArrayList<>();
        this.totalIterations = 0;
        this.allModifiedMainFiles = new HashSet<>();
        this.allModifiedTestFiles = new HashSet<>();
        this.totalMainDeletedLines = 0;
        this.totalTestDeletedLines = 0;
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
        boolean mainCodeSuccess = fixMainCodeErrors();
        
        // フェーズ2: テストコードの修正
        System.out.println("\n===== Phase 2: Test Code =====");
        boolean testCodeSuccess = fixTestCodeErrors();
        
        // テスト実行とメトリクス収集
        System.out.println("\n===== Running Tests =====");
        FixMetrics finalMetrics = collectFinalMetrics();
        
        // プログラム終了時刻を記録し、全体の実行時間を計算
        long programEndTime = System.currentTimeMillis();
        double totalExecutionTime = (programEndTime - programStartTime) / 1000.0;
        finalMetrics.setExecutionTime(totalExecutionTime);
        
        System.out.println("\nTotal program execution time: " + String.format("%.2f", totalExecutionTime) + " seconds");
        
        // CSV出力(最終結果のみ)
        List<FixMetrics> finalMetricsList = new ArrayList<>();
        finalMetricsList.add(finalMetrics);
        CsvWriter.writeMetrics(finalMetricsList);
        
        // 最終結果の判定とディレクトリ移動
        boolean overallSuccess = mainCodeBuildSuccess && testCodeBuildSuccess;
        
        if (overallSuccess) {
            System.out.println("\n===== BUILD SUCCESS: Both Main and Test Code Compiled Successfully =====");
            moveToResultDirectory("success");
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
     * @param resultType "success" または "failure"
     */
    private void moveToResultDirectory(String resultType) {
        try {
            if (ApplicationConfig.SHA == null || ApplicationConfig.SHA.isEmpty()) {
                System.out.println("警告: SHAが設定されていないため、ディレクトリ移動をスキップします");
                return;
            }
            
            // 移動元: /output/SHA
            File sourceDir = new File(ApplicationConfig.OUTPUT_DIR, ApplicationConfig.SHA);
            
            // 移動先: /output/result/success/SHA または /output/result/failure/SHA
            File resultBaseDir = new File(ApplicationConfig.OUTPUT_DIR, "result");
            File resultTypeDir = new File(resultBaseDir, resultType);
            File destinationDir = new File(resultTypeDir, ApplicationConfig.SHA);
            
            // result/success または result/failure ディレクトリを作成
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

            // 各エラーファイルに対して修正処理を実行
            for (CompilationError compilationError : mainErrorFiles.values()) {
                File file = new File(compilationError.getFilePath());
                
                // ファイルが存在しない場合はスキップ
                if (!file.exists()) {
                    continue;
                }

                // ファイルを修正（ループ番号を渡す）
                int deletedLines = sourceCodeFixer.fixErrorFile(file, compilationError, currentLoopNumber);
                if (deletedLines > 0) {
                    mainCodeDeletedLines += deletedLines;
                    modifiedMainFiles.add(compilationError.getFileName());
                }
            }

            // 累積データを更新
            totalMainDeletedLines += mainCodeDeletedLines;
            allModifiedMainFiles.addAll(modifiedMainFiles);
            totalIterations = iteration;

            // 修正されたファイルがない場合は処理を終了
            if (modifiedMainFiles.isEmpty()) {
                break;
            }

            iteration++;
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
            Set<String> modifiedTestFiles = new HashSet<>();

            // 各エラーファイルに対して修正処理を実行
            for (CompilationError compilationError : testErrorFiles.values()) {
                File file = new File(compilationError.getFilePath());
                
                // ファイルが存在しない場合はスキップ
                if (!file.exists()) {
                    continue;
                }

                // ファイルを修正（ループ番号を渡す）
                int deletedLines = sourceCodeFixer.fixErrorFile(file, compilationError, currentLoopNumber);
                if (deletedLines > 0) {
                    testCodeDeletedLines += deletedLines;
                    modifiedTestFiles.add(compilationError.getFileName());
                }
            }

            // 累積データを更新
            totalTestDeletedLines += testCodeDeletedLines;
            allModifiedTestFiles.addAll(modifiedTestFiles);
            totalIterations = Math.max(totalIterations, iteration);

            // 修正されたファイルがない場合は処理を終了
            if (modifiedTestFiles.isEmpty()) {
                break;
            }

            iteration++;
        }
        
        return success;
    }
    
    /**
     * 最終的なメトリクスを収集(テスト実行結果を含む)
     * 注: 実行時間はrun()メソッドで後から設定される
     * @return 最終メトリクス
     * @throws Exception テスト実行時のエラー
     */
    private FixMetrics collectFinalMetrics() throws Exception {
        FixMetrics finalMetrics = new FixMetrics();
        
        // 総反復回数を記録
        finalMetrics.setIteration(totalIterations);
        
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
        
        for (String fileName : allModifiedMainFiles) {
            finalMetrics.addModifiedMainFile(fileName);
        }
        for (String fileName : allModifiedTestFiles) {
            finalMetrics.addModifiedTestFile(fileName);
        }
        
        // テストを実行してテスト結果を収集
        commandExecutor.runTests(finalMetrics);
        
        return finalMetrics;
    }
}