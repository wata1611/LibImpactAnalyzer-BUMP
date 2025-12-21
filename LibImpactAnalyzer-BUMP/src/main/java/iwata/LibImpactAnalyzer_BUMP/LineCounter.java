package iwata.LibImpactAnalyzer_BUMP;

import java.io.*;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Javaコードの行数をカウントするユーティリティクラス
 */
public class LineCounter {
    
    /**
     * 指定されたディレクトリ配下のすべてのJavaファイルの総行数をカウント
     * @param directories ディレクトリのリスト
     * @return 総行数
     */
    public static int countTotalLines(Iterable<String> directories) {
        int totalLines = 0;
        
        for (String directory : directories) {
            totalLines += countLinesInDirectory(directory);
        }
        
        return totalLines;
    }
    
    /**
     * 指定されたディレクトリ配下のすべてのJavaファイルの行数をカウント
     * @param directory ディレクトリパス
     * @return 総行数
     */
    public static int countLinesInDirectory(String directory) {
        int totalLines = 0;
        
        try {
            Path dirPath = Paths.get(directory);
            
            // ディレクトリが存在しない場合は0を返す
            if (!Files.exists(dirPath)) {
                return 0;
            }
            
            // ディレクトリ内のすべてのJavaファイルを走査
            try (Stream<Path> paths = Files.walk(dirPath)) {
                totalLines = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .mapToInt(LineCounter::countLinesInFile)
                    .sum();
            }
            
        } catch (IOException e) {
            System.err.println("Error counting lines in directory: " + directory + " - " + e.getMessage());
        }
        
        return totalLines;
    }
    
    /**
     * 指定されたファイルの行数をカウント
     * @param filePath ファイルパス
     * @return 行数
     */
    public static int countLinesInFile(Path filePath) {
        try {
            return (int) Files.lines(filePath).count();
        } catch (IOException e) {
            return 0;
        }
    }
}