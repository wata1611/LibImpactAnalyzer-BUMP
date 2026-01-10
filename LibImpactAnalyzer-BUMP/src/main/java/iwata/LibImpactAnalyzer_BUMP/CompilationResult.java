package iwata.LibImpactAnalyzer_BUMP;

import java.util.*;

/**
 * コンパイル結果を保持するクラス
 * エラー情報とビルド成功/失敗の情報を管理
 */
public class CompilationResult {
    /** エラーファイルのMap（ファイル名 → CompilationError） */
    private Map<String, CompilationError> errorFiles;
    
    /** ビルドが成功したかどうか */
    private boolean buildSuccess;
    
    /**
     * コンストラクタ
     * @param errorFiles エラーファイルのMap
     * @param buildSuccess ビルド成功フラグ
     */
    public CompilationResult(Map<String, CompilationError> errorFiles, boolean buildSuccess) {
        this.errorFiles = errorFiles;
        this.buildSuccess = buildSuccess;
    }
    
    /**
     * エラーファイルのMapを取得
     * @return エラーファイルのMap
     */
    public Map<String, CompilationError> getErrorFiles() {
        return errorFiles;
    }
    
    /**
     * ビルドが成功したかどうかを取得
     * @return ビルド成功の場合true
     */
    public boolean isBuildSuccess() {
        return buildSuccess;
    }
}