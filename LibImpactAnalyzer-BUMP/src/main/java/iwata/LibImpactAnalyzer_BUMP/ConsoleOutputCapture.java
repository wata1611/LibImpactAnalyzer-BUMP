package iwata.LibImpactAnalyzer_BUMP;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * コンソール出力をキャプチャしてファイルに保存するユーティリティクラス
 * System.outとSystem.errの両方をキャプチャし、txtファイルに出力する
 */
public class ConsoleOutputCapture {
    
    private static PrintStream originalOut;
    private static PrintStream originalErr;
    private static File outputFile;
    private static PrintStream fileOut;
    private static TeeOutputStream teeOut;
    private static TeeOutputStream teeErr;
    
    /**
     * コンソール出力のキャプチャを開始
     * SHA名ディレクトリ配下にconsole_output.txtを作成
     */
    public static void start() {
        try {
            // 元のストリームを保存
            originalOut = System.out;
            originalErr = System.err;
            
            // 出力ファイルのパスを構築
            String shaDir = ApplicationConfig.OUTPUT_DIR;
            if (ApplicationConfig.SHA != null && !ApplicationConfig.SHA.isEmpty()) {
                shaDir = ApplicationConfig.OUTPUT_DIR + "/" + ApplicationConfig.SHA;
            }
            
            // SHAディレクトリが存在しない場合は作成
            File shaDirFile = new File(shaDir);
            if (!shaDirFile.exists()) {
                shaDirFile.mkdirs();
            }
            
            // 出力ファイルを作成
            outputFile = new File(shaDir, "console_output.txt");
            fileOut = new PrintStream(new FileOutputStream(outputFile), true, StandardCharsets.UTF_8);
            
            // TeeOutputStreamを使用してコンソールとファイルの両方に出力
            teeOut = new TeeOutputStream(originalOut, fileOut);
            teeErr = new TeeOutputStream(originalErr, fileOut);
            
            System.setOut(new PrintStream(teeOut, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(teeErr, true, StandardCharsets.UTF_8));
            
            System.out.println("=== コンソール出力のキャプチャを開始 ===");
            System.out.println("出力ファイル: " + outputFile.getAbsolutePath());
            System.out.println();
            
        } catch (Exception e) {
            System.err.println("コンソール出力のキャプチャに失敗しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * コンソール出力のキャプチャを終了
     */
    public static void stop() {
        try {
            if (originalOut != null && originalErr != null) {
                System.out.println();
                System.out.println("=== コンソール出力のキャプチャを終了 ===");
                
                // 元のストリームに戻す
                System.setOut(originalOut);
                System.setErr(originalErr);
                
                // ファイルストリームをクローズ
                if (fileOut != null) {
                    fileOut.close();
                }
                
                System.out.println("コンソール出力を保存しました: " + outputFile.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("コンソール出力のキャプチャ終了時にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 2つのOutputStreamに同時に書き込むためのクラス
     */
    private static class TeeOutputStream extends OutputStream {
        private final OutputStream out1;
        private final OutputStream out2;
        
        public TeeOutputStream(OutputStream out1, OutputStream out2) {
            this.out1 = out1;
            this.out2 = out2;
        }
        
        @Override
        public void write(int b) throws IOException {
            out1.write(b);
            out2.write(b);
        }
        
        @Override
        public void write(byte[] b) throws IOException {
            out1.write(b);
            out2.write(b);
        }
        
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out1.write(b, off, len);
            out2.write(b, off, len);
        }
        
        @Override
        public void flush() throws IOException {
            out1.flush();
            out2.flush();
        }
        
        @Override
        public void close() throws IOException {
            try {
                out1.close();
            } finally {
                out2.close();
            }
        }
    }
}