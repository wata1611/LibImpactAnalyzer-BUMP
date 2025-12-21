package iwata.LibImpactAnalyzer_BUMP;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Javaファイル検索のユーティリティクラス
 * プロジェクト内のJavaファイルを検索する機能を提供
 * マルチモジュールプロジェクトにも対応
 */
public class JavaFileSearcher {
    
    /**
     * プロジェクト内でJavaファイルを検索
     * srcディレクトリとtestsディレクトリの両方を検索対象とする
     * マルチモジュールプロジェクトの場合は全モジュールを検索
     * 
     * @param fileName 検索するファイル名（例: "Example.java"）
     * @return ファイルの完全パス。見つからない場合はnull
     */
    public static String findJavaFile(String fileName) {
        // まずsrcディレクトリで検索
        for (String srcDir : ApplicationConfig.getAllSrcDirs()) {
            String foundFile = findJavaFileInDirectory(fileName, srcDir);
            if (foundFile != null) {
                return foundFile;
            }
        }
        
        // srcで見つからなければtestsディレクトリで検索
        for (String testDir : ApplicationConfig.getAllTestDirs()) {
            String foundFile = findJavaFileInDirectory(fileName, testDir);
            if (foundFile != null) {
                return foundFile;
            }
        }
        
        return null;
    }
    
    /**
     * 指定されたディレクトリ内でJavaファイルを再帰的に検索
     * 
     * @param fileName 検索するファイル名
     * @param directory 検索対象のディレクトリパス
     * @return ファイルの完全パス。見つからない場合はnull
     */
    public static String findJavaFileInDirectory(String fileName, String directory) {
        try {
            Path dirPath = Paths.get(directory);
            
            // ディレクトリが存在しない場合はnullを返す
            if (!Files.exists(dirPath)) {
                return null;
            }

            // ディレクトリ内を再帰的に走査してファイル名が一致するJavaファイルを検索
            List<Path> foundFiles = Files.walk(dirPath)
                .filter(Files::isRegularFile)  // 通常ファイルのみ対象
                .filter(path -> path.getFileName().toString().equals(fileName))  // ファイル名が一致
                .collect(Collectors.toList());

            // 見つからなかった場合
            if (foundFiles.isEmpty()) {
                return null;
            }

            // 最初に見つかったファイルのパスを文字列で返す
            return foundFiles.get(0).toString();
            
        } catch (IOException e) {
            // エラーが発生した場合はnullを返す
            return null;
        }
    }
}