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
    
    /** オリジナルファイル保存済みのファイルを追跡するSet */
    private static Set<String> savedOriginalFiles = new HashSet<>();
    
    /**
     * エラーが発生したファイルを修正する
     * 
     * @param file 修正対象のファイル
     * @param compilationError エラー情報(エラー行番号など)
     * @param loopNumber 現在のループ番号
     * @return 修正結果を格納した配列 [削除された行数, 削除されたテストメソッド数]
     */
    public int[] fixErrorFile(File file, CompilationError compilationError, int loopNumber) {
        try {
            // ファイル修正開始のヘッダー表示
            System.out.println("\n--- " + compilationError.getFileName() + " の修正処理開始 ---");
            
            // オリジナルファイルを保存（初回のみ）
            saveOriginalFile(file, compilationError.getFilePath());
            
            // ファイルがテストコードかどうかを判定(test/javaディレクトリに含まれるか)
            boolean isTestFile = compilationError.getFilePath().contains("\\test\\java\\") || 
                                compilationError.getFilePath().contains("/test/java/");

            // Spoon Launcherの初期化と設定
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);  // classpathを使用しない
            launcher.getEnvironment().setAutoImports(false);  // 自動インポート有効化
            launcher.getEnvironment().setPrettyPrinterCreator(
                () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())  // コード整形設定
            );
            launcher.addInputResource(file.getAbsolutePath());  // 対象ファイルを追加
            launcher.buildModel();  // ASTモデルを構築
            CtModel model = launcher.getModel();

            boolean modified = false;
            int deletedElementCount = 0;
            int removedTestMethodCount = 0;  // 削除されたテストメソッド数

            if (isTestFile) {
                // テストコードの場合: エラーを含むテストメソッドを削除
                int[] result = fixTestFile(launcher, model, compilationError);
                int count = result[0];
                removedTestMethodCount = result[1];
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
                        // クラス宣言エラーのチェック
                        int classFixCount = fixClassDeclarationError(launcher, model, lineNum);
                        if (classFixCount > 0) {
                            modified = true;
                            deletedElementCount += classFixCount;
                            continue;  // クラス宣言を修正した場合は通常の削除処理をスキップ
                        }
                        
                        // 指定された行番号の要素を検索
                        List<CtElement> targetNodes = model.getElements(e -> {
                            SourcePosition pos = e.getPosition();
                            return pos != null && pos.isValidPosition() && pos.getLine() == lineNum;
                        });

                        // 見つかった要素を削除
                        for (CtElement element : targetNodes) {
                            // クラス宣言やインターフェース宣言は削除しない（別途処理）
                            if (element instanceof CtClass || element instanceof CtInterface) {
                                System.out.println("警告: クラス/インターフェース宣言は削除をスキップ: " + element.getClass().getSimpleName());
                                continue;
                            }
                            
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
            // 流れ: コード修正 → エラー行のimport削除 → 2回以上削除されたimport削除 → 保存
            int importCount = removeErrorImportsAndSave(launcher, file, compilationError, modified, deletedElementCount);
            if (importCount > 0) {
                modified = true;
                deletedElementCount += importCount;
            }

            // 修正が行われた場合、ループディレクトリにコピー
            if (modified) {
                saveToLoopDirectory(file, compilationError.getFilePath(), loopNumber, isTestFile);
            }

            // 修正完了メッセージ
            if (modified) {
                System.out.println("削除された行数: " + deletedElementCount);
                if (removedTestMethodCount > 0) {
                    System.out.println("削除されたテストメソッド数: " + removedTestMethodCount);
                }
                System.out.println("修正完了: [" + (isTestFile ? "TEST" : "MAIN") + "] " + compilationError.getFileName());
            } else {
                System.out.println("修正不可: " + compilationError.getFileName());
            }

            return new int[] { deletedElementCount, removedTestMethodCount };

        } catch (Exception e) {
            System.err.println("Error processing file: " + compilationError.getFileName() + " - " + e.getMessage());
            e.printStackTrace();
            return new int[] { 0, 0 };
        }
    }
    
    /**
     * オリジナルファイルをoriginalディレクトリに保存
     * 各ファイルにつき最初の1回のみ保存する
     * 
     * @param file 対象ファイル
     * @param originalFilePath 元のファイルパス
     * @throws IOException ファイル操作エラー
     */
    private void saveOriginalFile(File file, String originalFilePath) throws IOException {
        // すでに保存済みの場合はスキップ
        if (savedOriginalFiles.contains(originalFilePath)) {
            return;
        }
        
        // SHA ディレクトリを作成 (例: /output/3ff575ae...)
        String shaDir = ApplicationConfig.OUTPUT_DIR;
        if (ApplicationConfig.SHA != null && !ApplicationConfig.SHA.isEmpty()) {
            shaDir = ApplicationConfig.OUTPUT_DIR + "/" + ApplicationConfig.SHA;
        }
        
        // originalディレクトリを作成 (例: /output/3ff575ae.../original)
        String originalDirPath = shaDir + "/original";
        File originalDir = new File(originalDirPath);
        if (!originalDir.exists()) {
            boolean created = originalDir.mkdirs();
            if (created) {
                System.out.println("originalディレクトリを作成: " + originalDirPath);
            }
        }
        
        // ファイル名のみを取得
        String fileName = new File(originalFilePath).getName();
        
        // 出力先のファイルパスを構築（originalディレクトリ直下にファイル名のみ）
        File outputFile = new File(originalDir, fileName);
        
        // ファイルが既に存在する場合はスキップ
        if (outputFile.exists()) {
            savedOriginalFiles.add(originalFilePath);
            return;
        }
        
        // ファイルをコピー
        Files.copy(file.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        
        System.out.println("オリジナルファイル保存: " + outputFile.getAbsolutePath());
        
        // 保存済みとしてマーク
        savedOriginalFiles.add(originalFilePath);
    }
    
    /**
     * 修正したファイルをループディレクトリにコピー
     * 
     * @param file 修正されたファイル
     * @param originalFilePath 元のファイルパス
     * @param loopNumber ループ番号
     * @param isTestFile テストファイルかどうか
     * @throws IOException ファイル操作エラー
     */
    private void saveToLoopDirectory(File file, String originalFilePath, int loopNumber, boolean isTestFile) throws IOException {
        // SHA ディレクトリを作成 (例: /output/3ff575ae...)
        String shaDir = ApplicationConfig.OUTPUT_DIR;
        if (ApplicationConfig.SHA != null && !ApplicationConfig.SHA.isEmpty()) {
            shaDir = ApplicationConfig.OUTPUT_DIR + "/" + ApplicationConfig.SHA;
        }
        
        // ループディレクトリを作成 (例: /output/3ff575ae.../loop1, /output/3ff575ae.../loop2, ...)
        String loopDirPath = shaDir + "/loop" + loopNumber;
        File loopDir = new File(loopDirPath);
        if (!loopDir.exists()) {
            boolean created = loopDir.mkdirs();
            if (created) {
                System.out.println("ループディレクトリを作成: " + loopDirPath);
            }
        }
        
        // ファイル名のみを取得
        String fileName = new File(originalFilePath).getName();
        
        // 出力先のファイルパスを構築（ループディレクトリ直下にファイル名のみ）
        File outputFile = new File(loopDir, fileName);
        
        // ファイルをコピー
        Files.copy(file.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        
        System.out.println("  → 保存先: " + outputFile.getAbsolutePath());
    }
    
    /**
     * クラス宣言のエラーを修正
     * extends句やimplements句から存在しないクラス/インターフェースの参照を削除
     * 匿名クラスの場合は親のステートメント全体を削除
     * 
     * @param launcher Spoon Launcher
     * @param model ASTモデル
     * @param lineNum エラー行番号
     * @return 修正された要素数
     */
    private int fixClassDeclarationError(Launcher launcher, CtModel model, int lineNum) {
        int modifiedCount = 0;
        
        // 指定された行にあるクラス/インターフェース宣言を検索
        List<CtType<?>> types = model.getElements(e -> {
            if (!(e instanceof CtClass || e instanceof CtInterface)) {
                return false;
            }
            SourcePosition pos = e.getPosition();
            return pos != null && pos.isValidPosition() && pos.getLine() == lineNum;
        }).stream()
        .map(e -> (CtType<?>) e)
        .collect(Collectors.toList());
        
        for (CtType<?> type : types) {
            System.out.println("クラス宣言エラーを検出: " + type.getSimpleName() + " (行 " + lineNum + ")");
            
            // 匿名クラスかどうかをチェック
            if (type.isAnonymous()) {
                System.out.println("  - 匿名クラスを検出");
                
                // 匿名クラスを含むステートメント全体を削除
                CtElement parent = type.getParent();
                
                // NewClass式を探す
                while (parent != null && !(parent instanceof CtNewClass)) {
                    parent = parent.getParent();
                }
                
                if (parent instanceof CtNewClass) {
                    CtNewClass<?> newClassExpr = (CtNewClass<?>) parent;
                    
                    // さらに親のステートメントや代入を探す
                    CtElement stmtParent = newClassExpr.getParent();
                    while (stmtParent != null && 
                           !(stmtParent instanceof CtStatement) && 
                           !(stmtParent instanceof CtAssignment) &&
                           !(stmtParent instanceof CtLocalVariable)) {
                        stmtParent = stmtParent.getParent();
                    }
                    
                    if (stmtParent instanceof CtStatement) {
                        System.out.println("  - 匿名クラスを含むステートメント全体を削除");
                        ((CtStatement) stmtParent).delete();
                        modifiedCount++;
                        continue;
                    } else if (stmtParent instanceof CtLocalVariable) {
                        System.out.println("  - 匿名クラスを含むローカル変数宣言を削除");
                        ((CtLocalVariable<?>) stmtParent).delete();
                        modifiedCount++;
                        continue;
                    }
                }
                
                // ステートメントが見つからない場合は型自体を削除
                System.out.println("  - 匿名クラス自体を削除");
                type.delete();
                modifiedCount++;
                continue;
            }
            
            // 通常のクラス/インターフェース宣言の場合
            // CtClassの場合、superclassを削除
            if (type instanceof CtClass) {
                CtClass<?> ctClass = (CtClass<?>) type;
                CtTypeReference<?> superClass = ctClass.getSuperclass();
                
                if (superClass != null && !superClass.getQualifiedName().equals("java.lang.Object")) {
                    System.out.println("  - スーパークラスを削除: " + superClass.getQualifiedName());
                    ctClass.setSuperclass(null);
                    modifiedCount++;
                }
            }
            
            // implements句の処理（CtClassとCtInterfaceの両方）
            Set<CtTypeReference<?>> superInterfaces = type.getSuperInterfaces();
            if (superInterfaces != null && !superInterfaces.isEmpty()) {
                System.out.println("  - 実装インターフェースを削除: " + superInterfaces.size() + "個");
                for (CtTypeReference<?> iface : superInterfaces) {
                    System.out.println("    * " + iface.getQualifiedName());
                }
                superInterfaces.clear();
                modifiedCount++;
            }
        }
        
        return modifiedCount;
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
     * - テストアノテーション(@Test等)が付いているメソッド: メソッド全体を削除
     * - それ以外のメソッド/コード: メインコードと同様にエラー行の要素を削除
     * 
     * @param launcher Spoon Launcher
     * @param model ASTモデル
     * @param compilationError エラー情報
     * @return 修正結果を格納した配列 [削除された要素数, 削除されたテストメソッド数]
     */
    private int[] fixTestFile(Launcher launcher, CtModel model, CompilationError compilationError) {
        int modifiedCount = 0;
        int removedTestMethodCount = 0;
        Set<CtMethod<?>> processedMethods = new HashSet<>();  // 処理済みメソッドを追跡
        
        // モデル内の全メソッドを取得
        List<CtMethod<?>> methods = model.getElements(e -> e instanceof CtMethod<?>).stream()
                .map(e -> (CtMethod<?>) e)
                .collect(Collectors.toList());
        
        // 各エラー行に対して処理
        for (int lineNum : compilationError.getErrorLines()) {
            // まずクラス宣言エラーをチェック
            int classFixCount = fixClassDeclarationError(launcher, model, lineNum);
            if (classFixCount > 0) {
                modifiedCount += classFixCount;
                continue;  // クラス宣言エラーの場合はメソッド処理をスキップ
            }
            
            boolean handledByTestMethod = false;
            
            // テストアノテーション付きメソッドのチェック
            for (CtMethod<?> method : methods) {
                
                // すでに処理済みのメソッドはスキップ
                if (processedMethods.contains(method)) {
                    continue;
                }
                
                // テストアノテーションが付いているかチェック
                if (!hasTestAnnotation(method)) {
                    continue;  // テストアノテーションがないメソッドはスキップ（後で別途処理）
                }
                
                SourcePosition methodPos = method.getPosition();
                if (methodPos != null && methodPos.isValidPosition()) {
                    int startLine = methodPos.getLine();
                    int endLine = methodPos.getEndLine();
                    
                    // エラー行がメソッド内にあるかチェック
                    if (lineNum >= startLine && lineNum <= endLine) {
                        // テストメソッド全体を削除
                        String methodName = method.getSimpleName();
                        System.out.println("テストメソッドを削除: " + methodName + " (行 " + startLine + "-" + endLine + ")");
                        method.delete();
                        
                        processedMethods.add(method);
                        modifiedCount++;
                        removedTestMethodCount++;
                        handledByTestMethod = true;
                        break;  // このエラー行は処理済み
                    }
                }
            }
            
            // テストアノテーション付きメソッドで処理されなかった場合、通常の削除処理を実行
            if (!handledByTestMethod) {
                // 指定された行番号の要素を検索
                List<CtElement> targetNodes = model.getElements(e -> {
                    SourcePosition pos = e.getPosition();
                    return pos != null && pos.isValidPosition() && pos.getLine() == lineNum;
                });

                // 見つかった要素を削除
                for (CtElement element : targetNodes) {
                    // クラス宣言やインターフェース宣言は削除しない（別途処理済み）
                    if (element instanceof CtClass || element instanceof CtInterface) {
                        System.out.println("警告: クラス/インターフェース宣言は削除をスキップ: " + element.getClass().getSimpleName());
                        continue;
                    }
                    
                    // 削除する要素の詳細を出力
                    System.out.println("削除対象要素(テストファイル): " + element.getClass().getSimpleName() + " - " + element.getShortRepresentation());
                    element.delete();
                    modifiedCount++;
                }
            }
        }
        
        return new int[] { modifiedCount, removedTestMethodCount };
    }
    
    /**
     * メソッドにテストアノテーションが付いているかチェック
     * JUnit4の@Test, JUnit5の@Test, @ParameterizedTest, @RepeatedTest等に対応
     * 
     * @param method チェック対象のメソッド
     * @return テストアノテーションが付いている場合true
     */
    private boolean hasTestAnnotation(CtMethod<?> method) {
        List<CtAnnotation<? extends java.lang.annotation.Annotation>> annotations = method.getAnnotations();
        
        for (CtAnnotation<?> annotation : annotations) {
            String annotationName = annotation.getAnnotationType().getSimpleName();
            
            // JUnit4, JUnit5のテストアノテーションをチェック
            if (annotationName.equals("Test") ||              // @Test (JUnit4/5)
                annotationName.equals("ParameterizedTest") ||  // @ParameterizedTest (JUnit5)
                annotationName.equals("RepeatedTest") ||       // @RepeatedTest (JUnit5)
                annotationName.equals("TestFactory") ||        // @TestFactory (JUnit5)
                annotationName.equals("TestTemplate")) {       // @TestTemplate (JUnit5)
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * エラー行のimport文を削除し、2回以上削除されたimport文も削除してファイルに保存
     * 
     * 処理の流れ:
     * 1. エラー行に該当するimport文を削除
     * 2. 削除したimport文を記録
     * 3. ファイルに保存（Spoonのprettyprintで出力）
     * 4. 2回以上削除されたimport文をテキストベースで削除（Spoonの自動生成を回避）
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
            // === ステップ1: エラー行に該当するimport文を削除 ===
            List<CtImport> importsToRemove = new ArrayList<>();
            for (CtImport ctImport : targetUnit.getImports()) {
                SourcePosition pos = ctImport.getPosition();
                if (pos != null && pos.isValidPosition() && 
                    compilationError.getErrorLines().contains(pos.getLine())) {
                    String importStatement = ctImport.toString().trim();
                    System.out.println("削除対象import文: " + importStatement);
                    
                    // === ステップ2: インポート文を記録(統計用) ===
                    String importedType = extractImportedType(importStatement);
                    if (importedType != null) {
                        ApplicationConfig.recordDeletedImport(compilationError.getFilePath(), importedType);
                    }
                    
                    importsToRemove.add(ctImport);
                    importCount++;
                }
            }
            
            // エラー行のimport文を削除
            targetUnit.getImports().removeAll(importsToRemove);

            // === ステップ3: 修正が行われた場合、ファイルに保存 ===
            if (alreadyModified || importCount > 0) {
                String result = targetUnit.prettyprint();  // ASTからソースコードを生成
                try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
                    writer.print(result);
                }
                
                // === ステップ4: 2回以上削除されたimport文をテキストベースで削除 ===
                // Spoonのprettyprintで自動生成されたインポートを削除するため、
                // ファイル保存後にテキストベースで処理
                int repeatedImportCount = removeRepeatedImportsFromFile(file, compilationError.getFilePath());
                importCount += repeatedImportCount;
            }
        }
        
        return importCount;
    }
    
    /**
     * 2回以上削除されたインポート文をファイルから削除（テキストベース処理）
     * Spoonの自動インポート生成を回避するため、保存後にテキストで削除
     * 
     * @param file 対象ファイル
     * @param filePath ファイルパス
     * @return 削除されたインポート文の数
     * @throws IOException ファイル操作エラー
     */
    private int removeRepeatedImportsFromFile(File file, String filePath) throws IOException {
        Set<String> repeatedImports = ApplicationConfig.getRepeatedlyDeletedImports(filePath);
        
        if (repeatedImports.isEmpty()) {
            return 0;
        }
        
        System.out.println("2回以上削除されたインポートの事後削除（テキストベース）: " + repeatedImports.size() + "個");
        
        // ファイルの内容を読み込み
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        List<String> newLines = new ArrayList<>();
        int deletedCount = 0;
        
        // import文のパターン（様々な形式に対応）
        Pattern importPattern = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([^;]+);\\s*$");
        
        for (String line : lines) {
            Matcher matcher = importPattern.matcher(line);
            
            if (matcher.matches()) {
                String importedType = matcher.group(1).trim();
                
                // 繰り返し削除されたインポートかチェック
                if (repeatedImports.contains(importedType)) {
                    System.out.println("  - 事後削除: import " + importedType + ";");
                    deletedCount++;
                    continue;  // この行はスキップ(削除)
                }
            }
            
            newLines.add(line);
        }
        
        // ファイルが修正された場合、書き戻す
        if (deletedCount > 0) {
            Files.write(file.toPath(), newLines, StandardCharsets.UTF_8);
            System.out.println("繰り返し削除されたインポートをテキストベースで削除しました: " + deletedCount + "個");
        }
        
        return deletedCount;
    }
    
    /**
     * import文から実際のインポート対象を抽出
     * 例: "import java.util.List;" -> "java.util.List"
     * 例: "import static org.junit.Assert.*;" -> "org.junit.Assert.*"
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