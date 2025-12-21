package iwata.LibImpactAnalyzer_BUMP;

import java.util.*;

/**
 * アプリケーション設定を管理するクラス
 * コンパイルエラー修正処理に必要な設定値を定義
 */
public class ApplicationConfig {
    /** 最大反復回数（コンパイルエラー修正の試行上限） */
    public static final int MAX_ITERATIONS = 20;
    
    /** 対象プロジェクトのルートディレクトリ */
    public static final String PROJECT_DIR = "C:\\Users\\cyber\\git\\wicket-crudifier";
    
    /** メインコードのソースディレクトリ（シングルモジュール用） */
    public static final String SRC_DIR = PROJECT_DIR + "\\src\\main\\java";
    
    /** テストコードのソースディレクトリ（シングルモジュール用） */
    public static final String TEST_DIR = PROJECT_DIR + "\\src\\test\\java";
    
    /** マルチモジュールプロジェクトのモジュール情報 */
    private static List<ModuleInfo> modules = null;
    
    /** マルチモジュールプロジェクトかどうか */
    private static boolean isMultiModule = false;
    
    /**
     * プロジェクト構成を初期化
     * pom.xmlを解析してマルチモジュールかどうかを判定
     */
    public static void initialize() {
        modules = PomParser.parseModules(PROJECT_DIR);
        isMultiModule = !modules.isEmpty();
        
        if (isMultiModule) {
            System.out.println("マルチモジュールモードで動作します");
        } else {
            System.out.println("シングルモジュールモードで動作します");
        }
    }
    
    /**
     * マルチモジュールプロジェクトかどうかを確認
     * @return マルチモジュールの場合true
     */
    public static boolean isMultiModule() {
        return isMultiModule;
    }
    
    /**
     * モジュール情報のリストを取得
     * @return モジュール情報のリスト
     */
    public static List<ModuleInfo> getModules() {
        return modules != null ? modules : Collections.emptyList();
    }
    
    /**
     * すべてのソースディレクトリのリストを取得
     * @return ソースディレクトリのリスト
     */
    public static List<String> getAllSrcDirs() {
        if (isMultiModule) {
            List<String> dirs = new ArrayList<>();
            for (ModuleInfo module : modules) {
                dirs.add(module.getSrcDir());
            }
            return dirs;
        } else {
            return Collections.singletonList(SRC_DIR);
        }
    }
    
    /**
     * すべてのテストディレクトリのリストを取得
     * @return テストディレクトリのリスト
     */
    public static List<String> getAllTestDirs() {
        if (isMultiModule) {
            List<String> dirs = new ArrayList<>();
            for (ModuleInfo module : modules) {
                dirs.add(module.getTestDir());
            }
            return dirs;
        } else {
            return Collections.singletonList(TEST_DIR);
        }
    }
    
    /**
     * OSに応じた適切なMavenコマンドを取得
     * @return Windows環境では "mvn.cmd"、それ以外では "mvn"
     */
    public static String getMavenCmd() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? "mvn.cmd" : "mvn";
    }
}