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
import java.util.*;
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
     * @param compilationError エラー情報（エラー行番号など）
     * @return 修正が行われた場合true、そうでない場合false
     */
    public boolean fixErrorFile(File file, CompilationError compilationError) {
        try {
            // ファイルがテストコードかどうかを判定（testsディレクトリに含まれるか）
            boolean isTestFile = compilationError.getFilePath().contains("\\tests\\");

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

            if (isTestFile) {
                // テストコードの場合: エラーメソッドの本体を削除してAssert.failを挿入
                modified = fixTestFile(launcher, model, compilationError);
            } else {
                // メインコードの場合
                if (compilationError.isMissingReturnStatement()) {
                    // return文欠如エラー: メソッドの末尾にreturn文を追加
                    modified = fixMissingReturnStatement(launcher, model, compilationError);
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
                            element.delete();
                            modified = true;
                        }
                    }
                }
            }

            // import文の処理とファイル保存
            modified = removeErrorImportsAndSave(launcher, file, compilationError, modified) || modified;

            return modified;

        } catch (Exception e) {
            System.err.println("Error processing file: " + compilationError.getFileName() + " - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * return文欠如エラーの修正
     * エラー行を含むメソッドの末尾にデフォルトのreturn文を追加
     * 
     * @param launcher Spoon Launcher
     * @param model ASTモデル
     * @param compilationError エラー情報
     * @return 修正が行われた場合true
     */
    private boolean fixMissingReturnStatement(Launcher launcher, CtModel model, CompilationError compilationError) {
        boolean modified = false;
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
                                body.addStatement(returnStmt);
                                processedMethods.add(method);
                                modified = true;
                            }
                        }
                    }
                }
            }
        }
        
        return modified;
    }
    
    /**
     * テストファイルのエラー修正
     * エラー行を含むメソッドの本体を削除し、Assert.fail文を挿入
     * 
     * @param launcher Spoon Launcher
     * @param model ASTモデル
     * @param compilationError エラー情報
     * @return 修正が行われた場合true
     */
    private boolean fixTestFile(Launcher launcher, CtModel model, CompilationError compilationError) {
        boolean modified = false;
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
                            body.getStatements().clear();  // 既存のステートメントをクリア
                        } else {
                            body = launcher.getFactory().Core().createBlock();
                            method.setBody(body);
                        }
                        
                        // Assert.fail文を挿入（試行）
                        try {
                            CtStatement failStatement = launcher.getFactory().Code()
                                .createCodeSnippetStatement(
                                    "org.junit.Assert.fail(\"[LIB-REMOVED] このテストは削除対象ライブラリ依存のため失敗扱い\")"
                                );
                            body.addStatement(failStatement);
                        } catch (Exception e) {
                            // Assert.fail挿入失敗時はRuntimeExceptionをスロー
                            CtStatement throwStatement = launcher.getFactory().Code()
                                .createCodeSnippetStatement(
                                    "throw new RuntimeException(\"[LIB-REMOVED] このテストは削除対象ライブラリ依存のため失敗扱い\")"
                                );
                            body.addStatement(throwStatement);
                        }
                        
                        processedMethods.add(method);
                        modified = true;
                    }
                }
            }
        }
        
        return modified;
    }
    
    /**
     * エラー行のimport文を削除してファイルに保存
     * エラー行に該当するimport文を削除し、修正後のコードをファイルに保存
     * 
     * @param launcher Spoon Launcher
     * @param file 保存対象のファイル
     * @param compilationError エラー情報
     * @param alreadyModified すでに修正が行われているかどうか
     * @return 修正が行われた場合true
     * @throws IOException ファイル書き込みエラー
     */
    private boolean removeErrorImportsAndSave(Launcher launcher, File file, CompilationError compilationError,
                                              boolean alreadyModified) throws IOException {
        boolean modified = alreadyModified;
        
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
                    importsToRemove.add(ctImport);
                    modified = true;
                }
            }
            
            // 該当するimport文を削除
            targetUnit.getImports().removeAll(importsToRemove);

            // 修正が行われた場合、ファイルに保存
            if (modified) {
                String result = targetUnit.prettyprint();  // ASTからソースコードを生成
                try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
                    writer.print(result);
                }
            }
        }
        
        return modified;
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
