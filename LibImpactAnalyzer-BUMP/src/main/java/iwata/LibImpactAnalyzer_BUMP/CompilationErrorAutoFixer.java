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
        
        CompilationErrorAutoFixer autoFixer = new CompilationErrorAutoFixer();
        autoFixer.run();
    }
    
    /**
     * メイン処理
     * フェーズ1(メインコード)とフェーズ2(テストコード)を順次実行
     * @throws Exception 処理中のエラー
     */
    public void run() throws Exception {
        // フェーズ1: メインコードの修正
        System.out.println("===== Phase 1: Main Code =====");
        boolean mainCodeSuccess = fixMainCodeErrors();
        
        // フェーズ2: テストコードの修正
        System.out.println("\n===== Phase 2: Test Code =====");
        boolean testCodeSuccess = fixTestCodeErrors();
        
        // テスト実行とメトリクス収集
        System.out.println("\n===== Running Tests =====");
        FixMetrics finalMetrics = collectFinalMetrics();
        
        // CSV出力(最終結果のみ)
        List<FixMetrics> finalMetricsList = new ArrayList<>();
        finalMetricsList.add(finalMetrics);
        CsvWriter.writeMetrics(finalMetricsList);
        
        // 最終結果の表示
        if (mainCodeSuccess && testCodeSuccess) {
            System.out.println("\n===== Compilation Successful =====");
        } else {
            System.out.println("\n===== Max iterations reached. Unresolved errors remain. =====");
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
            Map<String, CompilationError> mainErrorFiles = commandExecutor.compileMainCode();
            
            // エラーがなければ成功
            if (mainErrorFiles.isEmpty()) {
                System.out.println("Main code compilation successful");
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
            Map<String, CompilationError> testErrorFiles = commandExecutor.compileTestCode();
            
            // エラーがなければ成功
            if (testErrorFiles.isEmpty()) {
                System.out.println("Test code compilation successful");
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