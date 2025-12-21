package iwata.LibImpactAnalyzer_BUMP;

import java.util.*;

/**
 * コンパイルエラー情報を保持するクラス
 * 各Javaファイルのエラー行番号とエラー種別を管理
 */
public class CompilationError {
    /** エラーが発生したファイル名 */
    private String fileName;
    
    /** エラーが発生したファイルの完全パス */
    private String filePath;
    
    /** エラーが発生した行番号のセット（重複を避けるためSetを使用） */
    private Set<Integer> errorLines;
    
    /** return文が欠如しているエラーかどうかを示すフラグ */
    private boolean missingReturnStatement = false;
    
    /**
     * コンストラクタ
     * @param fileName ファイル名
     * @param filePath ファイルの完全パス
     */
    public CompilationError(String fileName, String filePath) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.errorLines = new HashSet<>();
    }
    
    // ===== Getters and Setters =====
    
    /**
     * ファイル名を取得
     * @return ファイル名
     */
    public String getFileName() { 
        return fileName; 
    }
    
    /**
     * ファイルの完全パスを取得
     * @return ファイルパス
     */
    public String getFilePath() { 
        return filePath; 
    }
    
    /**
     * エラー行番号のセットを取得
     * @return エラー行番号のセット
     */
    public Set<Integer> getErrorLines() { 
        return errorLines; 
    }
    
    /**
     * エラー行番号を追加
     * @param lineNumber 追加する行番号
     */
    public void addErrorLine(int lineNumber) { 
        this.errorLines.add(lineNumber); 
    }
    
    /**
     * return文欠如エラーかどうかを確認
     * @return return文が欠如している場合true
     */
    public boolean isMissingReturnStatement() { 
        return missingReturnStatement; 
    }
    
    /**
     * return文欠如フラグを設定
     * @param missingReturnStatement return文欠如の場合true
     */
    public void setMissingReturnStatement(boolean missingReturnStatement) { 
        this.missingReturnStatement = missingReturnStatement; 
    }
}