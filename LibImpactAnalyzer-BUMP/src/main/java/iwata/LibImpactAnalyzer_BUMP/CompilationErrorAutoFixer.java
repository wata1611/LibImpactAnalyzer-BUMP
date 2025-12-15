package iwata.LibImpactAnalyzer_BUMP;

import java.io.File;
import java.util.Map;

/**
 * コンパイルエラー自動修正ツール（メインクラス）
 * 
 * 処理の流れ:
 * 1. フェーズ1: メインコード（srcディレクトリ）のコンパイルエラーを自動修正
 * 2. フェーズ2: テストコード（testsディレクトリ）のコンパイルエラーを自動修正
 * 
 * 各フェーズでは最大MAX_ITERATIONS回まで繰り返し修正を試みる
 */
public class CompilationErrorAutoFixer {
    
    /** Mavenコマンド実行（コンパイル実行とエラー抽出） */
    private final MavenCommandExecutor commandExecutor;
    
    /** ソースコード修正（ファイルの自動修正処理） */
    private final SourceCodeFixer sourceCodeFixer;
    
    /**
     * コンストラクタ
     * 必要なコンポーネントを初期化
     */
    public CompilationErrorAutoFixer() {
        this.commandExecutor = new MavenCommandExecutor();
        this.sourceCodeFixer = new SourceCodeFixer();
    }
    
    /**
     * アプリケーションのエントリーポイント
     * @param args コマンドライン引数（未使用）
     * @throws Exception 処理中のエラー
     */
    public static void main(String[] args) throws Exception {
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

            boolean anyModified = false;

            // 各エラーファイルに対して修正処理を実行
            for (CompilationError compilationError : mainErrorFiles.values()) {
                File file = new File(compilationError.getFilePath());
                
                // ファイルが存在しない場合はスキップ
                if (!file.exists()) {
                    continue;
                }

                // ファイルを修正
                boolean modified = sourceCodeFixer.fixErrorFile(file, compilationError);
                if (modified) {
                    anyModified = true;
                }
            }

            // 修正されたファイルがない場合は処理を終了
            if (!anyModified) {
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

            boolean anyModified = false;

            // 各エラーファイルに対して修正処理を実行
            for (CompilationError compilationError : testErrorFiles.values()) {
                File file = new File(compilationError.getFilePath());
                
                // ファイルが存在しない場合はスキップ
                if (!file.exists()) {
                    continue;
                }

                // ファイルを修正
                boolean modified = sourceCodeFixer.fixErrorFile(file, compilationError);
                if (modified) {
                    anyModified = true;
                }
            }

            // 修正されたファイルがない場合は処理を終了
            if (!anyModified) {
                break;
            }

            iteration++;
        }
        
        return success;
    }
}
