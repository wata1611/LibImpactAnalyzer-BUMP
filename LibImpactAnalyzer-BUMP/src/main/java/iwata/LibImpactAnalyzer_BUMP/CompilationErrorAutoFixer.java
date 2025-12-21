package iwata.LibImpactAnalyzer_BUMP;

import java.io.File;
import java.util.*;

/**
 * コンパイルエラー自動修正ツール（メインクラス）
 * 
 * 処理の流れ:
 * 1. フェーズ1: メインコード（srcディレクトリ）のコンパイルエラーを自動修正
 * 2. フェーズ2: テストコード（testsディレクトリ）のコンパイルエラーを自動修正
 * 3. テスト実行とメトリクス収集
 * 4. CSV出力
 * 
 * 各フェーズでは最大MAX_ITERATIONS回まで繰り返し修正を試みる
 * マルチモジュールプロジェクトにも対応
 */
public class CompilationErrorAutoFixer {
    
    /** Mavenコマンド実行（コンパイル実行とエラー抽出） */
    private final MavenCommandExecutor commandExecutor;
    
    /** ソースコード修正（ファイルの自動修正処理） */
    private final SourceCodeFixer sourceCodeFixer;
    
    /** メトリクスデータのリスト */
    private final List<FixMetrics> metricsList;
    
    /**
     * コンストラクタ
     * 必要なコンポーネントを初期化
     */
    public CompilationErrorAutoFixer() {
        this.commandExecutor = new MavenCommandExecutor();
        this.sourceCodeFixer = new SourceCodeFixer();
        this.metricsList = new ArrayList<>();
    }
    
    /**
     * アプリケーションのエントリーポイント
     * @param args コマンドライン引数（未使用）
     * @throws Exception 処理中のエラー
     */
    public static void main(String[] args) throws Exception {
        // プロジェクト構成の初期化（マルチモジュール検出）
        ApplicationConfig.initialize();
        
        CompilationErrorAutoFixer autoFixer = new CompilationErrorAutoFixer();
        autoFixer.run();
    }
    
    /**
     * メイン処理
     * フェーズ1（メインコード）とフェーズ2（テストコード）を順次実行
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
        metricsList.add(finalMetrics);
        
        // CSV出力
        CsvWriter.writeMetrics(metricsList);
        
        // 最終結果の表示
        if (mainCodeSuccess && testCodeSuccess) {
            System.out.println("\n===== Compilation Successful =====");
        } else {
            System.out.println("\n===== Max iterations reached. Unresolved errors remain. =====");
        }
    }
    
    /**
     * フェーズ1: メインコード（srcディレクトリ）のエラー修正処理
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
            System.out.println("\n--- Main Code Loop: " + iteration + " ---");

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

                // ファイルを修正
                int deletedLines = sourceCodeFixer.fixErrorFile(file, compilationError);
                if (deletedLines > 0) {
                    mainCodeDeletedLines += deletedLines;
                    modifiedMainFiles.add(compilationError.getFileName());
                }
            }

            // メトリクスを記録
            FixMetrics metrics = new FixMetrics();
            metrics.setIteration(iteration);
            metrics.setMainCodeTotalLines(mainCodeTotalLines);
            metrics.setMainCodeDeletedLines(mainCodeDeletedLines);
            metrics.setMainCodeModifiedFiles(modifiedMainFiles.size());
            for (String fileName : modifiedMainFiles) {
                metrics.addModifiedMainFile(fileName);
            }
            metricsList.add(metrics);

            // 修正されたファイルがない場合は処理を終了
            if (modifiedMainFiles.isEmpty()) {
                break;
            }

            iteration++;
        }
        
        return success;
    }
    
    /**
     * フェーズ2: テストコード（testsディレクトリ）のエラー修正処理
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
            System.out.println("\n--- Test Code Loop: " + iteration + " ---");

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

                // ファイルを修正
                int deletedLines = sourceCodeFixer.fixErrorFile(file, compilationError);
                if (deletedLines > 0) {
                    testCodeDeletedLines += deletedLines;
                    modifiedTestFiles.add(compilationError.getFileName());
                }
            }

            // メトリクスを記録（既存のメトリクスに追加または新規作成）
            FixMetrics metrics;
            if (!metricsList.isEmpty() && metricsList.get(metricsList.size() - 1).getIteration() == iteration) {
                // メインコードと同じ反復の場合は既存のメトリクスを更新
                metrics = metricsList.get(metricsList.size() - 1);
            } else {
                // 新規メトリクスを作成
                metrics = new FixMetrics();
                metrics.setIteration(iteration);
                metricsList.add(metrics);
            }
            
            metrics.setTestCodeTotalLines(testCodeTotalLines);
            metrics.setTestCodeDeletedLines(testCodeDeletedLines);
            metrics.setTestCodeModifiedFiles(modifiedTestFiles.size());
            for (String fileName : modifiedTestFiles) {
                metrics.addModifiedTestFile(fileName);
            }

            // 修正されたファイルがない場合は処理を終了
            if (modifiedTestFiles.isEmpty()) {
                break;
            }

            iteration++;
        }
        
        return success;
    }
    
    /**
     * 最終的なメトリクスを収集（テスト実行結果を含む）
     * @return 最終メトリクス
     * @throws Exception テスト実行時のエラー
     */
    private FixMetrics collectFinalMetrics() throws Exception {
        FixMetrics finalMetrics = new FixMetrics();
        
        // 反復回数は最後のメトリクスから取得
        if (!metricsList.isEmpty()) {
            finalMetrics.setIteration(metricsList.get(metricsList.size() - 1).getIteration());
        }
        
        // 最終的なコード行数を記録
        int mainCodeTotalLines = LineCounter.countTotalLines(ApplicationConfig.getAllSrcDirs());
        int testCodeTotalLines = LineCounter.countTotalLines(ApplicationConfig.getAllTestDirs());
        finalMetrics.setMainCodeTotalLines(mainCodeTotalLines);
        finalMetrics.setTestCodeTotalLines(testCodeTotalLines);
        
        // 累積削除行数と修正ファイルを記録
        int totalMainDeleted = 0;
        int totalTestDeleted = 0;
        Set<String> allModifiedMainFiles = new HashSet<>();
        Set<String> allModifiedTestFiles = new HashSet<>();
        
        for (FixMetrics metrics : metricsList) {
            totalMainDeleted += metrics.getMainCodeDeletedLines();
            totalTestDeleted += metrics.getTestCodeDeletedLines();
            allModifiedMainFiles.addAll(metrics.getModifiedMainFiles());
            allModifiedTestFiles.addAll(metrics.getModifiedTestFiles());
        }
        
        finalMetrics.setMainCodeDeletedLines(totalMainDeleted);
        finalMetrics.setTestCodeDeletedLines(totalTestDeleted);
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