package iwata.LibImpactAnalyzer_BUMP;

import java.util.*;

/**
 * アプリケーション設定を管理するクラス
 * コンパイルエラー修正処理に必要な設定値を定義
 */
public class ApplicationConfig {
    /** 最大反復回数(コンパイルエラー修正の試行上限) */
    public static final int MAX_ITERATIONS = 20;
    
    /** 対象プロジェクトのルートディレクトリ */
    public static String PROJECT_DIR;
    
    /** CSV出力先ファイルパス */
    public static String CSV_OUTPUT_PATH = null;
    
    /** SHA値（プロジェクト識別用） */
    public static String SHA = null;
    
    /** 修正ファイル出力先ディレクトリ */
    public static String OUTPUT_DIR;
    
    /** メインコードのソースディレクトリ(シングルモジュール用) */
    public static String SRC_DIR;
    
    /** テストコードのソースディレクトリ(シングルモジュール用) */
    public static String TEST_DIR;
    
    /** マルチモジュールプロジェクトのモジュール情報 */
    private static List<ModuleInfo> modules = null;
    
    /** マルチモジュールプロジェクトかどうか */
    private static boolean isMultiModule = false;
    
    /** 削除されたインポート文を追跡するマップ (ファイルパス -> インポート文 -> 削除回数) */
    private static Map<String, Map<String, Integer>> deletedImportsMap = new HashMap<>();
    
    /**
     * コマンドライン引数から設定を初期化
     * @param args コマンドライン引数
     */
    public static void initializeFromArgs(String[] args) {
        // 引数からCSV出力先パスを取得
        if (args.length > 0) {
            CSV_OUTPUT_PATH = args[0];
            System.out.println("CSV output path: " + CSV_OUTPUT_PATH);
        }
        
        // 環境変数からプロジェクトディレクトリを取得
        String projectRootEnv = System.getenv("PROJECT_ROOT");
        if (projectRootEnv != null && !projectRootEnv.isEmpty()) {
            PROJECT_DIR = projectRootEnv;
            System.out.println("Project directory (from PROJECT_ROOT): " + PROJECT_DIR);
        } else {
            // 環境変数が設定されていない場合はカレントディレクトリを使用
            PROJECT_DIR = System.getProperty("user.dir");
            System.out.println("Project directory (current directory): " + PROJECT_DIR);
        }
        
        // ソースディレクトリのパスを設定
        SRC_DIR = PROJECT_DIR + "/src/main/java";
        TEST_DIR = PROJECT_DIR + "/src/test/java";
        
        // 出力ディレクトリのパスを設定
        // コンテナ環境では /output にマウントされる想定
        // CSV_OUTPUT_PATHが指定されている場合はその親ディレクトリを使用
        if (CSV_OUTPUT_PATH != null && !CSV_OUTPUT_PATH.isEmpty()) {
            java.io.File csvFile = new java.io.File(CSV_OUTPUT_PATH);
            java.io.File parentDir = csvFile.getParentFile();
            if (parentDir != null) {
                OUTPUT_DIR = parentDir.getAbsolutePath();
            } else {
                OUTPUT_DIR = "/output";
            }
            
            // CSV_OUTPUT_PATHのファイル名からSHAを抽出
            // 例: /output/3ff575ae202cdf76ddfa8a4228a1711a6fa1e921.csv -> 3ff575ae202cdf76ddfa8a4228a1711a6fa1e921
            String csvFileName = csvFile.getName();
            if (csvFileName.endsWith(".csv")) {
                SHA = csvFileName.substring(0, csvFileName.length() - 4);
                System.out.println("SHA: " + SHA);
            }
        } else {
            OUTPUT_DIR = "/output";
        }
        System.out.println("Output directory: " + OUTPUT_DIR);
    }
    
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
    
    /**
     * 削除されたインポート文を記録
     * @param filePath ファイルパス
     * @param importStatement インポート文
     */
    public static void recordDeletedImport(String filePath, String importStatement) {
        deletedImportsMap.putIfAbsent(filePath, new HashMap<>());
        Map<String, Integer> importsCount = deletedImportsMap.get(filePath);
        importsCount.put(importStatement, importsCount.getOrDefault(importStatement, 0) + 1);
    }
    
    /**
     * 2回以上削除されたインポート文を取得
     * @param filePath ファイルパス
     * @return 2回以上削除されたインポート文のセット
     */
    public static Set<String> getRepeatedlyDeletedImports(String filePath) {
        if (!deletedImportsMap.containsKey(filePath)) {
            return Collections.emptySet();
        }
        
        Set<String> repeatedImports = new HashSet<>();
        Map<String, Integer> importsCount = deletedImportsMap.get(filePath);
        
        for (Map.Entry<String, Integer> entry : importsCount.entrySet()) {
            if (entry.getValue() >= 2) {
                repeatedImports.add(entry.getKey());
            }
        }
        
        return repeatedImports;
    }
    
}