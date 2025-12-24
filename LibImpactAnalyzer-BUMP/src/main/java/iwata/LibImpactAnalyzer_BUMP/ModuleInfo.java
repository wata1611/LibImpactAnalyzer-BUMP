package iwata.LibImpactAnalyzer_BUMP;

/**
 * モジュール情報を保持するクラス
 * マルチモジュールプロジェクトの各モジュールのディレクトリ情報を管理
 */
public class ModuleInfo {
    /** モジュール名 */
    private String moduleName;
    
    /** モジュールのルートディレクトリ */
    private String moduleDir;
    
    /** モジュールのメインソースディレクトリ */
    private String srcDir;
    
    /** モジュールのテストソースディレクトリ */
    private String testDir;
    
    /**
     * コンストラクタ
     * @param moduleName モジュール名
     * @param moduleDir モジュールのルートディレクトリ
     */
    public ModuleInfo(String moduleName, String moduleDir) {
        this.moduleName = moduleName;
        this.moduleDir = moduleDir;
        this.srcDir = moduleDir + "/src/main/java";
        this.testDir = moduleDir + "/src/test/java";
    }
    
    public String getModuleName() {
        return moduleName;
    }
    
    public String getModuleDir() {
        return moduleDir;
    }
    
    public String getSrcDir() {
        return srcDir;
    }
    
    public String getTestDir() {
        return testDir;
    }
}