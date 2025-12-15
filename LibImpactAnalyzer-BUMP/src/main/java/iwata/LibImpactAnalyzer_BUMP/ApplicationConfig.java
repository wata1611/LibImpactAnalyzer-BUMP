package iwata.LibImpactAnalyzer_BUMP;

/**
 * アプリケーション設定を管理するクラス
 * コンパイルエラー修正処理に必要な設定値を定義
 */
public class ApplicationConfig {
    /** 最大反復回数（コンパイルエラー修正の試行上限） */
    public static final int MAX_ITERATIONS = 20;
    
    /** 対象プロジェクトのルートディレクトリ */
    public static final String PROJECT_DIR = "C:\\Users\\cyber\\git\\delete_test\\delete_test";
    
    /** メインコードのソースディレクトリ */
    public static final String SRC_DIR = PROJECT_DIR + "\\src";
    
    /** テストコードのソースディレクトリ */
    public static final String TEST_DIR = PROJECT_DIR + "\\tests";
    
    /**
     * OSに応じた適切なMavenコマンドを取得
     * @return Windows環境では "mvn.cmd"、それ以外では "mvn"
     */
    public static String getMavenCmd() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? "mvn.cmd" : "mvn";
    }
}
