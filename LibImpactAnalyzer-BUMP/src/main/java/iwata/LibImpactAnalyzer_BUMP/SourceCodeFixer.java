package iwata.LibImpactAnalyzer_BUMP;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.cu.CompilationUnit;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * ソースコード自動修正クラス
 * Spoonライブラリを使用してJavaソースコードのASTを操作し、
 * コンパイルエラー箇所を自動的に修正する
 */
public class SourceCodeFixer {
    
    /**
     * エラーが発生したファイルを修正する
     * 
     * @param file 修正対象のファイル
     * @param compilationError エラー情報(エラー行番号など)
     * @return 削除された行数
     */
    public int fixErrorFile(File file, CompilationError compilationError) {
        try {
            // ファイル修正開始のヘッダー表示
            System.out.println("\n--- " + compilationError.getFileName() + " の修正処理開始 ---");
            
            // 2回以上削除されたインポートを事前に削除
            removeRepeatedlyDeletedImports(file, compilationError.getFilePath());
            
            // ファイルがテストコードかどうかを判定(test/javaディレクトリに含まれるか)
            boolean isTestFile = compilationError.getFilePath().contains("\\test\\java\\") || 
                                compilationError.getFilePath().contains("/test/java/");

            // Spoon Launcherの初期化と設定
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);  // classpathを使用しない
            launcher.getEnvironment().setAutoImports(true);  // 自動インポート有効化
            launcher.getEnvironment().setPrettyPrinterCreator(
                () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())  // コード整形設定
            );
            launcher.addInputResource(file.getAbsolutePath());  // 対象ファイルを追加
            launcher.buildModel();  // ASTモデルを構築
            CtModel model = launcher.getModel();

            boolean modified = false;
            int deletedElementCount = 0;

            if (isTestFile) {
                // テストコードの場合: エラーメソッドの本体を削除してAssert.failを挿入
                int count = fixTestFile(launcher, model, compilationError);
                if (count > 0) {
                    modified = true;
                    deletedElementCount = count;
                }
            } else {
                // メインコードの場合
                if (compilationError.isMissingReturnStatement()) {
                    // return文欠如エラー: メソッドの末尾にreturn文を追加
                    int count = fixMissingReturnStatement(launcher, model, compilationError);
                    if (count > 0) {
                        modified = true;
                        deletedElementCount = count;
                    }
                } else {
                    // 通常のエラー: エラー行の要素を削除
                    for (int lineNum : compilationError.getErrorLines()) {
                        // 指定された行番号の要素を検索
                        List<CtElement> targetNodes = model.getElements(e -> {
                            SourcePosition pos = e.getPosition();
                            return pos != null && pos.isValidPosition() && pos.getLine() == lineNum;
                        });

                        // 見つかった要素を削除
                        for (CtElement element : targetNodes) {
                            // 削除する要素の詳細を出力
                            System.out.println("削除対象要素: " + element.getClass().getSimpleName() + " - " + element.getShortRepresentation());
                            element.delete();
                            modified = true;
                            deletedElementCount++;
                        }
                    }
                }
            }

            // import文の処理とファイル保存
            int importCount = removeErrorImportsAndSave(launcher, file, compilationError, modified, deletedElementCount);
            if (importCount > 0) {
                modified = true;
                deletedElementCount += importCount;
            }

            // 修正完了メッセージ
            if (modified) {
                System.out.println("削除された行数: " + deletedElementCount);
                System.out.println("修正完了: [" + (isTestFile ? "TEST" : "MAIN") + "] " + compilationError.getFileName());
            } else {
                System.out.println("修正不要: " + compilationError.getFileName());
            }

            return deletedElementCount;

        } catch (Exception e) {
            System.err.println("Error processing file: " + compilationError.getFileName() + " - " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * 2回以上削除されたインポート文を事前に削除
     * 
     * @param file 対象ファイル
     * @param filePath ファイルパス
     * @throws IOException ファイル読み書きエラー
     */
    private void removeRepeatedlyDeletedImports(File file, String filePath) throws IOException {
        Set<String> repeatedImports = ApplicationConfig.getRepeatedlyDeletedImports(filePath);
        
        if (repeatedImports.isEmpty()) {
            return;
        }
        
        System.out.println("事前削除対象のインポート数: " + repeatedImports.size());
        
        // ファイルの内容を読み込み
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        List<String> newLines = new ArrayList<>();
        boolean fileModified = false;
        
        // import文のパターン
        Pattern importPattern = Pattern.compile("^\\s*import\\s+(.+);\\s*$");
        
        for (String line : lines) {
            Matcher matcher = importPattern.matcher(line);
            
            if (matcher.matches()) {
                String importStatement = matcher.group(1).trim();
                
                // 繰り返し削除されたインポートかチェック
                if (repeatedImports.contains(importStatement)) {
                    System.out.println("事前削除: import " + importStatement + ";");
                    fileModified = true;
                    continue;  // この行はスキップ(削除)
                }
            }
            
            newLines.add(line);
        }
        
        // ファイルが修正された場合、書き戻す
        if (fileModified) {
            Files.write(file.toPath(), newLines, StandardCharsets.UTF_8);
            System.out.println("繰り返し削除されたインポートを事前削除しました");
        }
    }
    
    /**
     * return文欠如エラーの修正
     * エラー行を含むメソッドの末尾にデフォルトのreturn文を追加
     * 
     * @param launcher Spoon Launcher
     * @param model ASTモデル
     * @param compilationError エラー情報
     * @return 修正された要素数
     */
    private int fixMissingReturnStatement(Launcher launcher, CtModel model, CompilationError compilationError) {
        int modifiedCount = 0;
        Set<CtMethod<?>> processedMethods = new HashSet<>();  // 処理済みメソッドを追跡
        
        // モデル内の全メソッドを取得
        List<CtMethod<?>> methods = model.getElements(e -> e instanceof CtMethod<?>).stream()
                .map(e -> (CtMethod<?>) e)
                .collect(Collectors.toList());
        
        // 各エラー行に対して処理
        for (int lineNum : compilationError.getErrorLines()) {
            for (CtMethod<?> method : methods) {
                
                // すでに処理済みのメソッドはスキップ
                if (processedMethods.contains(method)) {
                    continue;
                }
                
                SourcePosition methodPos = method.getPosition();
                if (methodPos != null && methodPos.isValidPosition()) {
                    int startLine = methodPos.getLine();
                    int endLine = methodPos.getEndLine();
                    
                    // エラー行がメソッド内にあるかチェック
                    if (lineNum >= startLine && lineNum <= endLine) {
                        // メソッドの戻り値型を取得
                        CtTypeReference<?> returnType = method.getType();
                        
                        // void以外のメソッドの場合
                        if (returnType != null && !returnType.getSimpleName().equals("void")) {
                            CtBlock<?> body = method.getBody();
                            if (body != null) {
                                // デフォルトのreturn文を作成
                                CtReturn<Object> returnStmt = launcher.getFactory().Core().createReturn();
                                CtExpression<Object> defaultValue = createDefaultReturnValue(launcher, returnType);
                                returnStmt.setReturnedExpression(defaultValue);
                                
                                // メソッド本体の末尾にreturn文を追加
                                System.out.println("return文追加: メソッド " + method.getSimpleName() + " (行 " + lineNum + ")");
                                body.addStatement(returnStmt);
                                processedMethods.add(method);
                                modifiedCount++;
                            }
                        }
                    }
                }
            }
        }
        
        return modifiedCount;
    }
    
    /**
     * テストファイルのエラー修正
     * エラー行を含むメソッドの本体を削除し、Assert.fail文を挿入
     * 
     * @param launcher Spoon Launcher
     * @param model ASTモデル
     * @param compilationError エラー情報
     * @return 修正された要素数
     */
    private int fixTestFile(Launcher launcher, CtModel model, CompilationError compilationError) {
        int modifiedCount = 0;
        Set<CtMethod<?>> processedMethods = new HashSet<>();  // 処理済みメソッドを追跡
        
        // モデル内の全メソッドを取得
        List<CtMethod<?>> methods = model.getElements(e -> e instanceof CtMethod<?>).stream()
                .map(e -> (CtMethod<?>) e)
                .collect(Collectors.toList());
        
        // 各エラー行に対して処理
        for (int lineNum : compilationError.getErrorLines()) {
            for (CtMethod<?> method : methods) {
                
                // すでに処理済みのメソッドはスキップ
                if (processedMethods.contains(method)) {
                    continue;
                }
                
                SourcePosition methodPos = method.getPosition();
                if (methodPos != null && methodPos.isValidPosition()) {
                    int startLine = methodPos.getLine();
                    int endLine = methodPos.getEndLine();
                    
                    // エラー行がメソッド内にあるかチェック
                    if (lineNum >= startLine && lineNum <= endLine) {
                        // メソッド本体を取得または作成
                        CtBlock<?> body = method.getBody();
                        if (body != null) {
                            int statementCount = body.getStatements().size();
                            System.out.println("テストメソッド本体をクリア: " + method.getSimpleName() + " (削除ステートメント数: " + statementCount + ")");
                            body.getStatements().clear();  // 既存のステートメントをクリア
                            modifiedCount += statementCount;
                        } else {
                            body = launcher.getFactory().Core().createBlock();
                            method.setBody(body);
                        }
                        
                        // Assert.fail文を挿入(試行)
                        try {
                            CtStatement failStatement = launcher.getFactory().Code()
                                .createCodeSnippetStatement(
                                    "org.junit.Assert.fail(\"[LIB-REMOVED] このテストは削除対象ライブラリ依存のため失敗扱い\")"
                                );
                            body.addStatement(failStatement);
                            System.out.println("Assert.fail文を挿入: " + method.getSimpleName());
                        } catch (Exception e) {
                            // Assert.fail挿入失敗時はRuntimeExceptionをスロー
                            CtStatement throwStatement = launcher.getFactory().Code()
                                .createCodeSnippetStatement(
                                    "throw new RuntimeException(\"[LIB-REMOVED] このテストは削除対象ライブラリ依存のため失敗扱い\")"
                                );
                            body.addStatement(throwStatement);
                            System.out.println("RuntimeException文を挿入: " + method.getSimpleName());
                        }
                        
                        processedMethods.add(method);
                        modifiedCount++;
                    }
                }
            }
        }
        
        return modifiedCount;
    }
    
    /**
     * エラー行のimport文を削除してファイルに保存
     * エラー行に該当するimport文を削除し、修正後のコードをファイルに保存
     * 
     * @param launcher Spoon Launcher
     * @param file 保存対象のファイル
     * @param compilationError エラー情報
     * @param alreadyModified すでに修正が行われているかどうか
     * @param elementCount これまでに削除された要素数
     * @return 削除されたimport文の数
     * @throws IOException ファイル書き込みエラー
     */
    private int removeErrorImportsAndSave(Launcher launcher, File file, CompilationError compilationError,
                                          boolean alreadyModified, int elementCount) throws IOException {
        int importCount = 0;
        
        // 対象ファイルのCompilationUnitを取得
        CompilationUnit targetUnit = launcher.getFactory().CompilationUnit().getMap().values().stream()
                .filter(cu -> cu.getFile() != null && 
                            cu.getFile().getName().equals(compilationError.getFileName()))
                .findFirst()
                .orElse(null);

        if (targetUnit != null) {
            // エラー行に該当するimport文を検索
            List<CtImport> importsToRemove = new ArrayList<>();
            for (CtImport ctImport : targetUnit.getImports()) {
                SourcePosition pos = ctImport.getPosition();
                if (pos != null && pos.isValidPosition() && 
                    compilationError.getErrorLines().contains(pos.getLine())) {
                    String importStatement = ctImport.toString().trim();
                    System.out.println("削除対象import文: " + importStatement);
                    
                    // インポート文を記録(統計用)
                    String importedType = extractImportedType(importStatement);
                    if (importedType != null) {
                        ApplicationConfig.recordDeletedImport(compilationError.getFilePath(), importedType);
                    }
                    
                    importsToRemove.add(ctImport);
                    importCount++;
                }
            }
            
            // 該当するimport文を削除
            targetUnit.getImports().removeAll(importsToRemove);

            // 修正が行われた場合、ファイルに保存
            if (alreadyModified || importCount > 0) {
                String result = targetUnit.prettyprint();  // ASTからソースコードを生成
                try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
                    writer.print(result);
                }
            }
        }
        
        return importCount;
    }
    
    /**
     * import文から実際のインポート対象を抽出
     * 例: "import java.util.List;" -> "java.util.List"
     * 
     * @param importStatement import文の文字列
     * @return インポート対象のクラス/パッケージ名
     */
    private String extractImportedType(String importStatement) {
        // "import " と ";" を除去
        Pattern pattern = Pattern.compile("import\\s+(?:static\\s+)?([^;]+);?");
        Matcher matcher = pattern.matcher(importStatement);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return null;
    }
    
    /**
     * 戻り値型に応じたデフォルト値を生成
     * プリミティブ型にはそれぞれのデフォルト値、参照型にはnullを返す
     * 
     * @param launcher Spoon Launcher
     * @param returnType 戻り値の型
     * @return デフォルト値を表すCtExpression
     */
    private CtExpression<Object> createDefaultReturnValue(Launcher launcher, CtTypeReference<?> returnType) {
        String typeStr = returnType.getSimpleName();
        
        // 型に応じてデフォルト値を返す
        switch (typeStr) {
            case "boolean":
                return launcher.getFactory().Code().createLiteral(false);
            case "char":
                return launcher.getFactory().Code().createLiteral('\0');
            case "byte":
            case "short":
            case "int":
            case "long":
            case "float":
            case "double":
                return launcher.getFactory().Code().createLiteral(0);
            default:
                // 参照型の場合はnull
                return launcher.getFactory().Code().createLiteral(null);
        }
    }
}